package matching.schema;

import matching.indexing.ElementType;

import static matching.indexing.ElementType.NAME;
import static matching.indexing.ElementType.NONE;

public class DelayedFlight extends Schema {
    public DelayedFlight() {
        this.types = new ElementType[42];
        for (int typeCtr = 0; typeCtr <  types.length; typeCtr++) {
            this.types[typeCtr] = NONE;
        }
        this.types[1] = NAME;
        this.types[3] = NAME;
        this.types[5] = NAME;
        this.types[6] = NAME;
        this.types[8] = NAME;
        this.types[9] = NAME;
        this.types[12] = NAME;
        this.types[13] = NAME;
        this.types[15] = NAME;
        this.types[16] = NAME;
    }
}
