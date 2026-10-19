package tech.kayys.numou.workforce.domain.performance;

public enum CompetencyRating {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    private final int value;

    CompetencyRating(int value) {
        this.value = value;
    }

    public int numericValue() {
        return value;
    }
}
