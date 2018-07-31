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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Heitor Barbieri
 * date: 20150707
 */

public class Field {
    public enum Status { REQUIRED,  // the field presence is required to check this field
                         MAX_SCORE  // if the field is missing then
                                    // minimum score will be 1.0
                                    // instead of minScore.
                       }

    public final String name;         // field's name
    public final Status contentMatch; // this field has to be equal to the indexed one if 'required', 'optional' if not and 'optional requiring max score' if not.
    public final String requiredField;// name of another field which is required by this field or null if not
    public final int pos;            // position inside line A|B|C|D|...|H (piped expression/file)

    Field(final String name,
          final int pos) {
        this(name, pos, Status.REQUIRED, null);
    }

    Field(final String name,
          final int pos,
          final Status contentMatch,
          final String requiredField) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (pos < 0) {
            throw new IllegalArgumentException("pos < 0");
        }
        this.name = name;
        this.contentMatch = (contentMatch == null) ? Status.REQUIRED
                                                   : contentMatch;
        this.requiredField = (requiredField == null) ? "" : requiredField;
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

        return (this.pos == other.pos);
    }
}

class IdField extends Field {
    final static String FNAME = "id";
    IdField(final int pos) {
        super(FNAME, pos, Status.REQUIRED, null);
    }
}

class DatabaseField extends Field {
    final static String FNAME = "database";
    DatabaseField(final int pos) {
        super(FNAME, pos, Status.REQUIRED, null);
    }
}

class NoCompareField extends Field {
    NoCompareField(final String name,
                   final int pos) {
        super(name, pos, Status.REQUIRED, null);
    }
}

class ExactField extends Field {
    ExactField(final String name,
               final int pos) {
        this(name, pos, Status.REQUIRED, null);
    }
    ExactField(final String name,
               final int pos,
               final Status contentMatch,
               final String requiredField) {
        super(name, pos, contentMatch, requiredField);
    }
}

class RegExpField extends Field {
    final Matcher matcher;
    final int groupNum;

    RegExpField(final String name,
                final int pos,
                final Status contentMatch,
                final String regularExpression,
                final int groupNumber) {
        this(name, pos, contentMatch, null, regularExpression, groupNumber);
    }
    RegExpField(final String name,
                final int pos,
                final Status contentMatch,
                final String requiredField,
                final String pattern,
                final int groupNumber) {
        super(name, pos, contentMatch, requiredField);
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
    IndexedNGramField(final String name,
                      final int pos) {
        super(name, pos, Status.REQUIRED, null);
    }
}

class NGramField extends Field {
    final float minScore;

    NGramField(final String name,
               final int pos,
               final Status contentMatch,
               final float minScore) {
        this(name, pos, contentMatch, null, minScore);
    }
    NGramField(final String name,
               final int pos,
               final Status contentMatch,
               final String requiredField,
               final float minScore) {
        super(name, pos, contentMatch, requiredField);
        if ((minScore < 0) || (minScore > 1)) {
            throw new IllegalArgumentException("minScore: " + minScore
                                                                    + " [0,1]");
        }
        this.minScore = minScore;
    }
}