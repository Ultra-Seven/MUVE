package matching.indexing;



import java.util.function.Function;

public enum ElementType {
    NAME,
    TEXT,
    ADDRESS,
    EMAIL,
    PHONE,
    NUMBER,
    DATE,
    AGE,
    NONE;

    private ElementType() {
    }

    public Function<String, String> getPreProcessFunction() {
        switch(this) {
            case NAME:
                return Preprocessing.namePreprocessing();
            case TEXT:
                return Preprocessing.removeSpecialChars();
            case ADDRESS:
                return Preprocessing.addressPreprocessing();
            case EMAIL:
                return Preprocessing.removeDomain();
            case PHONE:
                return Preprocessing.usPhoneNormalization();
            case NUMBER:
            case AGE:
                return Preprocessing.numberPreprocessing();
            default:
                return Preprocessing.none();
        }
    }

//    protected Function getTokenizerFunction() {
//        switch(this) {
//            case NAME:
//                return TokenizerFunction.wordSoundexEncodeTokenizer();
//            case TEXT:
//                return TokenizerFunction.wordTokenizer();
//            case ADDRESS:
//                return TokenizerFunction.wordSoundexEncodeTokenizer();
//            case EMAIL:
//                return TokenizerFunction.triGramTokenizer();
//            case PHONE:
//                return TokenizerFunction.decaGramTokenizer();
//            default:
//                return TokenizerFunction.valueTokenizer();
//        }
//    }
//
//    protected MatchType getMatchType() {
//        switch(this) {
//            case NUMBER:
//            case AGE:
//            case DATE:
//                return MatchType.NEAREST_NEIGHBORS;
//            default:
//                return MatchType.EQUALITY;
//        }
//    }
}