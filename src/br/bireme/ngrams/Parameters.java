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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Heitor Barbieri
 * date: 20150720
 */
public class Parameters {
    final TreeSet<Score> scores;
    final DatabaseField db;
    final IdField id;
    final IndexedNGramField indexed;
    final Set<ExactField> exacts;
    final Set<NGramField> ngrams;
    final Set<RegExpField> regexps;
    final Set<NoCompareField> nocompare;
    final Map<Integer,Field> sfields;  // search (pos,field)
    final Map<String,Field> nameFields;  // field name (name,field)
    final int maxIdxFieldPos; // last position into piped expression (index process)

    Parameters(final TreeSet<Score> scores,
               final DatabaseField db,
               final IdField id,
               final IndexedNGramField indexed,
               final Set<ExactField> exacts,
               final Set<NGramField> ngrams,
               final Set<RegExpField> regexps,
               final Set<NoCompareField> nocompare) {
        assert scores != null;
        assert db != null;
        assert id != null;
        assert indexed != null;
        assert exacts != null;
        assert ngrams != null;
        assert regexps != null;
        assert nocompare != null;

        this.scores = scores;
        this.db = db;
        this.id = id;
        this.indexed = indexed;
        this.exacts = exacts;
        this.ngrams = ngrams;
        this.regexps = regexps;
        this.nocompare = nocompare;
        
        // number of fields
        final int nfields = 3 + exacts.size() + ngrams.size() + regexps.size()
                                                             + nocompare.size();        
        int maxPos = 0;

        sfields = new TreeMap<>();
        this.nameFields = new TreeMap<>();
        
        maxPos = addField(db, nfields, maxPos);
        maxPos = addField(id, nfields, maxPos);
        maxPos = addField(indexed, nfields, maxPos);                        
        
        for (ExactField exact : exacts) {
            maxPos = addField(exact, nfields, maxPos);
        }        
        for (NGramField ngram : ngrams) {
            maxPos = addField(ngram, nfields, maxPos);
        }        
        for (RegExpField regexp : regexps) {
            maxPos = addField(regexp, nfields, maxPos);
        }        
        for (NoCompareField nocomp : nocompare) {
            maxPos = addField(nocomp, nfields, maxPos);
        }                
        this.maxIdxFieldPos = maxPos;
        
        checkFields(nfields);
    }
    
    public Map<String,Field> getNameFields() {
        return nameFields;
    }
    
    private int addField(final Field fld,
                         final int nfields,
                         final int maxPosition) {
        assert fld != null;
        assert nfields > 0;
        assert maxPosition >= 0;
        
        if ((fld.pos >= nfields) || (sfields.containsKey(fld.pos))) {
            throw new IllegalArgumentException(fld.name + " spos[" 
                         + fld.pos + "] it out of range or already used");
        }            
        sfields.put(fld.pos, fld);                
        nameFields.put(fld.name, fld);
                
        return Integer.max(maxPosition, fld.pos);
    }
    
    private void checkFields(final int nfields) {
        for (int idx = 0; idx < nfields; idx++) {
            if (sfields.get(idx) == null) {
                throw new IllegalArgumentException("missing spos=" + idx);
            }
        }
        for (Field fld : nameFields.values()) {
            final String reqField = fld.requiredField;
            if ((reqField != null) && (!reqField.isEmpty())) {
                final Field otherField = nameFields.get(reqField);
                                          
                if (otherField == null) {
                    throw new IllegalArgumentException("invalid requiredField = [" 
                                                              + reqField + "]");
                }
                // To avoid cyclic graph with requiredField, it is required that
                // each requiredField point to a field which pos is lower than
                // the current one.
                if (otherField.pos >= fld.pos) {
                    throw new IllegalArgumentException("required Field(" + 
                        otherField.name +") pos ["+ otherField.pos + 
                        "] >= current field(" + fld.name + ") pos [" + 
                        fld.pos + "]");
                }                    
            }                        
        }
    }
}
