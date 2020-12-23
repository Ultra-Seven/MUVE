package matching.schema;


import matching.indexing.ElementType;

import static matching.indexing.ElementType.*;

public class Sample311 extends Schema {
    public Sample311() {
        this.types = new ElementType[]{
                NUMBER, DATE, DATE, NAME, NAME, NAME, NAME, NAME, NUMBER,
                ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                NAME, NAME, ADDRESS, NAME, NAME, DATE, TEXT, DATE,
                ADDRESS, NUMBER, NAME, NUMBER, NUMBER, NAME, NAME, NAME,
                NAME, NAME, ADDRESS, NAME, NAME, NAME, ADDRESS, NUMBER, NUMBER, NUMBER
        };
    }
}
