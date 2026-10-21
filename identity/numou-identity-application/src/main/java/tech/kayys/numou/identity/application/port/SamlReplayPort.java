package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;

import java.time.Instant;

/**
 * Replay-protection store for SAML assertion IDs.
 *
 * <p>An assertion ID is "consumed" until {@code expiresAt} (typically the assertion's
 * {@code NotOnOrAfter} window).  Implementations <em>must</em> be durable and shared
 * across all service instances (Postgres, Redis, …) so that a restart or horizontal
 * scale-out cannot replay an already-consumed assertion.
 *
 * <p>The check-and-set operation must be atomic: {@link #tryConsume} should only
 * return {@code true} for the <em>first</em> caller that presents a given {@code assertionId}.
 */
public interface SamlReplayPort {

    /**
     * Atomically marks {@code assertionId} as consumed until {@code expiresAt}.
     *
     * @return {@code true} if this is the first consumption (caller may proceed);
     *         {@code false} if it was already consumed (replay — caller must reject).
     */
    Uni<Boolean> tryConsume(String assertionId, Instant expiresAt);
}
