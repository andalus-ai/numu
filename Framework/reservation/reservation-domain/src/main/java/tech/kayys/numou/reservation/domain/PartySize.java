package tech.kayys.numou.reservation.domain;

/**
 * Party size or capacity demand (number of guests, patients, seats, passengers).
 */
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
