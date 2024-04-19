/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In memory compare files.
 * @author Heitor Barbieri
 * date: 20150825
 */
public class CompareFiles {
    public static void showLines(final String file1,
                                 final String file1Encoding,
                                 final String file1RegExp,
                                 final String groupName1,
                                 final String file2,
                                 final String file2Encoding,
                                 final String file2RegExp,
                                 final String groupName2,
                                 final boolean showLeft,
                                 final boolean showRight,
                                 final boolean showCommon) throws IOException {
        assert file1 != null;
        assert file1Encoding != null;
        assert file2 != null;
        assert file2Encoding != null;

        final Charset charset1 = Charset.forName(file1Encoding);
        final Charset charset2 = Charset.forName(file2Encoding);
        final Set<String> set1 = new TreeSet<>();
        final Set<String> set2 = new TreeSet<>();
        final Matcher mat1 = (file1RegExp == null) ? null
                                     : Pattern.compile(file1RegExp).matcher("");
        final Matcher mat2 = (file2RegExp == null) ? null
                                     : Pattern.compile(file2RegExp).matcher("");

        try (BufferedReader reader1 = Files.newBufferedReader(
                                          new File(file1).toPath(), charset1);
             BufferedReader reader2 = Files.newBufferedReader(
                                          new File(file2).toPath(), charset2)) {
            while (reader1.ready()) {
                final String line1a = reader1.readLine();
                final String line1b;
                if (mat1 == null) {
                    line1b = line1a;
                } else {
                    mat1.reset(line1a);
                    if (mat1.find()) {
                        line1b = mat1.group(groupName1);
                    } else {
                        line1b = null;
                    }
                }
                if (line1b != null) {
                    set1.add(line1b);
                }
            }
            while (reader2.ready()) {
                final String line2a = reader2.readLine();
                final String line2b;
                if (mat2 == null) {
                    line2b = line2a;
                } else {
                    mat2.reset(line2a);
                    if (mat2.find()) {
                        line2b = mat2.group(groupName1);
                    } else {
                        line2b = null;
                    }
                }
                if (line2b != null) {
                    set2.add(line2b);
                }
            }
        }
        // Eu sei que a implementação está horrível. Não precisa me dizer!
        int totalL = 0;
        int totalR = 0;
        int totalC = 0;

        if (showLeft) {
            for (String left : set1) {
                if (!set2.contains(left)) {
                    System.out.println("<< " + left);
                    totalL++;
                }
            }
        }
        if (showRight) {
            for (String right : set2) {
                if (!set1.contains(right)) {
                    System.out.println(">> " + right);
                    totalR++;
                }
            }
        }
        if (showCommon) {
            for (String left : set1) {
                if (set2.contains(left)) {
                    System.out.println("== " + left);
                    totalC++;
                }
            }
        }

        System.out.println("\ntotal << : " + totalL);
        System.out.println("total >> : " + totalR);
        System.out.println("total == : " + totalC);
    }

    private static void usage() {
        System.err.println("usage: CompareFiles (common|different|onlyleft|onlyright|all)"
                + "\n\t\t    <file1> <file1Encoding> <file2> <file2Encoding> "
                + "\n\t\t    [-file1regexp=<regexp> -groupName1=<name>] "
                + "\n\t\t    [-file2regexp=<regexp> -groupName2=<name>]");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 5) {
            usage();
        }
        final boolean showLeft  = (args[0].equals("different") ||
                                   args[0].equals("onlyleft")  ||
                                   args[0].equals("all"));
        final boolean showRight = (args[0].equals("different") ||
                                   args[0].equals("onlyright") ||
                                   args[0].equals("all"));
        final boolean showCommon = (args[0].equals("common") ||
                                    args[0].equals("all"));
        String f1RegExp = null;
        String gName1 = null;
        String f2RegExp = null;
        String gName2 = null;

        for (int idx = 5; idx < args.length; idx++) {
            if (args[idx].startsWith("-file1regexp=")) {
                f1RegExp = args[idx].substring(13);
            } else if (args[idx].startsWith("-file2regexp=")) {
                f2RegExp = args[idx].substring(13);
            } else if (args[idx].startsWith("-groupName1=")) {
                gName1 = args[idx].substring(12);
            } else if (args[idx].startsWith("-groupName2=")) {
                gName2 = args[idx].substring(12);
            } else {
                usage();
            }
        }
        if ((gName1 != null) && (f1RegExp == null)) {
            usage();
        }
        if ((gName1 == null) && (f1RegExp != null)) {
            usage();
        }
        if ((gName2 != null) && (f2RegExp == null)) {
            usage();
        }
        if ((gName2 == null) && (f2RegExp != null)) {
            usage();
        }
        showLines(args[1], args[2], f1RegExp, gName1,
                  args[3], args[4], f2RegExp, gName2,
                  showLeft, showRight, showCommon);
    }
}
