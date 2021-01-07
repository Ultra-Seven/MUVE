package matching.schema;

import matching.indexing.ElementType;

import static matching.indexing.ElementType.*;


public class DobJob extends Schema {
    public DobJob() {
        this.types = new ElementType[117];
        for (int typeCtr = 0; typeCtr <  types.length; typeCtr++) {
            this.types[typeCtr] = NAME;
        }
    }
}
