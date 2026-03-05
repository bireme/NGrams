/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.spell.NGramDistance;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Heitor Barbieri
 * date: 20150626
 */
public class Tools {
    public static void showTerms(final String indexName,
                                 final String fieldName) throws IOException {
        if (indexName == null) {
            throw new NullPointerException("indexName");
        }
        if (fieldName == null) {
            throw new NullPointerException("fieldName");
        }
        try (Directory directory = FSDirectory.open(
            new File(indexName).toPath())) {   // current version
            //new File(indexName))) {                   // Lucene 4.0
            final DirectoryReader ireader = DirectoryReader.open(directory);
            final List<LeafReaderContext> leaves = ireader.leaves();    // current version
            //final List<AtomicReaderContext> leaves = ireader.leaves();     // Lucene 4.0
            if (leaves.isEmpty()) {
                throw new IOException("empty leaf readers list");
            }        
            final Terms terms = leaves.get(0).reader().terms(fieldName);
            /*final Terms terms = SlowCompositeReaderWrapper.wrap(ireader)
                    .terms(fieldName);*/
            if (terms != null) {
                final TermsEnum tenum = terms.iterator();  // current version
                //final TermsEnum tenum = terms.iterator(null);  // Lucene 4.0
                int pos = 0;
                // PostingsEnum penum = null;

                while (true) {
                    final BytesRef br = tenum.next();
                    if (br == null) {
                        break;
                    }
                    System.out.println((++pos) + ") term=[" + br.utf8ToString()
                                                                        + "] ");
                    /*
                    penum = tenum.postings(penum, PostingsEnum.OFFSETS);
                    while (penum.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
                        System.out.print(" startOffset=" + penum.startOffset());
                        System.out.println(" endOffset:" + penum.endOffset());
                    }
                    */
                }
            }
        }
    }

