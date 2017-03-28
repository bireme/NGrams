/*=========================================================================

    Copyright © 2017 BIREME/PAHO/WHO

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

import java.io.IOException;
import java.nio.CharBuffer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Tokenizer that generates only one token, the whole input.
 * @author Heitor Barbieri
 * date: 20170327
 */
public class OneTokenTokenizer extends Tokenizer {
    private final CharBuffer buffer;
    private final CharTermAttribute termAtt;
    
    public OneTokenTokenizer() {
        buffer = CharBuffer.allocate(1024);
        termAtt = addAttribute(CharTermAttribute.class);
        termAtt.resizeBuffer(1024);        
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        
        termAtt.setEmpty();
        while (input.read(buffer) != -1);        
        termAtt.append(buffer.toString().trim());
       
        return false;
    }
}