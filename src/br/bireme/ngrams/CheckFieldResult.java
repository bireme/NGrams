package br.bireme.ngrams;

/**
 *
 * @author Heitor Barbieri
 * date 20200615
 */
public class CheckFieldResult {
    public final String fieldName;
    public final String elem1;
    public final String elem2;
    public final Condition condition;
    public final float similarity;
   
    public CheckFieldResult(final String fieldName,
                            final String elem1,
                            final String elem2,
                            final Condition condition,
                            final float similarity) {
        assert fieldName != null;
        assert elem1 != null;
        assert elem2 != null;
        assert condition != null;
        
        this.fieldName = fieldName;
        this.elem1 = elem1;
        this.elem2 = elem2;
        this.condition = condition;
        this.similarity = similarity;
    }
}
