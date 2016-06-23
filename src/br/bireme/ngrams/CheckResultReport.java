/*=========================================================================

    Copyright © 2016 BIREME/PAHO/WHO

    This file is part of NGrams.

    NGrams is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    NGrams is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with NGrams. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/
package br.bireme.ngrams;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Create a report from similarity output report that helps to find lowest value
 * of similarity that still works well.
 * @author Heitor Barbieri
 * @date 20160621
 */

class Elem {
    final String sym;
    final String id1;
    final String id2;
    final String fld1;
    final String fld2;

    public Elem(final String sym, 
                final String id1, 
                final String id2, 
                final String fld1, 
                final String fld2) {
        this.sym = sym;
        this.id1 = id1;
        this.id2 = id2;
        this.fld1 = fld1;
        this.fld2 = fld2;
    }               
}

public class CheckResultReport {
    
    
    private static void usage() {
        System.err.println(
              "usage: CheckResultReport <standardReportFile> <checkReportFile>" +
              " [<encoding>]");
        System.exit(1);
    }
    
    public static void main(final String[] args) throws IOException {
        if (args.length < 2) usage();
        
        final String encoding = (args.length > 2) ? args[2] : "UTF-8";
        
        CheckResultReport.createReport(args[0], args[1], encoding);
    }    
    
    private static void createReport(final String standardReportFile,
                                     final String checkReportFile,
                                     final String encoding) throws IOException {
        assert standardReportFile != null;
        assert checkReportFile != null;
        assert encoding != null;
        
        final Charset charset = Charset.forName(encoding);
        final TreeMap<String,List<Elem>> map = genMap(standardReportFile, charset);
        
        try (BufferedWriter writer = Files.newBufferedWriter(
                                       new File(checkReportFile).toPath())) {
            for(List<Elem> lelem: map.values()) {
                for(Elem elem: lelem) {
                    writer.write("similarity: ");
                    writer.write(elem.sym);
                    writer.write("\nstext: ");
                    writer.write(elem.fld1);
                    writer.write("\nitext: ");
                    writer.write(elem.fld2);
                    writer.write("\nsid: ");
                    writer.write(elem.id1);
                    writer.write(" iid: ");
                    writer.write(elem.id2);
                    writer.write("\n\n");
                }
            }
        }               
    }
    
    private static TreeMap<String,List<Elem>> genMap(
                                               final String standardReportFile,
                                               final Charset encoding) 
                                                            throws IOException {
        final TreeMap<String,List<Elem>> map = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(
                             new File(standardReportFile).toPath(), encoding)) {
            reader.readLine();
            reader.readLine();
            
            while(true) {
                final String line = reader.readLine();
                if (line == null) break;
                
                final String tline = line.trim();
                if (!tline.isEmpty()) {
                    final String[] split = tline.split(" *\\| *", 8);
                    final String sym = split[1];
                    final Elem elem = new Elem(sym, split[2], split[3],
                            split[4], split[5]);
                    List<Elem> lelem = map.get(sym);
                    if (lelem == null) {
                        lelem = new ArrayList<>();
                        map.put(sym, lelem);
                    }
                    lelem.add(elem);
                }
            }
        }
        
        return map;
    }
}
