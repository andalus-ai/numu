package tech.kayys.syirkah.project.domain.risk;

/**
 * Controlled five-step probability scale instead of arbitrary decimal
 * percentages — comparable across the whole register.
 */
public enum Probability {

    VERY_LOW(1),

    LOW(2),

    MEDIUM(3),

    HIGH(4),

    VERY_HIGH(5);

    private final int score;

    Probability(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }
}
