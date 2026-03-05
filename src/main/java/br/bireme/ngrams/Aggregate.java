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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Heitor Barbieri
 * date: 20150825
 */
public class Aggregate {
    public static void aggregate(final String file1,
                                 final String file1Encoding,
                                 final int aggregColumn) throws IOException {
        if (file1 == null) {
            throw new NullPointerException("file1");
        }
        if (file1Encoding == null) {
            throw new NullPointerException("file1Encoding");
        }
        if (aggregColumn < 0) {
            throw new IllegalArgumentException("aggregColumn < 0");
        }
        final Map<String,Integer> map1 = new HashMap<>();
        final Charset charset1 = Charset.forName(file1Encoding);
        try (BufferedReader reader1 = Files.newBufferedReader(
                                          new File(file1).toPath(), charset1)) {
            while (reader1.ready()) {
                final String line = reader1.readLine().trim();
                if (!line.isEmpty()) {
                    final String[] split = line.split(" *\\| *");
                    final String key = split[aggregColumn];
                    final Integer value = map1.get(key);
                    map1.put(key, (value == null) ? 1 : value + 1);
                }
            }
        }
        final TreeMap<Integer,TreeSet<String>> map2 = new TreeMap<>();
        for (Map.Entry<String,Integer> entry : map1.entrySet()) {
            final Integer value = entry.getValue();
            TreeSet<String> set1 = map2.get(value);
            if (set1 == null) {
                set1 = new TreeSet<>();
                map2.put(value, set1);
            }
            set1.add(entry.getKey());
        }

        for (Map.Entry<Integer,TreeSet<String>> entry :
                                              map2.descendingMap().entrySet()) {
            for (String key : entry.getValue()) {
                System.out.println(key + " : " + entry.getKey());
            }
        }
    }

    private static void usage() {
        System.err.println("usage: Aggregate <file> <fileEncoding> <aggregColumn>");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 3) {
            usage();
        }
        aggregate(args[0], args[1], Integer.parseInt(args[2]));
    }
}
