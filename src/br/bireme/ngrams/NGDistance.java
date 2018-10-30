/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import static br.bireme.ngrams.NGrams.MAX_NG_TEXT_SIZE;
import static br.bireme.ngrams.NGrams.OCC_SEPARATOR;
import java.io.IOException;

/**
 *
 * @author Heitor Barbieri
 * date: 20150901
 */
public class NGDistance {
    private static void usage() {
        System.err.println("usage: NGDistance <str1> <str2> [-normalize]");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
//System.out.println("args.length=" + args.length);
        if (args.length < 2) {
            usage();
        }
        final String str1;
        final String str2;

        if (args.length > 2) {
            if (args[2].equals("-normalize")) {
                str1 = Tools.limitSize(Tools.normalize(args[0], OCC_SEPARATOR),
                                                       MAX_NG_TEXT_SIZE).trim();
                str2 = Tools.limitSize(Tools.normalize(args[1], OCC_SEPARATOR),
                                                       MAX_NG_TEXT_SIZE).trim();
            } else {
                str1 = null;
                str2 = null;
                usage();
            }
        } else {
            str1 = args[0];
            str2 = args[1];
        }
        System.out.println("str1=" + str1);
        System.out.println("str2=" + str2);
        System.out.println("distance=" + Tools.NGDistance(str1, str2));
    }
}