    public static void showTokens(final Analyzer analyzer,
                                  final String fieldName,
                                  final String text) throws IOException {
        //TokenStream tokenStream = analyzer.tokenStream(fieldName, text); // current version
        TokenStream tokenStream = analyzer.tokenStream(fieldName, new StringReader(text));  // Lucene 4.0
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            int startOffset = offsetAttribute.startOffset();
            int endOffset = offsetAttribute.endOffset();
            final String term = charTermAttribute.toString();

            System.out.println(term + " [" + startOffset + "," + endOffset + "]");
        }
    }

    /**
     * If the input string len is less or equal to the max size then the output
     * will be the input string, if it is greater then we will take a left
     * substring, a middle substring and a right substring of equal size and
     * join the three to form the output string.
     * @param in input string
     * @param maxSize output string size
     * @return new string formed by input string chopped
     */
    public static String limitSize(final String in,
                                   final int maxSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }

        final int len = in.length();
        final String ret;
        if (len <= maxSize) {
            ret = in;
        } else {
            final int midLenSize = len / 2;
            final int thrLen = maxSize / 3;
            final int mThrLen = maxSize % 3;
            final int leftSize = thrLen;
            final int midSize = (mThrLen == 0) ? thrLen : (thrLen + 1);
            final int rightSize = (mThrLen < 2) ? thrLen : (thrLen + 1);
            final int midMidSize = midSize / 2;
            final int midStartPos = midLenSize - midMidSize;

            ret = in.substring(0, leftSize) +
                  in.substring(midStartPos, midStartPos + midSize) +
                  in.substring(len - rightSize);
        }
        return ret;
    }

    public static void CommonLines(final String file1,
                                   final String file1Encoding,
                                   final String file2,
                                   final String file2Encoding)
                                                            throws IOException {
        if (file1 == null) {
            throw new NullPointerException("file1");
        }
        if (file2 == null) {
            throw new NullPointerException("file2");
        }
        if (file1Encoding == null) {
            throw new NullPointerException("file1Encoding");
        }
        if (file2Encoding == null) {
            throw new NullPointerException("file2Encoding");
        }
        final Charset charset1 = Charset.forName(file1Encoding);
        final Charset charset2 = Charset.forName(file2Encoding);

        try (BufferedReader reader1 = Files.newBufferedReader(
                                          new File(file1).toPath(), charset1);
             BufferedReader reader2 = Files.newBufferedReader(
                                          new File(file2).toPath(), charset2)) {

            final Set<String> set = new HashSet<>();
            while (reader1.ready()) {
                final String line = reader1.readLine().trim();
                if (!line.isEmpty()) {
                    set.add(line);
                }
            }
            while (reader2.ready()) {
                final String line = reader2.readLine().trim();
                if (set.contains(line)) {
                    System.out.println(line);
                }
            }
        }
    }

    /**
     *
     * @param in input String
     * @param occSeparator string separating each occurrence
     * @return the input string with the occurences ordered
     */
    public static String orderOcc(final String in,
                                  final String occSeparator) {
        if (in == null) return null;
        if (occSeparator == null) return in;

        final String[] split = in.split(occSeparator);
        String out = "";

        if (split.length == 1) {
            out = in;
        } else {
            final TreeSet<String> set = new TreeSet<>();
            boolean first = true;

            for (String occ1: split) {
                set.add(occ1.trim());
            }
            for (String occ2: set) {
                if (first) {
                    first = false;
                } else {
                    out += occSeparator;
                }
                out += occ2;
            }
        }
        return out;
    }

    /**
     *
     * @param in input String
     * @param occSeparator string separating each occurrence
     * @return input string with every not digit-alphabetic charater removed,
     *         accents removed and all converted to lower case.
     */
    public static String normalize(final String in,
                                   final String occSeparator) {
        final String ret;

        if (in == null) {
            ret = null;
        } else {
            final String in2 = orderOcc(in.trim().toLowerCase(), occSeparator);
            final String aux = Normalizer.normalize(in2, Normalizer.Form.NFD).
                                               replaceAll("[^a-z0-9]", " ");
            final int len = aux.length();
            final StringBuilder builder = new StringBuilder();
            boolean wasNumber = false;

            for (int idx = 0; idx < len; idx++) {
                final int ch = aux.charAt(idx);
                if ((ch >= 97) && (ch <= 122)) {  // a-z
                    wasNumber = false;
                    builder.append((char)ch);
                } else if ((ch >= 48) && (ch <= 57)) { // 0-9
                    wasNumber = true;
                    builder.append((char)ch);
                } else if ((idx > 0) && (idx < len - 1)) {
                    final int after  = aux.charAt(idx + 1);
                    if (wasNumber && (after >= 48) && (after <= 57)) { // 0-9
                        builder.append(' ');
                    }
                }
            }
            ret = builder.toString();
        }
        return ret;
    }

    /**
     *
     * @param in input String
     * @param occSeparator string separating each occurrence
     * @return string array with every not digit-alphabetic charater removed,
     *         accents removed and all converted to lower case.
     */
    public static String[] normalize2(final String in,
                                      final String occSeparator) {
        final String[] ret;

        if (in == null) {
            ret = null;
        } else {
            ret = in.trim().split(occSeparator);
            final int len = ret.length;

            for (int idx = 0; idx < len; idx++) {
                final String str1 = ret[idx].trim().toLowerCase();
                final String str2 = Normalizer.normalize(str1, Normalizer.Form.NFD).
                    replaceAll("[^a-z0-9]", " ");
                final StringBuilder builder = new StringBuilder();
                boolean wasNumber = false;
                int len2 = str2.length();
                for (int idx2 = 0; idx2 < len2; idx2++) {
                    final int ch = str2.charAt(idx2);
                    if ((ch >= 97) && (ch <= 122)) {  // a-z
                        wasNumber = false;
                        builder.append((char)ch);
                    } else if ((ch >= 48) && (ch <= 57)) { // 0-9
                        wasNumber = true;
                        builder.append((char)ch);
                    } else if ((idx2 > 0) && (idx2 < len2 - 1)) {
                        final int after  = str2.charAt(idx2 + 1);
                        if (wasNumber && (after >= 48) && (after <= 57)) { // 0-9
                            builder.append(' ');
                        }
                    }
                }
                ret[idx] = builder.toString();
            }
            Arrays.sort(ret);
        }

        return ret;
    }

    public static int countOccurrences(final String in,
                                       final char needle) {
        int count = 0;

        if (in != null) {
            for (int i=0; i < in.length(); i++) {
                if (in.charAt(i) == needle) {
                    count++;
                }
            }
        }
        return count;
    }

    public static float NGDistance(final String str1,
                                   final String str2) {
        return new NGramDistance(3).getDistance(str1, str2);
    }

    public static String mkString(final String[] entries,
                                  final String separator) {
        assert entries != null;
        assert separator != null;

        boolean first = true;
        String ret = "";

        for (String entry: entries) {
            if (first) {
                first = false;
            } else {
                ret += ",";
            }
            ret += entry.trim();
        }
        return ret;
    }

   /* public static Map<String, Object> json2Map(final String json) {
        final String j1 = json.replaceAll("\\{ *'", "{\"");
        final String j2 = j1.replaceAll("\\: *'", ":\"");
        final String j3 = j2.replaceAll("\\[ *'", "[\"");
        final String j4 = j3.replaceAll("\\, *'", ",\"");

        final String j5 = j4.replaceAll("' *\\:", "\":");
        final String j6 = j5.replaceAll("' *\\,", "\",");
        final String j7 = j6.replaceAll("' *\\]", "\"]");
        final String j8 = j7.replaceAll("' *\\}", "\"}");

        final ObjectMapper mapperObj = new ObjectMapper();
        Map<String, Object> ret;

        try {
            ret = mapperObj.readValue(j8,
                            new TypeReference<Map<String,String>>(){});
        } catch (IOException e) {
            ret = new HashMap<>();
        }
        return ret;
    }*/

    public static void main(final String[] args) throws IOException {
        //final String iname = "/home/heitor/Projetos/NGrams/lilacs_Sas";
        final String iname = "/home/heitor/Projetos/DeDup/work/lilacs_Sas";

        showTerms(iname, "titulo_artigo");
        //final Analyzer analyzer = new NGAnalyzer(3, false);
        //showTokens(analyzer, "titulo_artigo", "xx ");

        /*
        final String str1 = limitSize(normalize(args[0]), 100);
        final String str2 = limitSize(normalize(args[1]), 100);
        System.out.println("args[0]=" + args[0]);
        System.out.println("args[1]=" + args[1]);
        System.out.println("str1=" + str1);
        System.out.println("str2=" + str2);
        System.out.println("dist=" + NGDistance(str1, str2) + "|" + str1 + "|" +
                                                                          str2);
        */

       /* final String json = "{'nome':'Heitor', 'parentes':['Karim','Flavio', 'Marilene']}";
        //final String json = "{\"nome\":\"Heitor\", \"sobrenome\":\"Barbieri\"}";
        final Map<String, Object> map = json2Map(json);
        int x = 0; */
    }
}
