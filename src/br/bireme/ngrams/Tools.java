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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
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
                new File(indexName).toPath())) {
            final DirectoryReader ireader = DirectoryReader.open(directory);
            final Terms terms = SlowCompositeReaderWrapper.wrap(ireader)
                    .terms(fieldName);
            if (terms != null) {
                final TermsEnum tenum = terms.iterator();
                int pos = 0;

                while (true) {
                    final BytesRef br = tenum.next();
                    if (br == null) {
                        break;
                    }
                    System.out.println((++pos) + ") term =[" + br.utf8ToString() 
                                                                         + "]");
                }
            }
        }
    }
    
    public static void showTokens(final Analyzer analyzer,
                                  final String fieldName,
                                  final String text) throws IOException {
        TokenStream tokenStream = analyzer.tokenStream(fieldName, text);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            //int startOffset = offsetAttribute.startOffset();
            //int endOffset = offsetAttribute.endOffset();
            final String term = charTermAttribute.toString();
            
            System.out.println(term);
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
     * @return input string with every not digit-alphabetic charater removed,
     *         accents removed and all converted to lower case.
     */
    public static String normalize(final String in) {
        return (in == null) ? null  
                : Normalizer.normalize(in.toLowerCase(),Normalizer.Form.NFD)
                                                   .replaceAll("[^a-z0-9]", "");
    }
    
    public static float NGDistance(final String str1,
                                   final String str2) {
        return new NGramDistance(3).getDistance(str1, str2);
    }
    
    public static void main(final String[] args) throws IOException {
        final String iname = "teste";        
        
        //showTerms(iname, "titulo");
        
        final String str1 = limitSize(normalize(args[0]), 100);
        final String str2 = limitSize(normalize(args[1]), 100);
        System.out.println("dist=" + NGDistance(str1, str2));
    }    
}
