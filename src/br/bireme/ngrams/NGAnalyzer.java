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
        final Tokenizer source = search
                        ? new NGTokenizer(ngramSize) // generate side by size ngrams
                        : new NGramTokenizer(ngramSize, ngramSize); // generate all ngrams
        
        // Não funciona - se duas strings diferem de apenas uma letra,
        // todos os tokens serão diferentes.        
        //final Tokenizer source = new NGTokenizer(ngramSize);

        return new Analyzer.TokenStreamComponents(source);
    }
}
