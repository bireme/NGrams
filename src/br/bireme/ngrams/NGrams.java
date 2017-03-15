/*=========================================================================

    Copyright © 2015 BIREME/PAHO/WHO

    This file is part of NGrams.

    NGrams is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    NGrams is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with NGrams. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package br.bireme.ngrams;

import br.bireme.ngrams.Field.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spell.NGramDistance;
import org.apache.lucene.util.Bits;
import org.xml.sax.SAXException;

/**
 * Lê um arquivo de entrada e indexada cada entrada usando ngrams ou procura
 * cada item em um índice previamente preparado. Veja formatos de entrada e
 * saída na função usage().
 * @author Heitor Barbieri
 * date: 20150624
 */
public class NGrams {
    static class Result implements Comparable<Result> {
        final String[] param;
        final Document doc;
        final float similarity;
        final float score;
        private final String compare;

        Result(final String[] param,
               final Document doc,
               final float similarity,
               final float score) {
            assert param != null;
            assert doc != doc;
            assert similarity >= 0;
            assert score >= 0;

            this.param = param;
            this.doc = doc;
            this.similarity = similarity;
            this.score = score;
            this.compare = similarity + "_" + doc.get(DatabaseField.FNAME) + "_" 
                                                       + doc.get(IdField.FNAME);
        }
        
        @Override
        public int compareTo(final Result other) {
            return compare.compareTo(other.compare);
        }
    }

    /*
       Maximum ngram text size. If longer then it, it will be truncated.
    */
    public static final int MAX_NG_TEXT_SIZE = 90;
    //public static final int MAX_NG_TEXT_SIZE = 100;

    /*
      Suffix name of the not normalized indexed field associated to the field
      IndexedNGramField.
    */
    public static final String NOT_NORMALIZED_FLD = "~notnormalized";

    // <id>|<ngram index/search text>|<content>|...|<content>
    public static void index(final NGIndex index,
                             final NGSchema schema,
                             final String inFile,
                             final String inFileEncoding) throws IOException, 
                                                                ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (inFile == null) {
            throw new NullPointerException("inFile");
        }
        if (inFileEncoding == null) {
            throw new NullPointerException("inFileEncoding");
        }

