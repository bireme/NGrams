/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;

/**
 *
 * @author Heitor Barbieri
 * date: 20150721
 */
public class NGAnalyzer extends Analyzer {
    public static final int DEF_NG_SIZE = 3;

    private final int ngramSize;
    private final boolean search;

    public NGAnalyzer() {
        this(DEF_NG_SIZE, false);
    }

    public NGAnalyzer(final boolean search) {
        this(DEF_NG_SIZE, search);
    }

    public NGAnalyzer(final int ngramSize,
                      final boolean search) {
        super();
        if (ngramSize < 1) {
            throw new IllegalArgumentException("ngramSize < 1");
        }
        this.ngramSize = ngramSize;
        this.search = search;
    }

    public int getNgramSize() {
        return ngramSize;
    }

    @Override
    protected Analyzer.TokenStreamComponents createComponents(String fieldName) {
        final Tokenizer source;

        source = search ? new NGTokenizer(ngramSize) // generate side by size ngrams
                        : new NGramTokenizer(ngramSize, ngramSize); // generate all ngrams

        // Não funciona - se duas strings diferem de apenas uma letra,
        // todos os tokens serão diferentes.
        //final Tokenizer source = new NGTokenizer(ngramSize);

        return new Analyzer.TokenStreamComponents(source);
    }
}
