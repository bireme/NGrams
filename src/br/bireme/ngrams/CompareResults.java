/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;

/**
 *
 * @author Heitor Barbieri
 * date: 20160822
 */
public class CompareResults {
    private static void usage() {
        System.err.println("Usage: CompareResults <resultFile> <indexPath> " +
                           "<outputFile> [<encoding>]");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 3) usage();

        final String encoding = args.length > 3 ? args[3] : "utf-8";

        compare(args[0], args[1], args[2], encoding);
    }

    private static void compare(final String resultFile,
                                final String indexPath,
                                final String outputFile,
                                final String encoding) throws IOException {
        assert resultFile != null;
        assert indexPath != null;
        assert outputFile != null;
        assert encoding != null;

        try (DirectoryReader ireader = DirectoryReader.open(
                new MMapDirectory(new File(indexPath).toPath()))) {
            final IndexSearcher isearcher = new IndexSearcher(ireader);

            try (BufferedReader breader = Files.newBufferedReader(
                                                 new File(resultFile).toPath(),
                                                 Charset.forName(encoding));
                 BufferedWriter bwriter = Files.newBufferedWriter(
                                                 new File(outputFile).toPath(),
                                                 Charset.forName(encoding))) {
                boolean first = true;  // first line

                while (true) {
                    final String line = breader.readLine();
                    if (line == null) break;
                    if (first) {
                        first = false; // drop header line (first)
                    } else {
                        final String line2 = line.trim();
                        if (!line2.isEmpty()) {
                            final String[] split = line2.split("\\|");
                            checkDocs(split[1], split[2], split[3], isearcher,
                                                                       bwriter);
                        }
                    }
                }
            }
        }
    }

    private static void checkDocs(final String similarity,
                                  final String docId1,
                                  final String docId2,
                                  final IndexSearcher isearcher,
                                  final BufferedWriter bwriter)
                                                            throws IOException {
        assert similarity != null;
        assert docId1 != null;
        assert docId2 != null;
        assert isearcher != null;
        assert bwriter != null;

        final Query query1 = new TermQuery(new Term("id", docId1));
        final Query query2 = new TermQuery(new Term("id", docId2));
        final TopDocs top1 = isearcher.search(query1, 1);
        final TopDocs top2 = isearcher.search(query2, 1);
        final ScoreDoc[] scores1 = top1.scoreDocs;
        final ScoreDoc[] scores2 = top2.scoreDocs;

        if ((scores1.length > 0) && (scores2.length > 0)) {
            final Document doc1 = isearcher.doc(scores1[0].doc);
            final Document doc2 = isearcher.doc(scores2[0].doc);

            writeDocDifferences(similarity, doc1, doc2, bwriter);
        }
    }

    private static void writeDocDifferences(final String similarity,
                                            final Document doc1,
                                            final Document doc2,
                                            final BufferedWriter bwriter)
                                                            throws IOException {
        assert similarity != null;
        assert doc1 != null;
        assert doc2 != null;
        assert bwriter != null;

        final StringBuilder builder = new StringBuilder();
        final Set<String> diff = new HashSet<>();
        final String id1 = doc1.get("id");
        final String id2 = doc2.get("id");

        for (IndexableField fld : doc1.getFields()) {
            final String name = fld.name();

            if (name.endsWith("~notnormalized")) {
                if (!name.startsWith("id~")) {
                    final String value1 = fld.stringValue();
                    final String value2 = doc2.get(name);
                    if (((value1 == null) && (null != value2)) ||
                                                       !value1.equals(value2)) {
                        final String name2 = name.substring(0,
                                                         name.lastIndexOf('~'));
                        diff.add("[" + name2 + "]|" + value1 + "|" + value2);
                    }
                }
            }
        }
        if (diff.isEmpty()) {
            builder.append("<identical>|");
            builder.append(id1 + "|" + id2 + "\n");
        } else {
            if (similarity.equals("1.0")) {
                builder.append("<very similar>|");
            } else {
                builder.append("<similar>|");
            }
            builder.append(id1 + "|" + id2 + "\n");
            for (String di: diff) {
                builder.append(di);
                builder.append("\n");
            }
        }
        builder.append("\n");
        bwriter.append(builder.toString());
    }
}
