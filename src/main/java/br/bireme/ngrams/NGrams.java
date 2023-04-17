/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import br.bireme.ngrams.Field.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.vickumar1981.stringdistance.util.StringDistance;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
//import org.apache.lucene.index.MultiFields;  // older Lucene version
import org.apache.lucene.index.MultiBits;    // newer Lucene version
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
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
    public static class Result implements Comparable<Result> {
        public final String[] param;
        public final Document doc;
        public final float similarity;
        public final float score;
        private final String compare;
        private final ArrayList<CheckFieldResult> resltList;

        Result(final String[] param,
               final Document doc,
               final float similarity,
               final float score,
               final ArrayList<CheckFieldResult> resltList) {
            assert param != null;
            assert doc != null;
            assert similarity >= 0;
            assert score >= 0;
            assert resltList != null;

            this.param = param;
            this.doc = doc;
            this.similarity = similarity;
            this.score = score;
            this.resltList = resltList;
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

    /*
       String delimiter of repetitive occurrences
    */
    public static final String OCC_SEPARATOR = "//@//";

    /*
      Available charsets
    */
    private static final Charset UTF8 = Charset.availableCharsets().get("UTF-8");

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

        writer.deleteAll();
        writer.commit();

        try (BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), charset)) {
            writer.deleteAll();

            while (true) {
                final String line;
                try {
                    line = reader.readLine();
                } catch (final MalformedInputException mie) {
                    System.err.println("Line with another encoding. Line number:"
                                                                     + (++cur));
                    continue;
                }
                if (line == null) {
                    break;
                }
                final boolean ret = indexDocument(index, writer, schema, line, true, false);
                if (ret && (++cur % 100000 == 0)) {
                    System.out.println(">>> " + cur);
                }
            }
            writer.commit();
            writer.forceMerge(1); // optimize index
        }
    }

    public static void indexDocuments(final NGSchema schema,
                                      final NGIndex index,
                                      final String multiLinePipedDoc)
                                            throws IOException {
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (index == null) {
            throw new NullPointerException("index");
        }
        if (multiLinePipedDoc == null) {
            throw new NullPointerException("multiLinePipedDoc");
        }

        final IndexWriter writer = index.getIndexWriter();
        final String[] pipedDoc = multiLinePipedDoc.trim().split(" *\n *");

        for (final String line: pipedDoc) {
            indexDocument(index, writer, schema, line, true, false);
        }
        writer.commit();
    }

    public static boolean indexDocument(final NGIndex index,
                                        final IndexWriter writer,
                                        final NGSchema schema,
                                        final String pipedDoc,
                                        final boolean allowDocUpdate,
                                        final boolean commit)
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
        boolean ret = false;
        final String pipedDocT = pipedDoc.trim();
        if (!isUtf8Encoding(pipedDocT)) {
            throw new IOException("Invalid encoded string");
        }
        if (! pipedDocT.isEmpty()) {
            final Parameters parameters = schema.getParameters();
            if (Tools.countOccurrences(pipedDoc, '|') < parameters.maxIdxFieldPos) {
                throw new IOException("invalid number of fields: [" + pipedDoc + "]");
            }
            final String pipedDoc2 = StringEscapeUtils.unescapeHtml4(pipedDocT);
            final String[] split = pipedDoc2.replace(':', ' ').trim()
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
                if (allowDocUpdate) {
                    writer.updateDocument(new Term("id", id), doc);
                } else {
                    writer.addDocument(doc);
                }
                if (commit) {
                  writer.commit();
                }
                ret = true;
            }
        }
        return ret;
    }

    public static void deleteDocument(final String id,
                                      final NGIndex index) throws IOException {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (index == null) {
            throw new NullPointerException("index");
        }
        final String idN = Tools.limitSize(
                             Tools.normalize(id, OCC_SEPARATOR),
                                                       MAX_NG_TEXT_SIZE).trim();

        try (IndexWriter writer = index.getIndexWriter()) {
            final Query query;
            if (id.trim().endsWith("*")) { // delete all documents with same prefix
                query = new PrefixQuery(
                            new Term("id", idN.substring(0, idN.length() - 1)));
            } else {
                query = new TermQuery(new Term("id", idN));
            }

            writer.deleteDocuments(query);
            writer.commit();
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
                        ret.append(obj);
                    } else if (obj instanceof List) {
                        boolean first = true;
                        for (final Object obj2: (List<Object>)obj) {
                            if (first) {
                                first = false;
                            } else {
                                ret.append(OCC_SEPARATOR);
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

        Document doc = new Document();
        String dbName = null;
        String id = null;

        final Set<String> names = new HashSet<>();
        for (final br.bireme.ngrams.Field fld : fields.values()) {
            final String content = flds[fld.pos];
            final String fname = fld.name;
            if (fld instanceof IndexedNGramField) {
                if (names.contains(fname)) {
                    doc = null;
                    break;
                }
                final String ncontent = Tools.limitSize(
                    Tools.normalize(content, OCC_SEPARATOR), MAX_NG_TEXT_SIZE)
                                                                     .trim();
                doc.add(new TextField(fname, ncontent, Field.Store.YES));
                doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                           content.trim()));
            } else if (fld instanceof DatabaseField) {
                if (names.contains(fname)) {
                    doc = null;
                    break;
                }
                dbName = Tools.limitSize(
                         Tools.normalize(content, OCC_SEPARATOR),
                                                   MAX_NG_TEXT_SIZE).trim();
                doc.add(new StringField(fname, dbName, Field.Store.YES));
                doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                           content.trim()));
            } else if (fld instanceof IdField) {
                if (names.contains(fname)) {
                    doc = null;
                    break;
                }
                id = Tools.limitSize(
                         Tools.normalize(content, OCC_SEPARATOR),
                                                   MAX_NG_TEXT_SIZE).trim();
                doc.add(new StringField(fname, id, Field.Store.YES));
                doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                           content.trim()));
            } else if (fld instanceof AuthorsField) {
                if (names.contains(fname)) {
                    doc = null;
                    break;
                }
                doc.add(new StoredField(fname + NOT_NORMALIZED_FLD,
                                                           content.trim()));
                final String[] authors = Tools.normalize2(content, OCC_SEPARATOR);
                if (authors != null) {
                    final int len = authors.length;
                    for (int idx = 0; idx < len; idx++) {
                        doc.add(new StoredField(fname, authors[idx]));
                    }
                }
            } else {
                final String ncontent = Tools.limitSize(
                         Tools.normalize(content, OCC_SEPARATOR),
                                                   MAX_NG_TEXT_SIZE).trim();
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
        if (doc != null) {
            doc.add(new StringField("db_id",
                          Tools.normalize(dbName + "_" + id, OCC_SEPARATOR),
                                                                Store.YES));
        }

        return doc;
    }

    /**
     * Checks is the string encoding is utf-8.
     * @param text input text to check the encoding
     * @return if the input text encoding is utf-8
     */
    private static boolean isUtf8Encoding(final String text) {
        assert text != null;

        //final Charset utf8 = Charset.availableCharsets().get("UTF-8");
        final byte[] b1 = text.getBytes(UTF8) ;
        final byte[] b2 = new String(b1, UTF8).getBytes(UTF8);

        return java.util.Arrays.equals(b1, b2);
    }

    /**
     *
     * @param index
     * @param schema
     * @param inFile
     * @param inFileEncoding
     * @param outFile
     * @param outFileEncoding
     * @param report
     * @param selfCheck
     * @throws IOException
     * @throws ParseException
     */
    public static void search(final NGIndex index,
                              final NGSchema schema,
                              final String inFile,
                              final String inFileEncoding,
                              final String outFile,
                              final String outFileEncoding,
                              final boolean report,
                              final boolean selfCheck) throws IOException,
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

            if (report) {
                final StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (br.bireme.ngrams.Field fld: parameters.getSearchFields().values()) {
                    if (first) first = false;
                    else sb.append("|");
                    sb.append(fld.name).append("_1").append("|");
                    sb.append(fld.name).append("_2").append("|");
                    sb.append(fld.name).append("_similarity").append("|");
                    sb.append(fld.name).append("_veridict");
                }
                writer.append(sb.toString());
            } else {
                writer.append("rank|similarity|search_doc_id|index_doc_id|" +
                    "ngram_search_text|ngram_index_text|search_source|" +
                                                              "index_source");
            }

            final List<Result> results = new ArrayList<>();
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
                    searchRaw(parameters, searcher, analyzer, ngDistance,
                                        tline, true, selfCheck, id_id, results);
                    if (!results.isEmpty()) {
                        writeOutput(parameters, results, writer, report);
                    }
                }
            }
            searcher.getIndexReader().close();
        }
    }

    public static Set<String> search(final NGIndex index,
                                     final NGSchema schema,
                                     final String text,
                                     final boolean report,
                                     final boolean selfCheck) throws IOException,
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
        final List<Result> results = new ArrayList<>();

        final String ttext = text.replace(':', ' ').trim();
        final String[] split = ttext.split(" *\\| *", Integer.MAX_VALUE);
        if (split.length != parameters.nameFields.size()) {
            throw new IOException("invalid number of fields: " + text);
        }

        searchRaw(parameters, searcher, analyzer, ngDistance, ttext, true,
                                                    selfCheck, id_id, results);

        searcher.getIndexReader().close();

        return report ? result2PipeReport(parameters, results)
                        : results2pipeFull(parameters, results);
    }

    public static Set<String> srcWithoutSimil(final NGIndex index,
                                              final NGSchema schema,
                                              final String text,
                                              final boolean report,
                                              final boolean selfCheck)
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
        final List<Result> results = new ArrayList<>();

        final String ttext = text.replace(':', ' ').trim();
        final String[] split = ttext.split(" *\\| *", Integer.MAX_VALUE);
        if (split.length != parameters.nameFields.size()) {
            throw new IOException("invalid number of fields: " + text);
        }

        searchRaw(parameters, searcher, analyzer, ngDistance, ttext, false,
                                                     selfCheck, id_id, results);

        searcher.getIndexReader().close();

        return report ? result2PipeReport(parameters, results)
                        : results2pipeFull(parameters, results);
    }

    public static Set<String> searchJson(final NGIndex index,
                                         final NGSchema schema,
                                         final String text,
                                         final boolean selfCheck)
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
        final List<Result> results = new ArrayList<>();
        final String ttext = text.replace(':', ' ').trim();

        searchRaw(parameters, searcher, analyzer, ngDistance, ttext, true,
                                                     selfCheck, id_id, results);
        searcher.getIndexReader().close();

        return results2json(parameters, results);
    }

    // <id>|<ngram search text>|<content>|...|<content>
    public static void searchRaw(final Parameters parameters,
                                 final IndexSearcher searcher,
                                 final NGAnalyzer analyzer,
                                 final NGramDistance ngDistance,
                                 final String text,
                                 final boolean useSimilarity,
                                 final boolean selfCheck,
                                 final Set<String> id_id,
                                 final List<Result> results)
                                            throws IOException, ParseException {
        assert parameters != null;
        assert searcher != null;
        assert analyzer != null;
        assert ngDistance != null;
        assert id_id != null;
        assert results != null;

        if (text == null) {
            throw new NullPointerException("text");
        }

        final String text2 = StringEscapeUtils.unescapeHtml4(text).trim();
        
        final long count = text2.chars().filter(ch -> ch == '|').count();
        String[] param;
        if (parameters.maxIdxFieldPos == count) {
            param = text2.trim().split(" *\\| *", Integer.MAX_VALUE);  // Avoid problems with string starting with "
        } else {
            final List<CSVRecord> records = CSVFormat.EXCEL.withDelimiter('|')
                    .parse(new StringReader(text2.trim())).getRecords();
            if (records.isEmpty()) {
                throw new IOException(text);
            }
            final CSVRecord record = records.get(0);
            final int size = record.size();
            
            param = new String[size];

            for (int i = 0; i < size; i++) {
                param[i] = record.get(i);
            }
        }
        
        if (param.length < parameters.nameFields.size()) {
            throw new IOException(text);
        }

        final String ntext = Tools.limitSize(Tools.normalize(
                                 param[parameters.indexed.pos], OCC_SEPARATOR),
                                                       MAX_NG_TEXT_SIZE).trim();
        final int MAX_RESULTS = 1000; //20;

        if (!ntext.isEmpty()) {
            final String fname = parameters.indexed.name;
            final QueryParser parser = new QueryParser(fname, analyzer); // current version
            //final QueryParser parser = new QueryParser(Version.LUCENE_40, fname, analyzer);   // Lucene 4.0
            String escaped = QueryParser.escape(ntext);
            final Query query = parser.parse(escaped);
            final TopDocs top = searcher.search(query, MAX_RESULTS);
            final float lower = parameters.scores.last().minValue;
            final ScoreDoc[] scores = top.scoreDocs;
            int remaining = MAX_RESULTS;

            for (final ScoreDoc sdoc : scores) {
                if (remaining-- <= 0) {
                    break;  // Only for performance
                }
                final Document doc = searcher.doc(sdoc.doc);
                if (useSimilarity) {
                    final String dname = doc.get(fname);
                    if (dname == null) {
                        throw new IOException("dname");
                    }
                    final float similarity = ngDistance.getDistance(ntext, dname);
                    if (similarity < lower) {
                        if (remaining > 3) {
                            remaining = 3;
                            //System.out.println("Atualizando tot=" + tot + " score=" + sdoc.score + " similarity=" + similarity+ " text=" + doc.get(fname));
                        }
                    } else {
                        final Result out = createResult(id_id, parameters, param,
                            doc, ngDistance, similarity, sdoc.score, selfCheck);
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
                             param, doc, ngDistance, 0, sdoc.score, selfCheck);
                    if (out != null) {
                        results.add(out);
                    }
                }
            }
        }
    }

    // <search doc id>|<similarity>|<index doc id>|<ngram search text>|<ngram index text>|<matches>(<possible matches>)
    private static synchronized Result createResult(final Set<String> id_id,
                                                    final Parameters parameters,
                                                    final String[] param,
                                                    final Document doc,
                                                    final NGramDistance ngDistance,
                                                    final float similarity,
                                                    final float score,
                                                    final boolean selfCheck) {
        assert id_id != null;
        assert parameters != null;
        assert param != null;
        assert doc != null;
        assert ngDistance != null;
        assert similarity >= 0;
        assert score >= 0;

        final String id1 = param[parameters.id.pos];
        final String id2 = doc.get("id");
        final String idb1 = id1 + "_" + Tools.normalize(param[parameters.db.pos],
                                                                 OCC_SEPARATOR);
        final String idb2 = id2 + "_" + doc.get("database");
        final String id1id2 = (idb1.compareTo(idb2) <= 0) ? (idb1 + "_" + idb2)
                                                          : (idb2 + "_" + idb1);
        final Result ret;

        if (selfCheck && id_id.contains(id1id2)) {
            ret = null; // document is reject (no field passed the check)
        } else {
            int matchedFields = 0;
            boolean maxScore = false;
            boolean denyDup = false;
            final Collection<br.bireme.ngrams.Field> fields =
                                                 parameters.sfields.values();
            final ArrayList<CheckFieldResult> resltList = new ArrayList<>();

            for (final br.bireme.ngrams.Field fld: fields) {
                final CheckFieldResult result =
                             checkField(similarity, ngDistance, fld, param, doc);
                resltList.add(result);

                switch(result.condition) {
                    case NOT_SIMILAR:
                    case IGNORE:
                        break;
                    case MAX_SCORE:
                        maxScore = true;
                        break;
                    case DENY_DUP:
                        denyDup = true;
                        break;
                    default:
                        matchedFields += 1;
                }
            }
            if (denyDup) {
                ret = null;
            } else if (checkScore(parameters, similarity, matchedFields, maxScore)) {
                id_id.add(id1id2);
                ret = new NGrams.Result(param, doc, similarity, score, resltList);
            } else {
                ret = null;
            }
        }

        return ret;
    }

    private static boolean checkScore(final Parameters parameters,
                                      final float similarity,
                                      final int matchedFields,
                                      final boolean maxScore) {
        assert parameters != null;
        assert similarity >= 0;
        assert matchedFields > 0;

        Score score = null;
        for (final Score score1 : parameters.scores) {
            final float minValue = maxScore ? 1 : score1.minValue;
            if (similarity >= minValue) {
                score = score1;
                break;
            }
        }

        return (score != null) && (matchedFields >= score.minFields);
    }

    public static Set<String> results2pipe(final Parameters parameters,
                                           final List<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();

        for (final Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;
            final String itext = doc.get(parameters.indexed.name +
                                     NOT_NORMALIZED_FLD).replace('|', '!');
            final String stext = param[parameters.indexed.pos].trim()
                                                             .replace('|', '!');
            final String id1 = param[parameters.id.pos];
            final String id2 = doc.get("id" + NOT_NORMALIZED_FLD);
            final String src1 = param[parameters.db.pos];
            final String src2 = doc.get("database" + NOT_NORMALIZED_FLD);
            final String str = result.score + "|" + result.similarity + "|" +
                    id1 + "|" + id2 + "|" + stext + "|" + itext + "|" + src1 +
                    "|" + src2;
            //System.out.println("! " + result.compare);
            ret.add(str);
        }
        return ret.descendingSet();
    }

    public static Set<String> results2pipeFull(final Parameters parameters,
                                               final List<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();
        final StringBuilder builder = new StringBuilder();
        final Collection<br.bireme.ngrams.Field> flds = parameters.sfields
                                                                      .values();

        for (final Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;

            builder.setLength(0);
            builder.append(result.score).append("|").append(result.similarity);
            for (int idx = 0; idx < flds.size(); idx++) {
                String fld = param[idx];
                fld = (fld == null) ? "" : fld.trim().replace('|', '!');
                builder.append("|").append(fld);
                builder.append("|").append(
                    Tools.limitSize(Tools.normalize(fld, OCC_SEPARATOR),
                                                             MAX_NG_TEXT_SIZE));
            }
            for (final br.bireme.ngrams.Field field: flds) {
                final String fldN = Tools.mkString(doc.getValues(field.name), OCC_SEPARATOR);
                final String fld = Tools.mkString(doc.getValues(field.name + NOT_NORMALIZED_FLD),
                                                  OCC_SEPARATOR);
                builder.append("|").append((fld == null) ? ""
                                                       : fld.replace('|', '!'));
                builder.append("|").append((fldN == null) ? ""
                                                      : fldN.replace('|', '!'));
            }
            ret.add(builder.toString());
        }
        return ret.descendingSet();
    }

    public static Set<String> result2PipeReport(final Parameters parameters,
                                                final List<Result> results) {
        assert parameters != null;
        assert results != null;

        final TreeSet<String> ret = new TreeSet<>();
        final StringBuilder builder = new StringBuilder();

        for (final Result result : results) {
            boolean first = true;
            builder.setLength(0);

            for (CheckFieldResult fcr: result.resltList) {
                if (first) {
                    first = false;
                } else {
                    builder.append("|");
                }

                builder.append(fcr.elem1).append("|").append(fcr.elem2)
                       .append("|").append(fcr.similarity)
                       .append("|").append(fcr.condition);
            }
            ret.add(builder.toString());
        }

        return ret.descendingSet();
    }

    public static Set<String> results2json(final Parameters parameters,
                                           final List<Result> results) {
        assert parameters != null;
        assert results != null;

        String name;
        String doc;
        final StringBuilder builder = new StringBuilder();
        final TreeSet<String> ret = new TreeSet<>();

        for (final Result result : results) {
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
            if (parameters.authors != null) {
                boolean first = true;
                name = parameters.authors.name;
                builder.append(", \"").append(name).append("\":[");
                for (String value: result.doc.getValues(name)) {
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    builder.append("\"").append(value).append("\"");
                }
                builder.append("]");
            }
            for (final ExactField exact : parameters.exacts) {
                name = exact.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (final NGramField ngrams : parameters.ngrams) {
                name = ngrams.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (final RegExpField regexps : parameters.regexps) {
                name = regexps.name;
                doc = result.doc.get(name).replace('\"', '\'');
                builder.append(", \"").append(name).append("\":\"")
                       .append(doc).append("\"");
            }
            for (final NoCompareField nocompare : parameters.nocompare) {
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
     * @param similarity
     * @param ngDistance
     * @param field
     * @param param
     * @param doc
     * @return CheckFieldResult
     */
    public static CheckFieldResult checkField(final float similarity,
                                              final NGramDistance ngDistance,
                                              final br.bireme.ngrams.Field field,
                                              final String[] param,
                                              final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert param != null;
        assert doc != null;

        final CheckFieldResult ret;

        final String text = param[field.pos].trim();
        if (field instanceof IndexedNGramField) {
            ret = compareIndexedNGramFields(similarity, field, text, doc);
        } else if (field instanceof NGramField) {
            ret = compareNGramFields(ngDistance, field, text, doc);
        } else if (field instanceof DiceField) {
            ret = compareDiceFields(ngDistance, field, text, doc);    
        } else if (field instanceof RegExpField) {
            ret = compareRegExpFields(field, text, doc);
        } else if (field instanceof ExactField) {
            ret = compareExactFields(field, text, doc);
        } else if (field instanceof AuthorsField) {
            ret = compareAuthorFields(field, text, doc);
        } else {
            final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);
            ret = new CheckFieldResult(field.name, text, fldText, Condition.IGNORE,
                                         ngDistance.getDistance(text, fldText));
        }

        return ret;
    }

    private static CheckFieldResult compareIndexedNGramFields(final float similarity,
                                                              final br.bireme.ngrams.Field field,
                                                              final String text,
                                                              final Document doc) {
        assert field != null;
        assert text != null;
        assert doc != null;

        final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);

        return new CheckFieldResult(field.name, text, fldText,
                                                  Condition.IGNORE, similarity);
    }

    private static CheckFieldResult compareNGramFields(final NGramDistance ngDistance,
                                                       final br.bireme.ngrams.Field field,
                                                       final String text,
                                                       final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert text != null;
        assert doc != null;

        final CheckFieldResult ret;
        final String normFldText = doc.get(field.name);
        final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);
        final String normText = Tools.limitSize(
                Tools.normalize(text, OCC_SEPARATOR),MAX_NG_TEXT_SIZE).trim();
        final float similarity = ngDistance.getDistance(normFldText, normText);
        final boolean passed = !normFldText.isEmpty() &&
                               (similarity >= ((NGramField)field).minScore);

        if (passed) {
            ret = new CheckFieldResult(field.name, text, fldText,
                                                 Condition.SIMILAR, similarity);
        } else if (field.contentMatch == Status.DENY_DUP) {
            ret = new CheckFieldResult(field.name, text, fldText,
                                             Condition.DENY_DUP, similarity);
        } else {
            ret = new CheckFieldResult(field.name, text, fldText,
                                             Condition.NOT_SIMILAR, similarity);
        }

        return ret;
    }
    
    private static CheckFieldResult compareDiceFields(final NGramDistance ngDistance,
                                                      final br.bireme.ngrams.Field field,
                                                      final String text,
                                                      final Document doc) {
        assert ngDistance != null;
        assert field != null;
        assert text != null;
        assert doc != null;

        final CheckFieldResult ret;
        final String normFldText = doc.get(field.name);
        final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);
        final String normText = Tools.limitSize(
                Tools.normalize(text, OCC_SEPARATOR),MAX_NG_TEXT_SIZE).trim();
        final float similarity = (float)DiceCoefficient.diceCoefficientOptimized(normFldText, normText);
        final boolean passed = !normFldText.isEmpty() &&
                               (similarity >= ((DiceField)field).minScore);

        if (passed) {
            ret = new CheckFieldResult(field.name, text, fldText,
                                                 Condition.SIMILAR, similarity);
        } else if (field.contentMatch == Status.DENY_DUP) {
            ret = new CheckFieldResult(field.name, text, fldText,
                                             Condition.DENY_DUP, similarity);
        } else {
            ret = new CheckFieldResult(field.name, text, fldText,
                                             Condition.NOT_SIMILAR, similarity);
        }

        return ret;
    }

    private static CheckFieldResult compareRegExpFields(final br.bireme.ngrams.Field field,
                                                        final String text,
                                                        final Document doc) {
        assert field != null;
        assert text != null;
        assert doc != null;

        final CheckFieldResult ret;
        final String normFldText = doc.get(field.name);
        final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);
        final String normText = Tools.limitSize(
                Tools.normalize(text, OCC_SEPARATOR),MAX_NG_TEXT_SIZE).trim();
        final RegExpField regExp = (RegExpField)field;
        final Matcher mat = regExp.matcher;
        String content1 = null;
        String content2 = null;

        mat.reset(normFldText);
        if (mat.find()) {
            content1 = mat.group(regExp.groupNum);
            content1 = (content1 == null) ? "" : content1.trim();
        }
        mat.reset(normText);
        if (mat.find()) {
            content2 = mat.group(regExp.groupNum);
            content2 = (content2 == null) ? "" : content2.trim();
        }

        switch (compareFields(field, content1, content2)) {
            case -3:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                         Condition.DENY_DUP, 0);
                break;
            case -2:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                        Condition.MAX_SCORE, 0);
                break;
            case -1:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                      Condition.NOT_SIMILAR, 0);
                break;
            case 0:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                           Condition.IGNORE, 0);
                break;
            default:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                          Condition.SIMILAR, 1);
        }

        return ret;
    }

    private static CheckFieldResult compareExactFields(final br.bireme.ngrams.Field field,
                                                       final String text,
                                                       final Document doc) {
        assert field != null;
        assert text != null;
        assert doc != null;

        final CheckFieldResult ret;
        final String normFldText = doc.get(field.name);
        final String fldText = doc.get(field.name + NOT_NORMALIZED_FLD);
        final String normText = Tools.limitSize(
                Tools.normalize(text, OCC_SEPARATOR),MAX_NG_TEXT_SIZE).trim();

        switch (compareFields(field, normText, normFldText)) {
            case -3:
                    ret = new CheckFieldResult(field.name, text, fldText,
                                                        Condition.DENY_DUP, 0);
                    break;
            case -2:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                        Condition.MAX_SCORE, 0);
                break;
            case -1:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                      Condition.NOT_SIMILAR, 0);
                break;
            case 0:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                           Condition.IGNORE, 0);
                break;
            default:
                ret = new CheckFieldResult(field.name, text, fldText,
                                                          Condition.SIMILAR, 1);
            }

        return ret;
    }

    private static CheckFieldResult compareAuthorFields(final br.bireme.ngrams.Field field,
                                                        final String text,
                                                        final Document doc) {
        assert field != null;
        assert text != null;
        assert doc != null;

        final String[] normTextAuthors = Tools.normalize2(text, OCC_SEPARATOR);
        final String[] normFldAuthors = doc.getValues(field.name);
        final String fldAuthors = Tools.mkString(
                doc.getValues(field.name + NOT_NORMALIZED_FLD), OCC_SEPARATOR);
        final boolean maxValue = (field.contentMatch == Status.MAX_SCORE);
        final boolean denyDup = (field.contentMatch == Status.DENY_DUP);
        final CheckFieldResult ret;

        if ((normTextAuthors.length > 0) && (normFldAuthors.length > 0)) {
            final Set<String> authorSet = new HashSet<>();

            for (String fldAuthor: normFldAuthors) {
                final String au = fldAuthor.trim();
                if (!au.isEmpty()) authorSet.add(au);
            }
            boolean found = true;
            for (String author: normTextAuthors) {
                final String aut = author.trim();
                if ((!aut.isEmpty()) && (!authorSet.isEmpty())) { // Do not remove
                    if (!checkAuthor(aut, authorSet)) {
                        found = false;
                        break;
                    }
                }
            }
            if (found) {
                final float sym = (float)
                     DiceCoefficient.diceCoefficientOptimized(text, fldAuthors);
                if (sym < 0.75) {
                    if (denyDup) {
                        ret = new CheckFieldResult(field.name, text, fldAuthors,
                                                       Condition.DENY_DUP, sym);
                    } else {
                        ret = new CheckFieldResult(field.name, text, fldAuthors,
                                                    Condition.NOT_SIMILAR, sym);
                    }
                } else {
                    ret = new CheckFieldResult(field.name, text, fldAuthors,
                                                        Condition.SIMILAR, sym);
                }
            } else {
                if (denyDup) {
                    ret = new CheckFieldResult(field.name, text, fldAuthors,
                                               Condition.DENY_DUP, 0);
                } else {
                    ret = new CheckFieldResult(field.name, text, fldAuthors,
                     maxValue ? Condition.MAX_SCORE : Condition.NOT_SIMILAR, 0);
                }
            }
        } else {
            if (denyDup) {
                ret = new CheckFieldResult(field.name, "", "",
                                                         Condition.DENY_DUP, 0);
            } else {
                ret = new CheckFieldResult(field.name, "", "",
                     maxValue ? Condition.MAX_SCORE : Condition.NOT_SIMILAR, 0);
            }
        }

        return ret;
    }

    private static boolean checkAuthor(String author,
                                       Set<String> authors) {
        final double THRESHOLD = 0.4;
        String moreSimilar = null;
        double similarity = THRESHOLD;

        for (String aut: authors) {
            final Double diceCoefficient = DiceCoefficient.diceCoefficientOptimized(author, aut);
//System.out.println("\t\tdiceCoefficient=" + diceCoefficient);
            if (diceCoefficient >= similarity) {
                similarity = diceCoefficient;
                moreSimilar = aut;
            }
        }
        final boolean ret = (moreSimilar != null);
        if (ret) authors.remove(moreSimilar);

        return ret;
    }

    /**
     *
     * @param field - configuration of the document
     * @param fld - text used to search
     * @param text - txt from index
     * @return -3 : fields dont match and contentMatch is DENY_DUP
     *         -2 : fields dont match and contentMatch is MAX_SCORE
     *         -1 : fields dont match and contentMatch is required
     *          0 : ignore this field
     *          1 : fields match
     */
    private static int compareFields(final br.bireme.ngrams.Field field,
                                     final String fld,
                                     final String text) {
        assert field != null;

        final int ret;
        final String xfld = (fld == null) ? "" : fld.trim();
        final String xtext = (text == null) ? "" : text.trim();

        if (xfld.equals(xtext)) {
            ret = xfld.isEmpty() ? 0 : 1;
        } else {
            if (xfld.isEmpty() || xtext.isEmpty()) ret = 0;
            else if (field.contentMatch == Status.DENY_DUP) ret = -3;
            else if (field.contentMatch == Status.MAX_SCORE) ret = -2;
            else ret = -1;
        }

        return ret;
    }

    private static void writeOutput(final Parameters parameters,
                                    final List<Result> results,
                                    final BufferedWriter writer,
                                    final boolean report)
                                                            throws IOException {
        assert parameters != null;
        assert results != null;
        assert writer != null;

        boolean first = true;
        final Set<String> pipes = report ? result2PipeReport(parameters, results)
                                           : results2pipe(parameters, results);

        writer.newLine();
        for (final String pipe : pipes) {
            if (first) {
                first = false;
            } else {
                writer.newLine();
            }
            try {
                writer.append(pipe);
            } catch(final IOException ioe) {
                System.err.println("Write error. String:" + pipe);
            }
        }
    }

    public static void export(final NGIndex index,
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
        final Bits liveDocs = MultiBits.getLiveDocs(reader);  // current version
        //final Bits liveDocs = MultiFields.getLiveDocs(reader);  // Lucene 4.0
        final BufferedWriter writer = Files.newBufferedWriter(
                       Paths.get(outFile),
                       Charset.forName(outFileEncoding),
                       StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        boolean first = true;

        for (final Map.Entry<Integer,br.bireme.ngrams.Field> entry :
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

        for (final Map.Entry<Integer,String> entry : fields.entrySet()) {
            if (cur > 0) {
                sb.append("|");
            }
            final int pos = entry.getKey();
            if (cur == pos) {
                final String[] flds = doc.getValues(entry.getValue());
                if (flds != null) {
                    boolean first = true;
                    String field = "";
                    for (final String fld: flds) {
                        if (first) {
                            first = false;
                        } else {
                            field += OCC_SEPARATOR;
                        }
                        field += fld;
                    }
                    sb.append(field);
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
          "\n\n   search1 <indexPath> <confFile> <confFileEncoding> <inFile> <inFileEncoding> <outFile> [<outFileEncoding>] [--report] - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <inFile> - input file. See format bellow" +
          "\n       <inFileEncoding> - input file encoding" +
          "\n       <outFile> - output file. See format bellow" +
          "\n       [<outFileEncoding>] - output file encoding. Default = UTF-8" +
          "\n       [--report] - if present the output will be a complete version otherwise a simple version will be shown" +
          "\n\n   search2 <indexPath> <confFile> <confFileEncoding> <text> [--report] - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <text> - text used to find documents" +
          "\n       [--report] - if present the output will be a complete version otherwise a simple version will be shown" +
          "\n\n   search3 <indexPath> <confFile> <confFileEncoding> <text> [--report] - find similar documents - IT DOES NOT USE SIMILARITY FUNCTION." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <text> - text used to find documents" +
          "\n       [--report] - if present the output will be a complete version otherwise a simple version will be shown" +
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
    public static void main(final String[] args) throws IOException, ParseException,
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
            index.close();
            System.out.println("Indexing has finished.");
        } else if (args[0].equals("search1")) {
            if (args.length < 7) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            if (args.length == 7) {
                search(index, schema, args[4], args[5], args[6], "utf-8", false, false);
            } else if (args.length == 8) {
                if (args[7].equals("---report")) {
                    search(index, schema, args[4], args[5], args[6], "utf-8", true, false);
                } else {
                    search(index, schema, args[4], args[5], args[6], "utf-8", false, false);
                }
            } else {
                final String outFileEncoding = args[7].equals("--report") ? args[8] : args[7];
                search(index, schema, args[4], args[5], args[6], outFileEncoding, true, false);
            }
            index.close();
            System.out.println("Searching has finished.");
        } else if (args[0].equals("search2")) {
            if (args.length < 4+1) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            Set<String> set = null;

            if (args.length == 5) {
                set = search(index, schema, args[4], false, false);
            } else if (args[5].equals("--report")) {
                set = search(index, schema, args[4], true, false);
            } else usage();
            if ((set == null) || (set.isEmpty())) {
                System.out.println("No result was found.");
            } else {
                int pos = 0;
                for(final String res : set) {
                    System.out.println((++pos) + ") " + res);
                }
            }
            index.close();
        } else if (args[0].equals("search3")) {
            if (args.length < 5) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            Set<String> set = null;

            if (args.length == 5) {
                set = srcWithoutSimil(index, schema, args[4], false, false);
            } else if (args[5].equals("--report")) {
                set = srcWithoutSimil(index, schema, args[4], true, false);
            } else usage();
            if ((set == null) || (set.isEmpty())) {
                System.out.println("No result was found.");
            } else {
                int pos = 0;
                for(final String res : set) {
                    System.out.println((++pos) + ") " + res);
                }
            }
            index.close();
        } else if (args[0].equals("export")) {
            if (args.length != 6) {
                usage();
            }
            final NGIndex index = new NGIndex("dummy", args[1], true);
            export(index, schema, args[4], args[5]);
            index.close();
        } else {
            usage();
        }

        final long endTime = new GregorianCalendar().getTimeInMillis();
        final long difTime = (endTime - startTime) / 1000;

        System.out.println("\nElapsed time: " + difTime + "s");
    }
}
