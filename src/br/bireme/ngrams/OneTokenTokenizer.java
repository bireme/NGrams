/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

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
