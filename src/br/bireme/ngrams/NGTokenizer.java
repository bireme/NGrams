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

import java.io.IOException;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 *
 * @author Heitor Barbieri
 * date: 20151216
 */
public class NGTokenizer extends Tokenizer {
    public static final int DEF_NG_SIZE = 3;

    private final int ngramSize;
    private final CharTermAttribute termAtt;
        
    public NGTokenizer(int ngramSize) {
        super();
        if (ngramSize < 1) {
            throw new IllegalArgumentException("ngramSize < 1");
        }
        this.ngramSize = ngramSize;        
        termAtt = addAttribute(CharTermAttribute.class);
        termAtt.resizeBuffer(ngramSize);        
    }

    public int getNgramSize() {
        return ngramSize;
    }
    
    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();
        termAtt.setEmpty();
        
        return getNextToken();
    }
    
    private boolean getNextToken() throws IOException {
        return getNextToken(0, false);     
    }
    
    private boolean getNextToken(final int pos,
                                 final boolean hasNotSpace) throws IOException {
        assert pos >= 0;
        
        final boolean ret;
        
        if (pos == ngramSize) {
            ret = hasNotSpace ? true : incrementToken();
        } else {
            final int ich = input.read();
            if (ich == -1) {
                termAtt.setEmpty();
                ret = false;
            } else {
                final char ch = (char)ich;
                termAtt.append(ch);
                ret = getNextToken(pos + 1, hasNotSpace || (ch != ' '));
            }
        }
        
        return ret;
    }    
}
