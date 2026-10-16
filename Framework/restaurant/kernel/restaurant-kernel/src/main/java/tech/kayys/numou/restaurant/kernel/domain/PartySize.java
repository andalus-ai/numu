package tech.kayys.numou.restaurant.kernel.domain;

/** Number of guests (covers). Upper limits are venue policy, not a domain constant. */
public record PartySize(int value) {

    public PartySize {
        if (value < 1) {
            throw new IllegalArgumentException("Party size must be at least 1");
        }
    }

    public static PartySize of(int value) {
        return new PartySize(value);
    }
}
