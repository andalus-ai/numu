package tech.kayys.syirkah.restaurant.kitchen.domain;

/** KDS ordering weight: higher sorts first. */
public enum Priority {
    NORMAL(0), RUSH(1), VIP(2);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
