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
    public enum Status { REQUIRED,  // the field presence is required
                         OPTIONAL,  // the field can be missing
                         MAX_SCORE  // if the field is missing then
                                    // minimum score will be 1.0
                                    // instead of minScore.
                       }

    final String name;         // field's name
    final Status presence;     // tells if the field presence is 'required', 'optional' or 'optional requiring max score'
    final Set<String> content; // contents that require max score if this field is/is_not equals to
    final Status contentMatch; // this field has to be equal to the indexed one
    final String requiredField;// name of another field which is required by this field or null if not
    final int ipos;            // position inside line A|B|C|D|...|H (index piped expression/file)
    final int spos;            // position inside line A|B|C|D|...|H (search piped expression/file)

    Field(final String name,
          final int ipos,
          final int spos) {
        this(name, ipos, spos, Status.OPTIONAL, null, Status.OPTIONAL, null);
    }

    Field(final String name,
          final int ipos,
          final int spos,
          final Status presence,
          final Set<String> content,
          final Status contentMatch,
          final String requiredField) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (ipos < 0) {
            throw new IllegalArgumentException("pos < 0");
        }
        if (spos < 0) {
            throw new IllegalArgumentException("pos < 0");
        }
        this.name = name;
        this.presence = (presence == null) ? Status.OPTIONAL : presence;
        this.content = content;
        this.contentMatch = (contentMatch == null) ? Status.OPTIONAL
                                                   : contentMatch;
        this.requiredField = requiredField;
        this.ipos = ipos;
        this.spos = spos;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.ipos + this.spos;
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

        return ((this.ipos == other.ipos) && (this.spos == other.spos));
    }
}

class IdField extends Field {
    final static String FNAME = "id";
    IdField(final int ipos,
            final int spos) {
        super(FNAME, ipos, spos, Status.REQUIRED, null, Status.OPTIONAL, null);
    }
}

class DatabaseField extends Field {
    final static String FNAME = "database";
    DatabaseField(final int ipos,
                  final int spos) {
        super(FNAME, ipos, spos, Field.Status.REQUIRED, null, 
                                                   Field.Status.OPTIONAL, null);
    }
}

class NoCompareField extends Field {
    NoCompareField(final String name,
                   final int ipos,
                   final int spos) {
        super(name, ipos, spos, Status.OPTIONAL, null, Status.OPTIONAL, null);
    }
}

class ExactField extends Field {
    ExactField(final String name,
               final Set<String> content,
               final int ipos,
               final int spos) {
        this(name, ipos, spos, Status.OPTIONAL, content, Status.OPTIONAL, null);
    }
    ExactField(final String name,
               final int ipos,
               final int spos, 
               final Status presence,
               final Set<String> content,
               final Status contentMatch,
               final String requiredField) {
        super(name, ipos, spos, presence, content, contentMatch, requiredField);
    }
}

class RegExpField extends Field {
    final Matcher matcher;
    final int groupNum;

    RegExpField(final String name,
                final int ipos,
                final int spos,
                final Set<String> content,
                final Status contentMatch,
                final String regularExpression,
                final int groupNumber) {
        this(name, ipos, spos, Status.OPTIONAL, content, contentMatch, null,
                                                regularExpression, groupNumber);
    }
    RegExpField(final String name,
                final int ipos,
                final int spos,
                final Status presence,
                final Set<String> content,
                final Status contentMatch,
                final String requiredField,
                final String pattern,
                final int groupNumber) {
        super(name, ipos, spos, presence, content, contentMatch, requiredField);
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

    IndexedNGramField(final String name,
                      final int ipos,
                      final int spos,
                      final float minScore) {
        super(name, ipos, spos, Status.REQUIRED, null, Status.OPTIONAL, null);
        if ((minScore < 0) || (minScore > 1)) {
            throw new IllegalArgumentException("minScore: " + minScore
                                                                    + " [0,1]");
        }
        this.minScore = minScore;
    }
}

class NGramField extends Field {
    final float minScore;

    NGramField(final String name,
               final int ipos,
               final int spos,
               final Status presence,
               final Set<String> content,
               final Status contentMatch,
               final float minScore) {
        this(name, ipos, spos, presence, content, contentMatch, null, minScore);
    }
    NGramField(final String name,
               final int ipos,
               final int spos,
               final Status presence,
               final Set<String> content,
               final Status contentMatch,
               final String requiredField,
               final float minScore) {
        super(name, ipos, spos, presence, content, contentMatch, requiredField);
        if ((minScore < 0) || (minScore > 1)) {
            throw new IllegalArgumentException("minScore: " + minScore
                                                                    + " [0,1]");
        }
        this.minScore = minScore;
    }
}