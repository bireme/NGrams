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

/**
 *
 * @author Heitor Barbieri
 * date: 20150707
 */
class Score implements Comparable {
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

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Float.floatToIntBits(this.minValue);
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
        final Score other = (Score) obj;
        return (Float.floatToIntBits(this.minValue) != 
                                         Float.floatToIntBits(other.minValue));
    }        

    @Override
    public int compareTo(Object t) {
        if (t == null) {
            throw new NullPointerException();
        }
        final float res = (minValue - ((Score)t).minValue);
        
        return (res < 0) ? -1 : (res == 0) ? 0 : 1;
    }
}
