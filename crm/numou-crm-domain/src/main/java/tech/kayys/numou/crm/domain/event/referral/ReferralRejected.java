package tech.kayys.numou.crm.domain.event.referral;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a referral is rejected.
 */
public record ReferralRejected(
        UUID eventId,
        Instant occurredAt,
        UUID referralId,
        UUID referrerAccountId,
        String target,
        String type,
        String code,
        long timestampMillis) implements DomainEvent {

    public ReferralRejected {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(referralId, "referralId cannot be null");
        Objects.requireNonNull(referrerAccountId, "referrerAccountId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
    }

    public static ReferralRejected of(UUID referralId,
                                      UUID referrerAccountId,
                                      String target,
                                      String type,
                                      String code,
                                      long timestampMillis) {
        return new ReferralRejected(
                UUID.randomUUID(),
                Instant.now(),
                referralId,
                referrerAccountId,
                target,
                type,
                code,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.referral.rejected";
    }
}