package tech.kayys.numou.feedback.application.service;

import tech.kayys.numou.feedback.application.port.PublicInvitationRateLimitRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/** One-minute, tenant-independent invitation-token limit backed by a shared repository. */
public final class PublicInvitationRateLimiter {
    private final PublicInvitationRateLimitRepository repository;
    private final int requestsPerMinute;

    public PublicInvitationRateLimiter(PublicInvitationRateLimitRepository repository, int requestsPerMinute) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        if (requestsPerMinute < 1 || requestsPerMinute > 10_000)
            throw new IllegalArgumentException("requestsPerMinute must be in 1..10000");
        this.requestsPerMinute = requestsPerMinute;
    }

    public boolean allow(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) return false;
        Objects.requireNonNull(now, "now cannot be null");
        Instant window = now.truncatedTo(ChronoUnit.MINUTES);
        return repository.tryConsume(sha256(rawToken), window, requestsPerMinute);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}