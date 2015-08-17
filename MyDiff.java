import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class MyDiff {
    private static void usage() {
        System.err.println("usage: MyDiff <file1> <charset1> <file2> <charset2>");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 4) {
            usage();
        }
        final Charset charset1 = Charset.forName(args[1]);
        final Charset charset2 = Charset.forName(args[3]);
        try (BufferedReader reader1 = Files.newBufferedReader(
                                          new File(args[0]).toPath(), charset1);
             BufferedReader reader2 = Files.newBufferedReader(
                                          new File(args[2]).toPath(), charset2)) {                     
            
            final Set<String> set1 = new HashSet<>();
            final Set<String> set2 = new HashSet<>();
            while (reader1.ready()) {
                final String line = reader1.readLine().trim();
                if (!line.isEmpty()) {
                    set1.add(line);
                }
            }
            while (reader2.ready()) {
                final String line = reader2.readLine().trim();
                if (!line.isEmpty()) {
                    set2.add(line);
                }
            }
            for (String e2: set2) {
                if (set1.contains(e2)) {
                      System.out.println("<>" + e2);
                      set1.remove(e2);
                      set2.remove(e2); 
                }
            }
            for (String e1: set1) { 
                System.out.println("<" + e1);
            }
            for (String e2: set2) {
                System.out.println(">" + e2);
            }
        }
    }
} 
