package tech.kayys.numou.survey.domain.logic;

public enum CompareOp {
    EQ, NE, GT, GTE, LT, LTE;

    public boolean apply(double left, double right) {
        return switch (this) {
            case EQ -> left == right;
            case NE -> left != right;
            case GT -> left > right;
            case GTE -> left >= right;
            case LT -> left < right;
            case LTE -> left <= right;
        };
    }
}
