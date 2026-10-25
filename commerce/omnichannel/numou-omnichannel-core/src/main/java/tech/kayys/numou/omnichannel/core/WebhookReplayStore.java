package tech.kayys.numou.omnichannel.core;

/**
 * Atomic replay-token reservation port. Implementations return true only to
 * the sole claimant; concurrent reservations for the same scoped key must not
 * both succeed. Expired reservations may be replaced atomically.
 */
public interface WebhookReplayStore {
    boolean reserve(WebhookReplayReservation reservation);
}