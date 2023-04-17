/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import static br.bireme.ngrams.NGrams.OCC_SEPARATOR;
import static br.bireme.ngrams.Tools.NGDistance;
import static br.bireme.ngrams.Tools.limitSize;
import static br.bireme.ngrams.Tools.normalize;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 *
 * @author Heitor Barbieri
 * date: 20150811
 */
public class Afiliacao {
    public static void main(String[] args) throws IOException {
        final Charset charset1 = Charset.forName("UTF-8");
        try (BufferedReader reader1 = Files.newBufferedReader(
                  new File("./autorAfiliacaoNormalizado.csv").toPath(), charset1)) {

            //int lineNum = 0;
            String lastLine = null;
            while (reader1.ready()) {
                final String line = reader1.readLine().trim();
                if (line.isEmpty()) {
                    lastLine = "";
                } else {
                    final String[] split = line.split(" *\\| *");
                    if (lastLine != null) {
                        final String str1 =
                                limitSize(normalize(lastLine, OCC_SEPARATOR), 100);
                        final String str2 =
                                limitSize(normalize(split[0], OCC_SEPARATOR), 100);
                        final float dist = NGDistance(str1, str2);
                        if (dist >= 0.35) {
                            System.out.println("dist=" + dist
                                                     + "|" + str1 + "|" + str2);
                        }
                    }
                    lastLine = split[0];
                }
                //lineNum++;
            }
        }
    }
}
