/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

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
    private final char[] buffer;

    public NGTokenizer(int ngramSize) {
        super();
        if (ngramSize < 1) {
            throw new IllegalArgumentException("ngramSize < 1");
        }
        this.ngramSize = ngramSize;
        termAtt = addAttribute(CharTermAttribute.class);
        termAtt.resizeBuffer(ngramSize);
        buffer = new char[ngramSize];
        for (int idx = 0; idx < ngramSize; idx++) {
            buffer[idx] = ' ';
        }
    }

    public int getNgramSize() {
        return ngramSize;
    }

    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();

        return getNextToken();
    }

    private boolean getNextToken() throws IOException {
        termAtt.setEmpty();
        return getNextToken(0);
    }


    private boolean getNextToken(final int pos) throws IOException {
        assert pos >= 0;

        final boolean ret;

        if (pos == ngramSize) {
            ret = true;
        } else {
            final int ich = input.read();
            if (ich == -1) {
                termAtt.setEmpty();
                ret = false;
            } else {
                final char ch = (char)ich;
                if (ch == ' ') {
                    ret = getNextToken();
                } else {
                    termAtt.append(ch);
                    ret = getNextToken(pos + 1);
                }
            }
        }

        return ret;
    }

    /*
    private boolean getNextToken() throws IOException {
        termAtt.setEmpty();
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
    */
}
