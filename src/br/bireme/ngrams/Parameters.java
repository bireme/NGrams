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

import java.util.Set;

/**
 *
 * @author Heitor Barbieri
 * date: 20150720
 */
class Parameters {    
    final IdField id;
    final IndexedNGramField indexed;
    final Set<ExactField> exacts;
    final Set<NGramField> ngrams;
    final Set<Score> scores;
    final Field[] fields;
    final int nfields;
    
    Parameters(final IdField id,
               final IndexedNGramField indexed,
               final Set<ExactField> exacts,
               final Set<NGramField> ngrams,
               final Set<Score> scores) {
        assert id != null;
        assert indexed != null;
        assert exacts != null;
        assert ngrams != null;
        assert scores != null;
        
        this.id = id;
        this.indexed = indexed;
        this.exacts = exacts;
        this.ngrams = ngrams;
        this.scores = scores;        
        this.nfields = 2 + exacts.size() + ngrams.size();
        
        fields = new Field[nfields];
        
        if (id.pos >= nfields) {
            throw new IllegalArgumentException("id pos >= " + nfields);
        }
        fields[id.pos] = id;
        if (indexed.pos >= nfields) {
            throw new IllegalArgumentException("indexed pos >= " + nfields);
        }
        fields[indexed.pos] = indexed;
        for (ExactField exact : exacts) {
            if (exact.pos >= nfields) {
                throw new IllegalArgumentException("exact pos >= " + nfields);
            }
            fields[exact.pos] = exact;
        }
        for (NGramField ngram : ngrams) {
            if (ngram.pos >= nfields) {
                throw new IllegalArgumentException("ngram pos >= " + nfields);
            }
            fields[ngram.pos] = ngram;
        }
    }
}
