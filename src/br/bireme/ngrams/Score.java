/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

/**
 *
 * @author Heitor Barbieri
 * date: 20150707
 */
public class Score implements Comparable<Score> {
    /**
     * Minimum score value [0,1]
     */
    final float minValue;
    /**
     * Minimum required fields for these score
     */
    final int minFields;

    Score(final float minValue,
          final int minFields) {
        if ((minValue < 0) || (minValue > 1)) {
            throw new IllegalArgumentException("minValue: " + minValue
                                                                    + " [0,1]");
        }
        if (minFields < 0) {
            throw new IllegalArgumentException("minFields < 0");
        }
        this.minValue = minValue;
        this.minFields = minFields;
    }

    public float getMinValue() {
        return minValue;
    }

    public int getMinFields() {
        return minFields;
    }

    @Override
    public int compareTo(final Score t) {
        if (t == null) {
            throw new NullPointerException();
        }
        final float res = (minValue - t.minValue);

        return (res < 0) ? -1 : (res == 0) ? 0 : 1;
    }
}
