/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

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
    final AuthorsField authors;
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
               final AuthorsField authors,
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
        this.authors = authors;
        this.indexed = indexed;
        this.exacts = exacts;
        this.ngrams = ngrams;
        this.regexps = regexps;
        this.nocompare = nocompare;

        // number of fields
        final int nfields = 3 + ((authors == null) ? 0 : 1)  + exacts.size() +
                   ngrams.size() + regexps.size() + nocompare.size();
//System.out.println("3+" + exacts.size() + "+" +  ngrams.size() + "+" + regexps.size()
//                                                      + "+" + nocompare.size());
        int maxPos = 0;

        sfields = new TreeMap<>();
        this.nameFields = new TreeMap<>();

        maxPos = addField(db, nfields, maxPos);
        maxPos = addField(id, nfields, maxPos);
        if (authors != null) {
            maxPos = addField(authors, nfields, maxPos);
        }
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
        if (nfields == 3) {
            throw new IllegalArgumentException("Empty check list of fields");
        }
        this.maxIdxFieldPos = maxPos;

        checkFields(nfields);
    }

    public TreeSet<Score> getScores() {
        return scores;
    }

    public Map<String,Field> getNameFields() {
        return nameFields;
    }

    public Map<Integer,Field> getSearchFields() {
        return sfields;
    }

    public float getMinSimilarity() {
        return scores.first().minValue;
    }

    public int getIndexedPos() {
        return indexed.pos;
    }

    private int addField(final Field fld,
                         final int nfields,
                         final int maxPosition) {
        assert fld != null;
        assert nfields > 0;
        assert maxPosition >= 0;

        if (fld.pos >= nfields) {
            throw new IllegalArgumentException(fld.name + " field pos["
                         + fld.pos + "] >=" + nfields);
        }
        if (sfields.containsKey(fld.pos)) {
            throw new IllegalArgumentException(fld.name + "  field pos["
                         + fld.pos + "] is already used");
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
