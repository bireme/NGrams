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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
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
    static class Result implements Comparator<Result> {
        final String[] param;
        final Document doc;
        final float similarity;
                
        Result(final String[] param,
               final Document doc,
               final float similarity) {
            assert param != null;
            assert doc != doc;
            assert similarity >= 0;
            
            this.param = param;
            this.doc = doc;
            this.similarity = similarity;
        }        

        @Override
        public int compare(final Result t1, 
                           final Result t2) {
            return (t1.similarity < t2.similarity) ? -1
                                     : (t1.similarity == t2.similarity) ? 0 : 1;
        }
    }
    
    /*
       Maximum ngram text size. If longer then it, it will be truncated.
    */
    public static final int MAX_NG_TEXT_SIZE = 80;

    // <id>|<ngram index/search text>|<content>|...|<content>
    public static void index(final String indexName,
                             final String inFile,
                             final String inFileEncoding) throws IOException {
        if (indexName == null) {
            throw new NullPointerException("indexName");
        }
        if (inFile == null) {
            throw new NullPointerException("inFile");
        }
        if (inFileEncoding == null) {
            throw new NullPointerException("inFileEncoding");
        }

        final Charset charset = Charset.forName(inFileEncoding);
        final NGInstance instance = Instances.getInstance(indexName);
        final IndexWriter writer = instance.getIndexWriter();
        final Parameters parameters = instance.getParameters();
        final br.bireme.ngrams.Field[] flds = parameters.fields;
        int cur = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), charset)) {
            writer.deleteAll();

            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                final String[] split = line.replace(':', ' ').trim()
                                           .split(" *\\| *", Integer.MAX_VALUE);
                if (split.length != parameters.nfields) {
                    throw new IOException("invalid number of fields: " + line);
                }
                final Document doc = createDocument(flds, split, parameters);
                if (doc != null) {
                    writer.addDocument(doc);
                }
                if (++cur % 10000 == 0) {
                    System.out.println(">>> " + cur);
                }
            }
            writer.forceMerge(1); // optimize index
            writer.close();
        }
    }

    private static Document createDocument(final br.bireme.ngrams.Field[] fields,
                                           final String[] flds,
                                           final Parameters parameters)
                                                            throws IOException {
        assert fields != null;
        assert flds != null;
        assert parameters != null;

        Document doc = checkFieldsPresence(parameters, flds) ? new Document()
                                                             : null;
        final Set<String> names = new HashSet<>();

        if (doc != null) {
            for (int idx = 0; idx < parameters.nfields; idx++) {
                final String content = flds[idx];
                final br.bireme.ngrams.Field fld = fields[idx];
                final String fname = fld.name;
                if (fld instanceof IndexedNGramField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    final String ncontent = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new TextField(fld.name, ncontent, Field.Store.YES));
                } else if (fld instanceof SourceField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    final String ncontent = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new StoredField(fld.name, ncontent));
                } else if (fld instanceof IdField) {
                    if (names.contains(fname)) {
                        doc = null;
                        break;
                    }
                    doc.add(new StoredField(fld.name, content));
                } else {
                    final String ncontent = Tools.limitSize(
                             Tools.normalize(content), MAX_NG_TEXT_SIZE).trim();
                    doc.add(new StoredField(fld.name, ncontent));
                }
                names.add(fname);
            }
        }
        return doc;
    }

    private static boolean checkFieldsPresence(final Parameters parameters,
                                               final String[] param) {
        assert parameters != null;
        assert param != null;

        boolean ok = true;

        for (int idx = 0; idx < param.length; idx++) {
            ok = checkPresence(idx, parameters, param, new HashSet<Integer>());
            if (!ok) {
                break;
            }
        }
        return ok;
    }

    private static boolean checkPresence(final int pos,
                                         final Parameters parameters,
                                         final String[] param,
                                         final Set<Integer> checked) {
        assert pos >= 0;
        assert parameters != null;
        assert param != null;
        assert checked != null;

        final boolean ok;

        if ((pos == -1) || (checked.contains(pos))) {
            ok = true;
        } else {
            final br.bireme.ngrams.Field field = parameters.fields[pos];

            checked.add(pos);
            ok = (param[pos].isEmpty()) ? (field.presence != Status.REQUIRED)
                         : checkPresence(field.requiredField, parameters, param,
                                                                       checked);
        }
        return ok;
    }

    public static void search(final String indexName,
                              final String inFile,
                              final String inFileEncoding,
                              final String outFile,
                              final String outFileEncoding) throws IOException,
                                                                ParseException {
        if (indexName == null) {
            throw new NullPointerException("indexName");
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
        final NGInstance instance = Instances.getInstance(indexName);
        final IndexSearcher searcher = instance.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)Instances.getAnalyzer();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        int cur = 0;
        try (final BufferedReader reader = Files.newBufferedReader(
                                          new File(inFile).toPath(), inCharset);
             final BufferedWriter writer = Files.newBufferedWriter(
                                      new File(outFile).toPath(), outCharset)) {
            writer.append("search_doc_id|similarity|index_doc_id|" +
                          "ngram_search_text|ngram_index_text|search_source|" + 
                                                              "index_source\n");
            
            final Set<Result> results = new TreeSet<>();
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (++cur % 1000 == 0) {
                    System.out.println("<<< " + cur);
                }
                
                results.clear();
                searchRaw(indexName, searcher, analyzer, ngDistance, line, 
                                                                id_id, results);
                if (!results.isEmpty()) {
                    writeOutput(indexName, results, writer);
                }
            }
            searcher.getIndexReader().close();
        }
    }

    public static Set<String> search(final Set<String> indexNames,
                                     final String text,
                                     final boolean json) throws IOException,
                                                                ParseException {
        if (indexNames == null) {
            throw new NullPointerException("indexNames");
        }
        if (text == null) {
            throw new NullPointerException("text");
        }
        final Set<Result> results = new HashSet<>();
        final Set<String> strSet = new TreeSet<>();
        
        for (String indexName : indexNames) {
            final NGInstance instance = Instances.getInstance(indexName);
            final Parameters parameters = instance.getParameters();                    
            
            results.clear();
            searchRaw(indexName, text, results);
            final Set<String> auxSet = json ? results2json(parameters, results)
                                            : results2pipe(parameters, results);
            strSet.addAll(auxSet);
        }
        
        return strSet;
    }
        
    public static Set<String> search(final String indexName,
                                     final String text,
                                     final boolean json) throws IOException,
                                                                ParseException {
        if (indexName == null) {
            throw new NullPointerException("indexName");
        }
        if (text == null) {
            throw new NullPointerException("text");
        }        
        final NGInstance instance = Instances.getInstance(indexName);
        final Parameters parameters = instance.getParameters();
        final Set<Result> results = new TreeSet<>();
        
        searchRaw(indexName, text, results);
        
        return json ? results2json(parameters, results) 
                    : results2pipe(parameters, results);
    }
    
    private static void searchRaw(final String indexName,
                                  final String text,
                                  final Set<Result> results) 
                                            throws IOException, ParseException {
        assert indexName != null;
        assert text != null;
        assert results != null;
        
        final NGInstance instance = Instances.getInstance(indexName);
        final IndexSearcher searcher = instance.getIndexSearcher();
        final NGAnalyzer analyzer = (NGAnalyzer)Instances.getAnalyzer();
        final NGramDistance ngDistance = new NGramDistance(
                                                       analyzer.getNgramSize());
        final Set<String> id_id = new HashSet<>();
        searchRaw(indexName, searcher, analyzer, ngDistance, text, id_id, 
                                                                       results);
        searcher.getIndexReader().close();
    }
    
    // <id>|<ngram search text>|<content>|...|<content>
    private static void searchRaw(final String indexName,
                                  final IndexSearcher searcher,
                                  final NGAnalyzer analyzer,
                                  final NGramDistance ngDistance,
                                  final String text,
                                  final Set<String> id_id,
                                  final Set<Result> results)
                                            throws IOException, ParseException {
        assert indexName != null;
        assert searcher != null;
        assert analyzer != null;
        assert ngDistance != null;
        assert text != null;
        assert id_id != null;
        assert results != null;

        final NGInstance instance = Instances.getInstance(indexName);
        final Parameters parameters = instance.getParameters();
        final String[] param = text.trim().split(" *\\| *", Integer.MAX_VALUE);
        if (param.length != parameters.nfields) {
            throw new IOException(text);
        }
        final String fname = parameters.indexed.name;
        final QueryParser parser = new QueryParser(fname, analyzer);
        final String ntext = Tools.limitSize(Tools.normalize(
                       param[parameters.indexed.pos]), MAX_NG_TEXT_SIZE).trim();
        if (!ntext.isEmpty()) {
            final Query query = parser.parse(QueryParser.escape(ntext));
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
                                            param, doc, ngDistance, similarity);
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
                                       final float similarity) {
        assert id_id != null;
        assert parameters != null;
        assert stext != null;
        assert param != null;
        assert doc != null;
        assert ngDistance != null;
        assert similarity >= 0;

        final Result ret;
        final br.bireme.ngrams.Field[] fields = parameters.fields;
        int matchedFields = 0;

        for (int pos = 0; pos < param.length; pos++) {
            final int val =
                      checkField(ngDistance, fields[pos], param[pos], doc);
            if (val == -1) {
                matchedFields = -1;
                break; // skip this document because it does not follow requests
            }
            matchedFields += val;
        }
        if (matchedFields > 0) {
            if (checkScore(parameters, param, similarity, matchedFields)) {
                final String id1 = param[parameters.id.pos];
                final String id2 = (String)doc.get("id");
                final String id1id2 = (id1.compareTo(id2) <= 0) ?
                                          (id1 + "_" + id2) : (id2 + "_" + id1);
                if (id_id.contains(id1id2)) {
                    ret = null;
                } else {
                    id_id.add(id1id2);
                    ret = new NGrams.Result(param, doc, similarity);
                }
            } else {
                ret = null;
            }
        } else {
            ret = null;
        }
        return ret;
    }
    
    private static Set<String> results2pipe(final Parameters parameters,
                                            final Set<Result> results) {
        assert parameters != null;
        assert results != null;
     
        final Set<String> ret = new TreeSet<>();
        
        for (Result result : results) {
            final String[] param = result.param;
            final Document doc = result.doc;
            final String itext = (String)doc.get(parameters.indexed.name);
            final String stext = Tools.limitSize(Tools.normalize(
                           param[parameters.indexed.pos]), MAX_NG_TEXT_SIZE).trim();
            final String id1 = param[parameters.id.pos];
            final String id2 = (String)doc.get("id");
            final String src1 = param[parameters.src.pos];
            final String src2 = (String)doc.get("source");
            final String str = id1 + "|" + result.similarity + "|" + id2 + "|"+ 
                                  stext + "|" + itext + "|" + src1 + "|" + src2;
            ret.add(str);
        }        
        return ret;
    }
    
    private static Set<String> results2json(final Parameters parameters,
                                            final Set<Result> results) {
        assert parameters != null;
        assert results != null;
        
        String name;
        final StringBuilder builder = new StringBuilder();        
        final Set<String> ret = new TreeSet<>();
        
        for (Result result : results) {
            builder.setLength(0);
            builder.append("{");
            name = parameters.src.name;
            builder.append(" \"").append(name).append("\":\"")
                   .append(result.doc.get(name)).append("\",");
            name = parameters.id.name;
            builder.append(" \"").append(name).append("\":\"")
                   .append(result.doc.get(name)).append("\",");
            name = parameters.indexed.name;
            builder.append(" \"").append(name).append("\":\"")
                   .append(result.doc.get(name)).append("\"");
            for (ExactField exact : parameters.exacts) {
                name = exact.name;
                builder.append(", \"").append(name).append("\":\"")
                       .append(result.doc.get(name)).append("\"");
            }
            for (ExactField exact : parameters.exacts) {
                name = exact.name;
                builder.append(", \"").append(name).append("\":\"")
                       .append(result.doc.get(name)).append("\"");
            }
            for (NGramField ngrams : parameters.ngrams) {
                name = ngrams.name;
                builder.append(", \"").append(name).append("\":\"")
                       .append(result.doc.get(name)).append("\"");
            }
            for (RegExpField regexps : parameters.regexps) {
                name = regexps.name;
                builder.append(", \"").append(name).append("\":\"")
                       .append(result.doc.get(name)).append("\"");
            }
            for (NoCompareField nocompare : parameters.nocompare) {
                name = nocompare.name;
                builder.append(", \"").append(name).append("\":\"")
                       .append(result.doc.get(name)).append("\"");
            }        
            builder.append(" }");
            ret.add(builder.toString());
        }
        
        return ret;
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

    private static boolean isMaxScore(
                             final Set<? extends br.bireme.ngrams.Field> fields,
                             final String[] param) {
        assert fields != null;
        assert param != null;

        boolean maxScore = false;

        for (br.bireme.ngrams.Field field : fields) {
            if ((field.presence == Status.MAX_SCORE) &&
                (param[field.pos].isEmpty())) {
                maxScore = true;
                break;
            }
            if (field.content != null) {
                boolean found = false;
                for (String str: field.content) {
                    if (str.equals(param[field.pos])) {
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

        if (field instanceof NGramField) {
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
                ret = text.isEmpty() ? 0 : -1;
            }
        } else if (fld.equals(text)) {
            ret = 1;
        } else {
            final float similarity = ngDistance.getDistance(fld, text);
            ret = (similarity >= ((NGramField)field).minScore) ? 1
                           : field.contentMatch != Status.OPTIONAL ? 0 : -1;
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
                                : (field.contentMatch == Status.REQUIRED ? -1
                                                                         : 0));
    }

    private static void writeOutput(final String indexName,
                                    final Set<Result> results,
                                    final BufferedWriter writer)
                                                            throws IOException {
        assert indexName != null;
        assert results != null;
        assert writer != null;

        final NGInstance instance = Instances.getInstance(indexName);
        final Parameters parameters = instance.getParameters();
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
        System.err.println("Usage: NGrams (config|index|search1|search2)" +
          /*"\n\n   config <indexPath> <indexAlias> <confFile> [<confFileEncoding>] - add an index configuration file." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <indexAlias> - nickname of the index" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." + */
          "\n\n   index <indexName> <inFile> <inFileEncoding> - index a list of documentes." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <indexAlias> - nickname of the index" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <indexName> - Lucene index name as defined in the configuration file" +
          "\n       <inFile> - input file. See format bellow" +
          "\n       <inFileEncoding> - input file encoding" +
          "\n\n   search1 <indexName> <inFile> <inFileEncoding> <outFile> [<outFileEncoding>] - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <indexAlias> - nickname of the index" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <indexName> - Lucene index name as defined in the configuration file" +
          "\n       <inFile> - input file. See format bellow" +
          "\n       <inFileEncoding> - input file encoding" +
          "\n       <outFile> - output file. See format bellow" +
          "\n       [<outFileEncoding>] - output file encoding. Default = UTF-8" +
          "\n\n   search2 <indexName> <text> - find similar documents." +
          "\n       <indexPath> - Lucene index name/path" +
          "\n       <indexAlias> - nickname of the index" +
          "\n       <confFile> - xml configuration file. See documentation for format." +
          "\n       <confFileEncoding> - configuration file character encoding." +
          "\n       <indexName> - Lucene index name as defined in the configuration file" +
          "\n       <text> - text used to find documents" +
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

        if (args.length < 3) {
            usage();
        }
        if (args[0].equals("config")) {
            if (args.length < 4) {
                usage();
            }
            final String encoding = (args.length == 5) ? args[4] : "UTF-8";
            Instances.addInstance(args[1], args[2], args[3], encoding);
        } else if (args[0].equals("index")) {
            if (args.length != 4+4) {
                usage();
            }
            Instances.addInstance(args[1], args[2], args[3], args[4]);
            index(args[4+1], args[4+2], args[4+3]);
            System.out.println("Indexing has finished.");
        } else if (args[0].equals("search1")) {
            if (args.length < 4+5) {
                usage();
            }
            final String encoding = (args.length == 4+6) ? args[4+5] : "UTF-8";
            Instances.addInstance(args[1], args[2], args[3], args[4]);
            search(args[4+1], args[4+2], args[4+3], args[4+4], encoding);
            System.out.println("Searching has finished.");
        } else if (args[0].equals("search2")) {
            if (args.length < 4+3) {
                usage();
            }
            Instances.addInstance(args[1], args[2], args[3], args[4]);
            final Set<String> set = search(args[4+1], args[4+2], false);
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