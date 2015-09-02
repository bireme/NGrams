/*=========================================================================

    Copyright © 2015 BIREME/PAHO/WHO

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

import static br.bireme.ngrams.NGrams.MAX_NG_TEXT_SIZE;
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
System.out.println("args.length=" + args.length);        
        if (args.length < 2) {
            usage();
        }
        final String str1;
        final String str2;
        
        if (args.length > 2) {
            if (args[2].equals("-normalize")) {
                str1 = Tools.limitSize(Tools.normalize(args[0]), 
                                                       MAX_NG_TEXT_SIZE).trim();
                str2 = Tools.limitSize(Tools.normalize(args[1]), 
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