        final Charset charset = Charset.forName(inFileEncoding);
        final IndexWriter writer = index.getIndexWriter(false);
        int cur = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), charset)) {
            writer.deleteAll();

            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                final String lineT = line.trim();
                if (! lineT.isEmpty()) {
                    indexDocument(index, writer, schema, line);
                }
                if (++cur % 10000 == 0) {
                    System.out.println(">>> " + cur);
                }
            }
            writer.forceMerge(1); // optimize index
            writer.close();
        }
    }
           
    public static boolean indexDocument(final NGIndex index,
                                        final IndexWriter writer,
                                        final NGSchema schema,
                                        final String pipedDoc) 
                                                            throws IOException, 
                                                                ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (writer == null) {
            throw new NullPointerException("writer");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }        
        if (pipedDoc == null) {
            throw new NullPointerException("pipedDoc");
        }
        final Parameters parameters = schema.getParameters();
        if (Tools.countOccurrences(pipedDoc, '|') < parameters.maxIdxFieldPos) {
            throw new IOException("invalid number of fields: [" + pipedDoc + "]");
        }
        
        final String[] split = pipedDoc.replace(':', ' ').trim()
                                           .split(" *\\| *", Integer.MAX_VALUE);
        final String id = split[parameters.id.pos];
        if (id.isEmpty()) {
            throw new IOException("id");
        }
        final String dbName = split[parameters.db.pos];
        if (dbName.isEmpty()) {
            throw new IOException("dbName");
        }        
        final Map<String,br.bireme.ngrams.Field> flds = parameters.nameFields;
        final Document doc = createDocument(flds, split);
        
        if (doc != null) {
            final String id_ = Tools.limitSize(Tools.normalize(id), 
                                                       MAX_NG_TEXT_SIZE).trim();
            final String db_ = Tools.limitSize(Tools.normalize(dbName), 
                                                       MAX_NG_TEXT_SIZE).trim();
            final QueryParser parser = new QueryParser("", index.getAnalyzer());
            final Query query = parser.parse(IdField.FNAME + ":\"" + id_ + 
                          "\" AND " + DatabaseField.FNAME + ":\"" + db_ + "\"");
                                    
            writer.deleteDocuments(query);
 //System.out.print("vou escrever");           
            writer.addDocument(doc);
 //System.out.println("  - OK");           
        }
                
        return (doc != null);
    }
    
    public static void indexDocuments(final NGSchema schema,
                                      final NGIndex index,
                                      final String multiLinePipedDoc) 
                                                            throws IOException, ParseException {
        if (schema == null) {
            throw new NullPointerException("schema");
        }        
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (multiLinePipedDoc == null) {
            throw new NullPointerException("multiLinePipedDoc");
        }
        final Parameters parameters = schema.getParameters();
        final Map<String,br.bireme.ngrams.Field> flds = parameters.nameFields;
        
        try (IndexWriter writer = index.getIndexWriter(true)) {
            final String[] mlPipedDoc = multiLinePipedDoc.trim().split(" *\n *");
            
            for (String line: mlPipedDoc) {
                if (!line.isEmpty()) {
                    final String[] split = line.replace(':', ' ').trim()
                            .split(" *\\| *", Integer.MAX_VALUE);
                    if (split.length < parameters.maxIdxFieldPos) {
                        throw new IOException("invalid number of fields: [" + 
                                                                    line + "]");
                    }
                    final String id = split[parameters.id.pos];
                    if (id.isEmpty()) {
                        throw new IOException("id");
                    }
                    final String dbName = split[parameters.db.pos];
                    if (dbName.isEmpty()) {
                        throw new IOException("dbName");
                    }
                    final Document doc = createDocument(flds, split);
                    if (doc != null) {
                        final String id_ = Tools.limitSize(Tools.normalize(id), 
                                                       MAX_NG_TEXT_SIZE).trim();
                        final String db_ = Tools.limitSize(Tools.normalize(dbName), 
                                                       MAX_NG_TEXT_SIZE).trim();
                        final QueryParser parser = new QueryParser("", 
                                                           index.getAnalyzer());
                        final Query query = parser.parse(IdField.FNAME + ":\"" + 
                            id_ + "\" AND " + DatabaseField.FNAME + ":\"" + db_ 
                                                                        + "\"");
                                    
                        writer.deleteDocuments(query);
                        writer.addDocument(doc);
                    }
                }
            }
        }
    }
        
    public static String json2pipe(final NGSchema schema,
                                   final String indexName,
                                   final String id,
                                   final String sjson) throws IOException {
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (indexName == null) {
            throw new NullPointerException("indexName");
        }
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (sjson == null) {
            throw new NullPointerException("sjson");
        }

        final String occSeparator = "//@//";
        final Parameters parameters = schema.getParameters();
        final String indexFldName = parameters.db.name;
        final String idFldName = parameters.id.name;
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String,Object> userData = mapper.readValue(sjson, Map.class);
        final StringBuilder ret = new StringBuilder();
        final Map<Integer,br.bireme.ngrams.Field> sfields = parameters.sfields;

        userData.put(indexFldName, indexName);
        userData.put(idFldName, id);
        
        for (int idx = 0; idx <= parameters.maxIdxFieldPos; idx++) {
            final br.bireme.ngrams.Field fld =  sfields.get(idx);

            ret.append((idx == 0) ? "" : "|");
            if (fld != null) {
                final Object obj = userData.get(fld.name);
                if (obj != null) {
                    if (obj instanceof String) {
                        ret.append((String) obj);
                    } else if (obj instanceof Number) {
                        ret.append((String) obj);
                    } else if (obj instanceof List) {
                        boolean first = true;
                        for (Object obj2: (List<Object>)obj) {
                            if (first) {
                                first = false;
                            } else {
                                ret.append(occSeparator);
                            }
                            ret.append((String)obj2);
                        }
                    } else {
                        throw new IOException("Illegal json format:" + sjson);
                    }
                }
            }
        }
        return ret.toString();
    }

    private static Document createDocument(
                               final Map<String, br.bireme.ngrams.Field> fields,
                               final String[] flds) throws IOException {
        assert fields != null;
        assert flds != null;

        Document doc = checkFieldsPresence(fields, flds) ? new Document(): null;
        String dbName = null;
        String id = null;
        
        if (doc != null) {
            final Set<String> names = new HashSet<>();
            for (br.bireme.ngrams.Field fld : fields.values()) {
                final String content = flds[fld.pos];
                final String fname = fld.name;
                if (fld instanceof IndexedNGramField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    final String ncontent = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new TextField(fname, ncontent, Field.Store.YES));
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                               content.trim()));
                } else if (fld instanceof DatabaseField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    dbName = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new StoredField(fname, dbName));
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                               content.trim()));
                } else if (fld instanceof IdField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    id = content.trim();
                    id = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    //doc.add(new StringField(fname, id, Field.Store.YES));
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD, content.trim()));
                } else {
                    final String ncontent = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new StoredField(fname, ncontent));
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                               content.trim()));
                }
                names.add(fname);
            }
            // Add field to avoid duplicated documents in the index
            if (dbName == null) {
                throw new IOException("dbName");
            }
            if (id == null) {
                throw new IOException("id");
            }
            doc.add(new StringField("db_id", Tools.normalize(dbName + "_" + id), 
                                                                    Store.YES));
        }
        return doc;
    }

    /**
     * If a record has all required fields then true else false
     * @param fields fields specification
     * @param param fields content
     * @param indexing true ifi indexing, false if searching
     * @return 
     */
    private static boolean checkFieldsPresence(final Map<String, 
                                                 br.bireme.ngrams.Field> fields,
                                               final String[] param) {
        assert fields != null;
        assert param != null;

        boolean ok = true;
        final Set<String> checked = new HashSet<>();
        
        for (String name : fields.keySet()) {
            ok = checkPresence(name, fields, param, checked);
            if (!ok) {
                break;
            }
        }
        return ok;
    }

    private static boolean checkPresence(final String fieldName,
                                         final Map<String, br.bireme.ngrams.Field> fields,
                                         final String[] param,
                                         final Set<String> checked) {
        assert fieldName != null;
        assert fields != null;
        assert param != null;
        assert checked != null;

        final boolean ok;

        if (checked.contains(fieldName)) {
            ok = true;
        } else {
            final br.bireme.ngrams.Field field = fields.get(fieldName);
            if (field == null) {
                ok = false;
            } else {
                final int pos = field.pos;
                final String requiredField = field.requiredField;
            
                checked.add(fieldName);
            
                ok = (param[pos].isEmpty()) ? (field.presence != Status.REQUIRED)
                        : (requiredField == null) ? true
                        : checkPresence(requiredField, fields, param, checked);
            }
        }
        return ok;
    }

    /**
     * 
     * @param index
     * @param schema
     * @param inFile
     * @param inFileEncoding
     * @param outFile
     * @param outFileEncoding
     * @throws IOException
     * @throws ParseException 
     */
    public static void search(final NGIndex index,
                              final NGSchema schema,
                              final String inFile,
                              final String inFileEncoding,
                              final String outFile,
                              final String outFileEncoding) throws IOException,
                                                                ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (inFile == null) {
            throw new NullPointerException("inFile");
        }
        if (inFileEncoding == null) {
            throw new NullPointerException("inFileEncoding");
        }
        if (outFile == null) {
            throw new NullPointerException("outFile");
        }
        if (outFileEncoding == null) {
            throw new NullPointerException("outFileEncoding");
        }
        final Charset inCharset = Charset.forName(inFileEncoding);
        final Charset outCharset = Charset.forName(outFileEncoding);
        final IndexSearcher searcher = index.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)index.getAnalyzer();
        final Parameters parameters = schema.getParameters();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        int cur = 0;
        try (final BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), inCharset);
             final BufferedWriter writer = Files.newBufferedWriter(
                                      new File(outFile).toPath(), outCharset)) {
            writer.append("rank|similarity|search_doc_id|index_doc_id|" +
                          "ngram_search_text|ngram_index_text|search_source|" +
                                                              "index_source\n");

            final Set<Result> results = new HashSet<>();
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (++cur % 250 == 0) {
                    System.out.println("<<< " + cur);
                }

                results.clear();
                final String tline = line.replace(':', ' ').trim();
                if (!tline.isEmpty()) {
                    final String[] split = tline.split(" *\\| *", Integer.MAX_VALUE);
                    if (split.length != parameters.nameFields.size()) {
                        throw new IOException("invalid number of fields: " + line);
                    }
                    if (checkFieldsPresence(parameters.nameFields,split)) {
                        searchRaw(parameters, searcher, analyzer, ngDistance, 
                                                    line, true, id_id, results);
                        if (!results.isEmpty()) {
                            writeOutput(parameters, results, writer);
                        }
                    }
                }
            }
            searcher.getIndexReader().close();
        }
    }

    public static Set<String> search(final NGIndex index,
                                     final NGSchema schema,
                                     final String text,
                                     final boolean original) throws IOException,
                                                                ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (text == null) {
            throw new NullPointerException("text");
        }
        final IndexSearcher searcher = index.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)index.getAnalyzer();
        final Parameters parameters = schema.getParameters();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        final Set<Result> results = new HashSet<>();

        final String ttext = text.replace(':', ' ').trim();
        final String[] split = ttext.split(" *\\| *", Integer.MAX_VALUE);
        if (split.length != parameters.nameFields.size()) {
            throw new IOException("invalid number of fields: " + text);
        }

        if (checkFieldsPresence(parameters.nameFields,split)) {
            searchRaw(parameters, searcher, analyzer, ngDistance, ttext, true,
                                                                id_id, results);
        }
        searcher.getIndexReader().close();
        
        return original ? results2pipeFull(parameters, results)
                        : results2pipe(parameters, results);
    }
    
    public static Set<String> srcWithoutSimil(final NGIndex index,
                                              final NGSchema schema,
                                              final String text,
                                              final boolean original) 
                                                         throws IOException,
                                                                ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (text == null) {
            throw new NullPointerException("text");
        }
        final IndexSearcher searcher = index.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)index.getAnalyzer();
        final Parameters parameters = schema.getParameters();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        final Set<Result> results = new HashSet<>();

        final String ttext = text.replace(':', ' ').trim();
        final String[] split = ttext.split(" *\\| *", Integer.MAX_VALUE);
        if (split.length != parameters.nameFields.size()) {
            throw new IOException("invalid number of fields: " + text);
        }

        if (checkFieldsPresence(parameters.nameFields,split)) {
            searchRaw(parameters, searcher, analyzer, ngDistance, ttext, false,
                                                                id_id, results);
        }
        searcher.getIndexReader().close();
        
        return original ? results2pipeFull(parameters, results)
                        : results2pipe(parameters, results);
    }
    
    public static Set<String> searchJson(final NGIndex index,
                                         final NGSchema schema,
                                         final String text) 
                                            throws IOException, ParseException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (text == null) {
            throw new NullPointerException("text");
        }
        final IndexSearcher searcher = index.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)index.getAnalyzer();
        final Parameters parameters = schema.getParameters();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        final TreeSet<Result> results = new TreeSet<>();

        searchRaw(parameters, searcher, analyzer, ngDistance, text, true, 
                                                                id_id, results);
        searcher.getIndexReader().close();

        return results2json(parameters, results.descendingSet());
    }

    // <id>|<ngram search text>|<content>|...|<content>
    private static void searchRaw(final Parameters parameters,
                                  final IndexSearcher searcher,
                                  final NGAnalyzer analyzer,
                                  final NGramDistance ngDistance,
                                  final String text,
                                  final boolean useSimilarity,
                                  final Set<String> id_id,
                                  final Set<Result> results)
                                            throws IOException, ParseException {
        assert parameters != null;
        assert searcher != null;
        assert analyzer != null;
        assert ngDistance != null;
        assert text != null;
        assert id_id != null;
        assert results != null;

        final String[] param = text.trim().split(" *\\| *", Integer.MAX_VALUE);
        if (param.length != parameters.nameFields.size()) {
            throw new IOException(text);
        }
        final String fname = parameters.indexed.name;
        final QueryParser parser = new QueryParser(fname, analyzer);
        final String ntext = Tools.limitSize(Tools.normalize(
                       param[parameters.indexed.pos]), MAX_NG_TEXT_SIZE).trim();
        final int MAX_RESULTS = 20;
        
        if (!ntext.isEmpty()) {
            final Query query = parser.parse(QueryParser.escape(ntext));
            final TopDocs top = searcher.search(query, MAX_RESULTS);
            final float lower = parameters.scores.first().minValue;
            ScoreDoc[] scores = top.scoreDocs;
            int remaining = MAX_RESULTS;
            
            for (ScoreDoc sdoc : scores) {  
                if (remaining-- <= 0) {
                    break;  // Only for performance
                }                
                final Document doc = searcher.doc(sdoc.doc);                
                if (useSimilarity) {
                    final float similarity = ngDistance.getDistance(ntext, 
                                                                doc.get(fname));
                    if (similarity < lower) {
                        if (remaining > 3) {
                            remaining = 3;
                            //System.out.println("Atualizando tot=" + tot + " score=" + sdoc.score + " similarity=" + similarity+ " text=" + doc.get(fname));
                        }
                    } else {
                        final Result out = createResult(id_id, parameters,
                             param, doc, ngDistance, similarity, sdoc.score);
                        if (out != null) {
                            results.add(out);
                        }
                    }
                } else {
                    if (sdoc.score < 1.0) {
                        System.out.println("Saindo score=" + sdoc.score);
                        break;    // Only for performance
                    }
                    final Result out = createResult(id_id, parameters,
                             param, doc, ngDistance, 0, sdoc.score);
                    if (out != null) {
                        results.add(out);
                    }
                }
            }
        }
    }

    // <search doc id>|<similarity>|<index doc id>|<ngram search text>|<ngram index text>|<matches>(<possible matches>)
    private static Result createResult(final Set<String> id_id,
                                       final Parameters parameters,
                                       final String[] param,
                                       final Document doc,
                                       final NGramDistance ngDistance,
                                       final float similarity,
                                       final float score) {
        assert id_id != null;
        assert parameters != null;
        assert param != null;
        assert doc != null;
        assert ngDistance != null;
        assert similarity >= 0;
        assert score >= 0;

        final Result ret;
        final Collection<br.bireme.ngrams.Field> fields = 
                                                 parameters.nameFields.values();
        int matchedFields = 0;
        boolean maxScore = false;

        for (br.bireme.ngrams.Field fld: fields) {
            final int val = checkField(ngDistance, fld, param, 
                                                    parameters.nameFields, doc);
            if (val == -1) {
                matchedFields = -1;
                break; // skip this document because it does not follow requests
            } else if (val == -2) {
                maxScore = true;
            } else {
                matchedFields += val;
            }
        }
        
        final String id1 = param[parameters.id.pos];
        final String id2 = (String)doc.get("id");
        final String idb1 = id1 + "_" + Tools.normalize(param[parameters.db.pos]);
        final String idb2 = id2 + "_" + (String)doc.get("database");
        final String id1id2 = (idb1.compareTo(idb2) <= 0) ? (idb1 + "_" + idb2) 
                                                          : (idb2 + "_" + idb1);
        
        if (matchedFields <= 0) {
            ret = null; // document is reject (one of its fields does not follow schema)
        } else {
            if (checkScore(parameters, param, similarity, matchedFields, 
                                                                    maxScore)) {
                //ret = new NGrams.Result(param, doc, similarity, score);
                if (id_id.contains(id1id2)) {
                    ret = null;                
                } else {
                    id_id.add(id1id2);
                    ret = new NGrams.Result(param, doc, similarity, score);
                }
            } else {
                ret = null;
            }
        }
        return ret;
    }
    
    private static boolean checkScore(final Parameters parameters,
                                      final String[] param,
                                      final float similarity,
                                      final int matchedFields,
                                      final boolean maxScore) {
        assert parameters != null;
        assert param != null;
        assert similarity >= 0;
        assert matchedFields > 0;

        Score score = null;
        for (Score score1 : parameters.scores) {
            final float minValue = maxScore ? 1 : score1.minValue;
            if (similarity >= minValue) {
                score = score1;
            } else {
                break;
            }
        }

        return (score != null) && (matchedFields >= score.minFields);
    }
    
    private static Set<String> results2pipe(final Parameters parameters,
                                            final Set<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();

        for (Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;
            final String itext = (String)doc.get(parameters.indexed.name + 
                                            "~notnormalized").replace('|', '!');
            final String stext = param[parameters.indexed.pos].trim()
                                                             .replace('|', '!');
            final String id1 = param[parameters.id.pos];
            final String id2 = (String)doc.get("id");
            final String src1 = param[parameters.db.pos];
            final String src2 = (String)doc.get("database");
            final String str = result.score + "|" + result.similarity + "|" + 
                    id1 + "|" + id2 + "|" + stext + "|" + itext + "|" + src1 + 
                    "|" + src2;
            //System.out.println("! " + result.compare);
            ret.add(str);
        }
        return ret.descendingSet();
    }

    private static Set<String> results2pipeFull(final Parameters parameters,
                                                final Set<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();
        final StringBuilder builder = new StringBuilder();
        final Collection<br.bireme.ngrams.Field> flds = parameters.sfields
                                                                      .values();

        for (Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;            

            builder.setLength(0);
            builder.append(result.score).append("|").append(result.similarity);
            for (int idx = 0; idx < flds.size(); idx++) {
                String fld = param[idx];
                fld = (fld == null) ? "" : fld.trim().replace('|', '!');
                builder.append("|").append(fld);
                builder.append("|").append(Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE));
            }
            for (br.bireme.ngrams.Field field: flds) {
                final String fldN = doc.get(field.name);
                final String fld = doc.get(field.name + NGrams.NOT_NORMALIZED_FLD);
                
                builder.append("|").append((fld == null) ? "" 
                                                       : fld.replace('|', '!'));
                builder.append("|").append((fldN == null) ? "" 
                                                      : fldN.replace('|', '!'));
            }
            ret.add(builder.toString());
        }
        return ret.descendingSet();
    }

    private static Set<String> results2json(final Parameters parameters,
                                            final Set<Result> results) {
        assert parameters != null;
        assert results != null;

        String name;
        String doc;
        final StringBuilder builder = new StringBuilder();
        final TreeSet<String> ret = new TreeSet<>();

        for (Result result : results) {
            builder.setLength(0);
            builder.append("{");
            name = parameters.db.name;
            doc = result.doc.get(name).replace('\"', '\'');
            builder.append(" \"").append(name).append("\":\"")
                   .append(doc).append("\",");
            name = parameters.id.name;
            doc = result.doc.get(name).replace('\"', '\'');
            builder.append(" \"").append(name).append("\":\"")
                   .append(doc).append("\",");
            name = parameters.indexed.name;
            doc = result.doc.get(name).replace('\"', '\'');
            builder.append(" \"").append(name).append("\":\"")
                   .append(doc).append("\"");
            for (ExactField exact : parameters.exacts) {
                name = exact.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (ExactField exact : parameters.exacts) {
                name = exact.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (NGramField ngrams : parameters.ngrams) {
                name = ngrams.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (RegExpField regexps : parameters.regexps) {
                name = regexps.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (NoCompareField nocompare : parameters.nocompare) {
                name = nocompare.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            builder.append(", \"score\":\"").append(result.score).append("\"");
            builder.append(" }");
            ret.add(builder.toString());
        }

        return ret.descendingSet();
    }
    
    /**
     *
     * @param ngDistance
     * @param field
     * @param fld
     * @param pos
     * @param fields
     * @param doc
     * @return -2 : fields dont match and contentMatch is MAX_SCORE
     *         -1 : fields dont match and contentMatch is required
     *          0 : fields dont match and contentMatch is optional
     *          1 : fields match
     */
    private static int checkField(final NGramDistance ngDistance,
                                  final br.bireme.ngrams.Field field,
                                  final String[] param,
                                  final Map<String,br.bireme.ngrams.Field> fields,
                                  final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert param != null;
        assert fields != null;
        assert doc != null;

        final int ret;
        
        final String fld = param[field.pos];
        final String requiredField = field.requiredField;
        int rfldPos = -1;  // required field pos;
        
        if (requiredField != null) {
            final br.bireme.ngrams.Field afld = fields.get(requiredField);
            if (afld != null) {
                rfldPos = afld.pos;
            }             
        } 
        if ((rfldPos != -1) && (param[rfldPos].isEmpty())) {
            ret = -1;
        } else if (field instanceof IndexedNGramField) {
            final String nfld = Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE).trim();
            ret = compareIndexedNGramFields(ngDistance, field, nfld, doc);
        } else if (field instanceof NGramField) {
            final String nfld = Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE).trim();
            ret = compareNGramFields(ngDistance, field, nfld, doc);
        } else if (field instanceof RegExpField) {
            final String nfld = Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE).trim();
            ret = compareRegExpFields(field, nfld, doc);
        } else if (field instanceof ExactField) {
            final String nfld = Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE).trim();
            final String idxText = (String)doc.get(field.name);
            ret = compareFields(field, nfld, idxText);
        } else if (field instanceof DatabaseField) {
            final String nfld = Tools.limitSize(Tools.normalize(fld),
                                                       MAX_NG_TEXT_SIZE).trim();
            final String idxText = (String)doc.get(field.name);
            //ret = (nfld.compareTo(idxText) == 0) ? 0 : -1;
            ret = 0; // database name should not be considered when checking duplicated
        } else {
            ret = 0;
        }

        return ret;
    }

    private static int compareIndexedNGramFields(final NGramDistance ngDistance,
                                             final br.bireme.ngrams.Field field,
                                                 final String fld,
                                                 final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert fld != null;
        assert doc != null;

        final int ret;
        final String text = (String)doc.get(field.name);

        if (fld.isEmpty()) {
            ret = -1;
        } else if (fld.equals(text)) {
            ret = 1;
        } else {
            final float similarity = ngDistance.getDistance(fld, text);
            ret = (similarity >= ((IndexedNGramField)field).minScore) ? 1 : -1;
        }
        return ret;
    }
    
    private static int compareNGramFields(final NGramDistance ngDistance,
                                          final br.bireme.ngrams.Field field,
                                          final String fld,
                                          final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert fld != null;
        assert doc != null;

        final int ret;
        final String idxText = (String)doc.get(field.name);

        if (fld.isEmpty()) {
            if (field.presence == Status.REQUIRED) {
                ret = -1;
            } else if (field.presence == Status.MAX_SCORE) {
                ret = -2;
            } else if (fld.equals(idxText)) {
                ret = 1;
            } else if (field.contentMatch == Status.REQUIRED) {
                ret = -1;
            } else if (field.contentMatch == Status.MAX_SCORE) {
                ret = -2;
            } else {   // Status.OPTIONAL
                ret = 0;
            }
         } else if (fld.equals(idxText)) {
            ret = 1;
         } else {
             final float similarity = ngDistance.getDistance(fld, idxText);
             if (similarity >= ((NGramField)field).minScore) {
                 ret = 1;
             } else if (field.contentMatch == Status.REQUIRED) {
                ret = -1;
            } else if (field.contentMatch == Status.MAX_SCORE) {
                ret = -2;
            } else {   // Status.OPTIONAL
                ret = 0;
            }
         }
        
        return ret;
    }

    private static int compareRegExpFields(final br.bireme.ngrams.Field field,
                                           final String fld,
                                           final Document doc) {
        assert field != null;
        assert fld != null;
        assert doc != null;

        final String idxText = (String)doc.get(field.name);
        final RegExpField regExp = (RegExpField)field;
        final Matcher mat = regExp.matcher;
        final int ret;

        mat.reset(idxText);
        if (mat.find()) {
            final String content1 = mat.group(regExp.groupNum);
            if (content1 == null) {
                ret = compareFields(field, fld, idxText);
            } else {
                mat.reset(fld);
                if (mat.find()) {
                    final String content2 = mat.group(regExp.groupNum);
                    if (content2 == null) {
                        ret = compareFields(field, fld, idxText);
                    } else {
                        ret = compareFields(field, content1, content2);
                    }
                } else {
                    ret = compareFields(field, fld, idxText);
                }
            }
        } else {
            ret = compareFields(field, fld, idxText);
        }
        return ret;
    }

    /**
     *
     * @param field - configuration of the document
     * @param fld - text used to search
     * @param idxText - txt from index
     * @return -2 : fields dont match and contentMatch is MAX_SCORE
     *         -1 : fields dont match and contentMatch is required
     *          0 : fields dont match and contentMatch is optional
     *          1 : fields match
     */
    private static int compareFields(final br.bireme.ngrams.Field field,
                                     final String fld,
                                     final String idxText) {
        assert field != null;
        assert fld != null;
        assert idxText != null;

        final int ret;
        
        if (fld.isEmpty()) {
            if (field.presence == Status.REQUIRED) {
                ret = -1;
            } else if (field.presence == Status.MAX_SCORE) {
                ret = -2;
            } else if (fld.equals(idxText)) {
                ret = 1;
            } else if (field.contentMatch == Status.REQUIRED) {
                ret = -1;
            } else if (field.contentMatch == Status.MAX_SCORE) {
                ret = -2;
            } else {   // Status.OPTIONAL
                ret = 0;
            }
         } else if (fld.equals(idxText)) {
            ret = 1;
         } else if (field.contentMatch == Status.REQUIRED) {
            ret = -1;
         } else if (field.contentMatch == Status.MAX_SCORE) {
            ret = -2;
         } else {   // Status.OPTIONAL
            ret = 0;
         }
        return ret;
    }

    private static void writeOutput(final Parameters parameters,
                                    final Set<Result> results,
                                    final BufferedWriter writer)
                                                            throws IOException {
        assert parameters != null;
        assert results != null;
        assert writer != null;

        boolean first = true;

        writer.newLine();
        for (String pipe : results2pipe(parameters, results)) {
            if (first) {
                first = false;
            } else {
                writer.newLine();
            }
            writer.append(pipe);
        }
    }

    public static void export(NGIndex index,
                              final NGSchema schema,
                              final String outFile,
                              final String outFileEncoding) throws IOException {
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (outFile == null) {
            throw new NullPointerException("outFile");
        }
        if (outFileEncoding == null) {
            throw new NullPointerException("outFileEncoding");
        }
        final Parameters parameters = schema.getParameters();
        final TreeMap<Integer,String> fields = new TreeMap<>();
        final IndexReader reader = index.getIndexSearcher().getIndexReader();
        final int maxdoc = reader.maxDoc();
        final Bits liveDocs = MultiFields.getLiveDocs(reader);
        final BufferedWriter writer = Files.newBufferedWriter(
                       Paths.get(outFile), 
                       Charset.forName(outFileEncoding), 
                       StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        
        boolean first = true;
        
        for (Map.Entry<Integer,br.bireme.ngrams.Field> entry : 
                                                parameters.sfields.entrySet()) {
            fields.put(entry.getKey(), entry.getValue().name + NOT_NORMALIZED_FLD);
        }
        
        for (int docID = 0; docID < maxdoc; docID++) {
            if ((liveDocs != null) && (!liveDocs.get(docID))) continue;
            final Document doc = reader.document(docID);

            if (first) {
                first = false;
            } else {
                writer.newLine();
            }
            writer.append(doc2pipe(doc,fields));
        }
        writer.close();
        reader.close();
    }
    
    private static String doc2pipe(final Document doc,
                                   final TreeMap<Integer,String> fields) {
        final StringBuilder sb = new StringBuilder();
        int cur = 0;
        
        for (Map.Entry<Integer,String> entry : fields.entrySet()) {
            if (cur > 0) {
                sb.append("|");
            }
            final int pos = entry.getKey();
            if (cur == pos) {
                final String fld = doc.get(entry.getValue());
                if (fld != null) {
                    sb.append(fld);
                }
            }
            cur++;
        }
        return sb.toString();
    }
    
    private static void usage() {
        System.err.println("Usage: NGrams (index|search1|search2|search3|export)" +
          "\n\n   index <indexPath> <confFile> <confFileEncoding> <inFile> <inFileEncoding> - index a list of documentes." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <inFile> - input file. See format bellow" +
          "\n       <inFileEncoding> - input file encoding" +
          "\n\n   search1 <indexPath> <confFile> <confFileEncoding> <inFile> <inFileEncoding> <outFile> [<outFileEncoding>] - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <inFile> - input file. See format bellow" +
          "\n       <inFileEncoding> - input file encoding" +
          "\n       <outFile> - output file. See format bellow" +
          "\n       [<outFileEncoding>] - output file encoding. Default = UTF-8" +
          "\n\n   search2 <indexPath> <confFile> <confFileEncoding> <text> [--original] - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <text> - text used to find documents" +
          "\n       [--original] - if present the output type will be the original pipe text otherwise will be a shorterned one" +
          "\n\n   search3 <indexPath> <confFile> <confFileEncoding> <text> [--original] - find similar documents - IT DOES NOT USE SIMILARITY FUNCTION." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <text> - text used to find documents" +
          "\n       [--original] - if present the output type will be the original pipe text otherwise will be a shorterned one" +
          "\n\n   export <indexPath> <confFile> <confFileEncoding> <outFile> <outFileEncoding> - exports all active index documents into a piped file." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <outFile> - output file following configuration file specification" +
          "\n       <outFileEncoding> - output file encoding" +
          "\n\nFormat of input file <inFile> line:  <id>|<ngram index/search text>|<content>|...|<content>" +
          "\nFormat of output file line: <rank>|<similarity>|<search doc id>|<index doc id>|<ngram search text>|" + 
                     " <ngram index text>|<search_source>|<index_source>\n");

        System.exit(1);
    }
    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     * @throws org.apache.lucene.queryparser.classic.ParseException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public static void main(String[] args) throws IOException, ParseException,
                                                  ParserConfigurationException,
                                                  SAXException {
        final long startTime = new GregorianCalendar().getTimeInMillis();

        if (args.length < 5) {
            usage();
        }
                                
        final NGSchema schema = new NGSchema("dummy", args[2], args[3]);        
        
        if (args[0].equals("index")) {
            if (args.length != 6) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], false);
            index(index, schema, args[4], args[5]);
            System.out.println("Indexing has finished.");
        } else if (args[0].equals("search1")) {
            if (args.length < 7) {
                usage();
            } 
            final NGIndex index = new NGIndex("dummy", args[1], true);
            if (args.length == 7) {
                search(index, schema, args[4], args[5], args[6], "utf-8");
            } else {    
                search(index, schema, args[4], args[5], args[6], args[7]);
            }
            System.out.println("Searching has finished.");
        } else if (args[0].equals("search2")) {
            if (args.length < 4+1) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            Set<String> set = null;
            
            if (args.length == 5) {
                set = search(index, schema, args[4], false);
            } else if (args[5].equals("--original")) {
                set = search(index, schema, args[4], true);
            } else usage();
            if ((set == null) || (set.isEmpty())) {
                System.out.println("No result was found.");
            } else {
                int pos = 0;
                for(String res : set) {
                    System.out.println((++pos) + ") " + res);
                }
            }
        } else if (args[0].equals("search3")) {
            if (args.length < 5) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            Set<String> set = null;
            
            if (args.length == 5) {
                set = srcWithoutSimil(index, schema, args[4], false);
            } else if (args[5].equals("--original")) {
                set = srcWithoutSimil(index, schema, args[4], true);
            } else usage();
            if ((set == null) || (set.isEmpty())) {
                System.out.println("No result was found.");
            } else {
                int pos = 0;
                for(String res : set) {
                    System.out.println((++pos) + ") " + res);
                }
            }
        } else if (args[0].equals("export")) {    
            if (args.length != 6) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            export(index, schema, args[4], args[5]);
        } else {
            usage();
        }

        final long endTime = new GregorianCalendar().getTimeInMillis();
        final long difTime = (endTime - startTime) / 1000;

        System.out.println("\nElapsed time: " + difTime + "s");
    }
}