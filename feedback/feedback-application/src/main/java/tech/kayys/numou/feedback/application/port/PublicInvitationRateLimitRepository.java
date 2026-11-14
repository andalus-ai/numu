package tech.kayys.numou.feedback.application.port;

import java.time.Instant;

/** Atomic, shared-store rate limit bucket for public invitation endpoints. */
@FunctionalInterface
public interface PublicInvitationRateLimitRepository {
    /**
     * Atomically consumes one request from the token bucket for the supplied UTC window.
     * @return true when the request is admitted; false when the configured limit is reached
     */
    boolean tryConsume(String tokenHash, Instant windowStart, int limit);
}