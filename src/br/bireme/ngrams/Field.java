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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Heitor Barbieri
 * date: 20150707
 */

class Field {    
    final String name;           // field's name
    final boolean optional;      // is field optional or required
    final boolean optionalMatch; // this field has to be equal to the indexed one
    final int requiredField;     // position of another field which is required by this field or -1 if not
    final int pos;               // position inside line A|B|C|D|...|H

    Field(final String name,
          final int pos) {
        this(name, pos, true, true, -1);
    }
    
    Field(final String name,
          final int pos,
          final boolean optional,
          final boolean optionalMatch,
          final int requiredField) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (pos < 0) {
            throw new IllegalArgumentException("pos < 0");
        }
        if (requiredField < -1) {
            throw new IllegalArgumentException("requiredField");
        }
        this.name = name;        
        this.optional = optional;
        this.optionalMatch = optionalMatch;
        this.requiredField = requiredField;
        this.pos = pos;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.pos;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Field other = (Field) obj;
        
        return this.pos == other.pos;
    }        
}

class IdField extends Field {
    IdField(final int pos) {
        super("id", pos, false, false, -1);
    }
}

class ExactField extends Field {
    ExactField(final String name,
               final int pos) {
        this(name, pos, true, true, -1);
    }
    ExactField(final String name,
               final int pos,
               final boolean optional,
               final boolean optionalMatch,
               final int requiredField) {
        super(name, pos, optional, optionalMatch, requiredField);
    }
}

class RegExpField extends Field {
    final Matcher matcher;
    final int groupNum;
    
    RegExpField(final String name,
                final int pos,
                final String regularExpression,
                final int groupNumber) {
        this(name, pos, true, true, -1, regularExpression,groupNumber);
    }
    RegExpField(final String name,
                final int pos,
                final boolean optional,
                final boolean optionalMatch,
                final int requiredField,
                final String pattern,
                final int groupNumber) {
        super(name, pos, optional, optionalMatch, requiredField);
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        if (groupNumber <= 0) {
            throw new IllegalArgumentException("groupNumber <= 0");
        }
        this.matcher = Pattern.compile(pattern).matcher("");
        this.groupNum = groupNumber;
    }
}

class IndexedNGramField extends Field {
    final float minScore;
    final Set<Integer> missingFields; // if missing field here then minimum
                                      // score will be 1.0 instead minScore.
                                              
    IndexedNGramField(final String name,
                      final int pos,
                      final float minScore,
                      final Set<Integer> missingFields) {
        super(name, pos, false, false, -1);
        if ((minScore < 0) || (minScore > 1)) {
            throw new IllegalArgumentException("minScore: " + minScore 
                                                                    + " [0,1]");
        }
        if (missingFields == null) {
            throw new NullPointerException("missingFields");
        }
        this.minScore = minScore;
        this.missingFields = missingFields;
    }
}

class NGramField extends Field {
    final float minScore;
    
    NGramField(final String name,
               final int pos,
               final boolean optional,
               final boolean optionalMatch,
               final float minScore) {
        this(name, pos, optional, optionalMatch, -1, minScore);
    }
    NGramField(final String name,
               final int pos,
               final boolean optional,
               final boolean optionalMatch,
               final int requiredField,
               final float minScore) {
        super(name, pos, optional, optionalMatch, requiredField);
        if ((minScore < 0) || (minScore > 1)) {
            throw new IllegalArgumentException("minScore: " + minScore 
                                                                    + " [0,1]");
        }
        this.minScore = minScore;
    }
}