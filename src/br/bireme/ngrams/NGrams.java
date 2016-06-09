/*=========================================================================

    Copyright © 2015 BIREME/PAHO/WHO

    This file is part of NGrams.

    NGrams is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at y   our option) any later version.

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
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NGramPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spell.NGramDistance;
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

    /*
      Suffix name of the not normalized indexed field associated to the field
      IndexedNGramField.
    */
    public static final String NOT_NORMALIZED_FLD = "~notnormalized";

    // <id>|<ngram index/search text>|<content>|...|<content>
    public static void index(final NGIndex index,
                             final NGSchema schema,
                             final String inFile,
                             final String inFileEncoding) throws IOException {
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
        final IndexWriter writer = index.getIndexWriter();
        int cur = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), charset)) {
            writer.deleteAll();

            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                indexDocument(index, writer, schema, line);
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
                                                            throws IOException {
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
        final String[] split = pipedDoc.replace(':', ' ').trim()
                                           .split(" *\\| *", Integer.MAX_VALUE);
        if (split.length <= parameters.maxIdxFieldPos) {
            throw new IOException("invalid number of fields: " + pipedDoc);
        }
        final String id = split[parameters.id.ipos];
        if (id.isEmpty()) {
            throw new IOException("id");
        }
        final String dbName = split[parameters.db.ipos];
        if (dbName.isEmpty()) {
            throw new IOException("dbName");
        }        
        final Map<String,br.bireme.ngrams.Field> flds = parameters.nameFields;
        final Document doc = createDocument(flds, split);
        
        if (doc != null) {
            final String dbId = Tools.normalize(dbName + id);
            writer.updateDocument(new Term("db_id", dbId), doc);
        }
                
        return (doc != null);
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

        Document doc = checkFieldsPresence(fields, flds, true) 
                                                         ? new Document(): null;
        String dbName = null;
        String id = null;
        
        if (doc != null) {
            final Set<String> names = new HashSet<>();
            for (br.bireme.ngrams.Field fld : fields.values()) {
                final String content = flds[fld.ipos];
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
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,dbName));
                } else if (fld instanceof IdField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    id = content.trim();
                    doc.add(new StoredField(fname, id));
                    doc.add(new StoredField(fname + NOT_NORMALIZED_FLD, id));
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
                                               final String[] param,
                                               final boolean indexing) {
        assert fields != null;
        assert param != null;

        boolean ok = true;
        final Set<String> checked = new HashSet<>();
        
        for (String name : fields.keySet()) {
            ok = checkPresence(name, fields, param, checked, indexing);
            if (!ok) {
                break;
            }
        }
        return ok;
    }

    private static boolean checkPresence(final String fieldName,
                                         final Map<String, br.bireme.ngrams.Field> fields,
                                         final String[] param,
                                         final Set<String> checked,
                                         final boolean indexing) {
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
                final int pos = indexing ? field.ipos : field.spos;
                final String requiredField = field.requiredField;
            
                checked.add(fieldName);
            
                ok = (param[pos].isEmpty()) ? (field.presence != Status.REQUIRED)
                        : (requiredField == null) ? true
                        : checkPresence(requiredField, fields, param, checked, 
                                                                      indexing);
            }
        }
        return ok;
    }

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
                if (++cur % 1000 == 0) {
                    System.out.println("<<< " + cur);
                }

                results.clear();
                final String[] split = line.replace(':', ' ').trim()
                                           .split(" *\\| *", Integer.MAX_VALUE);
                if (split.length != parameters.nameFields.size()) {
                    throw new IOException("invalid number of fields: " + line);
                }
                if (checkFieldsPresence(parameters.nameFields,split, false)) {
                    searchRaw(parameters, searcher, analyzer, ngDistance, line,
                                                                id_id, results);
                    if (!results.isEmpty()) {
                        writeOutput(parameters, results, writer);
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

        searchRaw(parameters, searcher, analyzer, ngDistance, text, id_id,
                                                                       results);
        searcher.getIndexReader().close();
        
        return original ? results2pipeFull(parameters, results)
                        : results2pipe(parameters, results);
    }
    
    public static Set<String> searchJson(final NGIndex index,
                                         final NGSchema schema,
                                         final String text) throws IOException,
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
        final TreeSet<Result> results = new TreeSet<>();

        searchRaw(parameters, searcher, analyzer, ngDistance, text, id_id,
                                                                       results);
        searcher.getIndexReader().close();

        return results2json(parameters, results.descendingSet());
    }

    // <id>|<ngram search text>|<content>|...|<content>
    private static void searchRaw(final Parameters parameters,
                                  final IndexSearcher searcher,
                                  final NGAnalyzer analyzer,
                                  final NGramDistance ngDistance,
                                  final String text,
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
                       param[parameters.indexed.spos]), MAX_NG_TEXT_SIZE).trim();
        final int MAX_RESULT = 100;
        if (!ntext.isEmpty()) {
            final Query query = parser.parse(QueryParser.escape(ntext));
            final TopDocs top = searcher.search(query, 10);
            final float lower = parameters.scores.first().minValue;
            ScoreDoc[] scores = top.scoreDocs;
            ScoreDoc after = null;
            int tot = 0;
            outer: while (scores.length > 0) {
                for (ScoreDoc sdoc : scores) {
                    if (++tot > MAX_RESULT) {
                        break outer;
                    }
                    final Document doc = searcher.doc(sdoc.doc);
                    final float similarity =
                                  ngDistance.getDistance(ntext, doc.get(fname));
                    //if (similarity < lower) {
                        //break outer;
                    //}
                    if (similarity >= lower) {
                        final Result out = createResult(id_id, parameters, ntext,
                                 param, doc, ngDistance, similarity,sdoc.score);
                        if (out != null) {
                            //System.out.println("##### " + out.compare);
                            results.add(out);
                        }
                    }
                    after = sdoc;
                }
                if (after != null) {
                    scores = searcher.searchAfter(after, query, 10).scoreDocs;
                }
            }
        }
    }

    private static void searchRaw2(final Parameters parameters,
                                   final IndexSearcher searcher,
                                   final NGAnalyzer analyzer,
                                   final NGramDistance ngDistance,
                                   final String text,
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
                       param[parameters.indexed.spos]), MAX_NG_TEXT_SIZE).trim();
        if (!ntext.isEmpty()) {
            final IndexReader ireader = searcher.getIndexReader();
            final Query query0 = parser.parse(QueryParser.escape(ntext));
            final Query q0 = query0.rewrite(ireader);
            final String[] terms = new String[] { QueryParser.escape(ntext) };
            final Query query = new NGramPhraseQuery(NGAnalyzer.DEF_NG_SIZE,
                                             new PhraseQuery(20, fname, terms));
            final Query query1 = new PhraseQuery(fname, terms);
            final Query query2 = new TermQuery(new Term(fname,
                                                    QueryParser.escape(ntext)));

            final Query q2 = query.rewrite(ireader);
            final TopDocs top = searcher.search(query, 10);
            final float lower = parameters.scores.first().minValue;
            ScoreDoc[] scores = top.scoreDocs;
            ScoreDoc after = null;
            outer: while (scores.length > 0) {
                for (ScoreDoc sdoc : scores) {
                    final Document doc = searcher.doc(sdoc.doc);
                    final float similarity =
                                  ngDistance.getDistance(ntext, doc.get(fname));
                    if (similarity < lower) {
                        break outer;
                    }
                    final Result out = createResult(id_id, parameters, ntext,
                                param, doc, ngDistance, similarity, sdoc.score);
                    if (out != null) {
                        //System.out.println("##### " + out + "\n");
                        results.add(out);
                    }
                    after = sdoc;
                }
                if (after != null) {
                    scores = searcher.searchAfter(after, query, 10).scoreDocs;
                }
            }
        }
    }

    // <search doc id>|<similarity>|<index doc id>|<ngram search text>|<ngram index text>|<matches>(<possible matches>)
    private static Result createResult(final Set<String> id_id,
                                       final Parameters parameters,
                                       final String stext,
                                       final String[] param,
                                       final Document doc,
                                       final NGramDistance ngDistance,
                                       final float similarity,
                                       final float score) {
        assert id_id != null;
        assert parameters != null;
        assert stext != null;
        assert param != null;
        assert doc != null;
        assert ngDistance != null;
        assert similarity >= 0;
        assert score >= 0;

        final Result ret;
        final Collection<br.bireme.ngrams.Field> fields = parameters.nameFields.values();
        int matchedFields = 0;

        for (br.bireme.ngrams.Field fld: fields) {
            final int val =
                      checkField(ngDistance, fld, param[fld.spos], doc);
            if (val == -1) {
                matchedFields = -1;
                break; // skip this document because it does not follow requests
            }
            matchedFields += val;
        }
        
        final String id1 = param[parameters.id.spos];
        final String id2 = (String)doc.get("id");
        final String id1id2 = (id1.compareTo(id2) <= 0) ?
                                      (id1 + "_" + id2) : (id2 + "_" + id1);
        if (matchedFields <= 0) {
            ret = null; // no field is matched
        } else {
            if (checkScore(parameters, param, similarity, matchedFields)) {
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

    private static Set<String> results2pipe(final Parameters parameters,
                                            final Set<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();

        for (Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;
            final String itext = (String)doc.get(parameters.indexed.name).
                                 replace('|', '!');
            final String stext = Tools.limitSize(Tools.normalize(
                           param[parameters.indexed.spos]), MAX_NG_TEXT_SIZE).
                           trim().replace('|', '!');
            final String id1 = param[parameters.id.spos];
            final String id2 = (String)doc.get("id");
            final String src1 = param[parameters.db.spos];
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
    
    private static boolean checkScore(final Parameters parameters,
                                      final String[] param,
                                      final float similarity,
                                      final int matchedFields) {
        assert parameters != null;
        assert param != null;
        assert similarity >= 0;
        assert matchedFields > 0;

        boolean maxScore = isMaxScore(parameters.exacts, param) ||
                           isMaxScore(parameters.ngrams, param) ||
                           isMaxScore(parameters.regexps, param);

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

    /**
     * Checks the fields/definititons to tell if the score should be MAX_SCORE 
     * or not.
     * @param fields field's definitions
     * @param param field's content
     * @return true if MAX_SCORE should be used or false if not.
     */
    private static boolean isMaxScore(
                             final Set<? extends br.bireme.ngrams.Field> fields,
                             final String[] param) {
        assert fields != null;
        assert param != null;

        boolean maxScore = false;

        for (br.bireme.ngrams.Field field : fields) {
            if ((field.presence == Status.MAX_SCORE) &&
                (param[field.spos].isEmpty())) {
                maxScore = true;
                break;
            }
            if (field.content != null) {
                boolean found = false;
                for (String str: field.content) {
                    if (str.equals(param[field.spos])) {
                        found = true;
                        break;
                    }
                }
                maxScore = !found;
                if (maxScore) {
                    break;
                }
            }
        }
        return maxScore;
    }

    /**
     *
     * @param ngDistance
     * @param field
     * @param fld
     * @param pos
     * @param doc
     * @return -1 fields dont match - match is required
     *          0 fields dont match - empty field or match is optional
     *          1 fields match
     */
    private static int checkField(final NGramDistance ngDistance,
                                  final br.bireme.ngrams.Field field,
                                  final String fld,
                                  final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert fld != null;
        assert doc != null;

        final int ret;

        if (field instanceof IndexedNGramField) {
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
            if (field.presence == Status.REQUIRED) {
                ret = -1;
            } else {
                ret = text.isEmpty() ? 0 
                               : field.contentMatch == Status.OPTIONAL ? 0 : -1;
            }
        } else if (fld.equals(text)) {
            ret = 1;
        } else {
            final float similarity = ngDistance.getDistance(fld, text);
            ret = (similarity >= ((IndexedNGramField)field).minScore) ? 1
                               : field.contentMatch == Status.OPTIONAL ? 0 : -1;
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
        final String text = (String)doc.get(field.name);

        if (fld.isEmpty()) {
            if (field.presence == Status.REQUIRED) {
                ret = -1;
            } else {
                ret = text.isEmpty() ? 0 
                               : field.contentMatch == Status.OPTIONAL ? 0 : -1;
            }
        } else if (fld.equals(text)) {
            ret = 1;
        } else {
            final float similarity = ngDistance.getDistance(fld, text);
            ret = (similarity >= ((NGramField)field).minScore) ? 1
                               : field.contentMatch == Status.OPTIONAL ? 0 : -1;
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
     * @return -1 : fields dont match and match is required or field is empty and it is required
     *          0 : fields dont match and match is optional or field is empty but it is optional
     *          1 : fields match
     */
    private static int compareFields(final br.bireme.ngrams.Field field,
                                     final String fld,
                                     final String idxText) {
        assert field != null;
        assert fld != null;
        assert idxText != null;

        return fld.isEmpty() ? ((field.presence == Status.REQUIRED) ? -1 : 0)
                             : (fld.equals(idxText) ? 1
                                : idxText.isEmpty() ? 0
                                   : (field.contentMatch == Status.REQUIRED ? -1
                                                                          : 0));
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

    private static void usage() {
        System.err.println("Usage: NGrams (index|search1|search2)" +
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
          "\n\n   search2 <indexPath> <confFile> <confFileEncoding> <text> [--original]- find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <text> - text used to find documents" +
          "\n       [--json] - if present the output type will be json otherwise peped text" +
          "\n\nFormat of input file <inFile> line:  <id>|<ngram index/search text>|<content>|...|<content>" +
          "\nFormat of output file line: <search doc id>|<similarity>|<index doc id>|<ngram search text>|<ngram index text>\n");

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

        if (args.length < 4) {
            usage();
        }
                                
        final NGSchema schema = new NGSchema("dummy", args[2], args[3]);
        final NGIndex index = new NGIndex("dummy", args[1]);
        
        if (args[0].equals("index")) {
            if (args.length != 4+2) {
                usage();
            }
            index(index, schema, args[3+1], args[3+2]);
            System.out.println("Indexing has finished.");
        } else if (args[0].equals("search1")) {
            if (args.length < 4+3) {
                usage();
            }
            final String encoding = (args.length == 4+4) ? args[4+3] : "UTF-8";
            search(index, schema, args[3+1], args[3+2], args[3+3], encoding);
            System.out.println("Searching has finished.");
        } else if (args[0].equals("search2")) {
            if (args.length < 4+1) {
                usage();
            }
            final boolean original = 
                          (args.length > 4+1) && args[4+1].equals("--original");
            final Set<String> set = search(index, schema, args[4], original);
            if (set.isEmpty()) {
                System.out.println("No result was found.");
            } else {
                int pos = 0;
                for(String res : set) {
                    System.out.println((++pos) + ") " + res);
                }
            }
        } else {
            usage();
        }

        final long endTime = new GregorianCalendar().getTimeInMillis();
        final long difTime = (endTime - startTime) / 1000;

        System.out.println("\nElapsed time: " + difTime + "s");
    }
}