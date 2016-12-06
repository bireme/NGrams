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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Heitor Barbieri
 * date: 2015924
 */
public class CommonLines {

    public static void CommonLines(final String file1,
                                   final String file1Encoding,
                                   final int  file1Columm,
                                   final String file2,
                                   final String file2Encoding,
                                   final int  file2Columm,
                                   final boolean showFile1Lines)
                                                            throws IOException {
        if (file1 == null) {
            throw new NullPointerException("file1");
        }
        if (file1Encoding == null) {
            throw new NullPointerException("file1Encoding");
        }
        if (file1Columm < 0) {
            throw new IllegalArgumentException("file1Columm < 0");
        }
        if (file2 == null) {
            throw new NullPointerException("file2");
        }
        if (file2Encoding == null) {
            throw new NullPointerException("file2Encoding");
        }
        if (file2Columm < 0) {
            throw new IllegalArgumentException("file2Columm < 0");
        }
        final Charset charset1 = Charset.forName(file1Encoding);
        final Charset charset2 = Charset.forName(file2Encoding);
        final Map<String,String> map = new HashMap<>();

        try (BufferedReader reader1 = Files.newBufferedReader(
                                          new File(file1).toPath(), charset1);
             BufferedReader reader2 = Files.newBufferedReader(
                                          new File(file2).toPath(), charset2)) {
            final int scol1 = file1Columm + 1;
            while (reader1.ready()) {
                final String line = reader1.readLine();
                final String[] split = line.split("\\|");
                if (split.length >= scol1) {
                    map.put(split[file1Columm], line);
                }
            }
            final int scol2 = file2Columm + 1;
            while (reader2.ready()) {
                final String line = reader2.readLine();
                final String[] split = line.split("\\|");
                if (split.length >= scol2) {
                    final String value = map.get(split[file2Columm]);
                    if (value != null) {
                        System.out.println(showFile1Lines ? value : line);
                    }
                }
            }
        }
    }

    private static void usage() {
        System.err.println("usage: CommonLines " +
                                "\n\t<file1> <file1Encoding> <file1Columm>" +
                                "\n\t<file2> <file2Encoding> <file2Columm>" +
                                "\n\t\t(file1Lines|file2Lines)");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 7) {
            usage();
        }
        CommonLines(args[0], args[1], Integer.parseInt(args[2]),
                    args[3], args[4], Integer.parseInt(args[5]),
                    args[6].equals("file1Lines"));
    }
}
