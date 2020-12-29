package matching.schema;

import matching.indexing.ElementType;

import static matching.indexing.ElementType.*;
import static matching.indexing.ElementType.NUMBER;

public class SampleAu extends Schema{
    public SampleAu() {
        this.types = new ElementType[]{
                NUMBER, NAME, DATE, DATE, DATE, NAME, NAME, NAME, EMAIL,
                NAME, NAME, NAME, NAME, NAME, NAME, EMAIL, NAME, PHONE,
                EMAIL, PHONE, PHONE, PHONE, PHONE, PHONE, ADDRESS, ADDRESS,
                ADDRESS, NAME, NAME, NUMBER, NAME, NAME, NUMBER, NUMBER,
                NAME, NAME, EMAIL, NAME, NAME, NAME, NUMBER, NAME, NAME, NAME, NAME
        };
    }
}
