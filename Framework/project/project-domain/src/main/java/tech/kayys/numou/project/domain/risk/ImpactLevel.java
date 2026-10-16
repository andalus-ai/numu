package tech.kayys.syirkah.project.domain.risk;

public enum ImpactLevel {

    NEGLIGIBLE(1),

    MINOR(2),

    MODERATE(3),

    MAJOR(4),

    CRITICAL(5);

    private final int score;

    ImpactLevel(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }
}
