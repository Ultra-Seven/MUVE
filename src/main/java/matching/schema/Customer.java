package matching.schema;

import matching.indexing.ElementType;

import static matching.indexing.ElementType.NAME;
import static matching.indexing.ElementType.NUMBER;


public class Customer extends Schema {
    public Customer() {
        this.types = new ElementType[2];
        this.types[0] = NAME;
        this.types[1] = NUMBER;
    }
}
