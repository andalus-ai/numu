package tech.kayys.numou.crm.domain.event.referral;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a referral is converted to a lead or opportunity.
 */
public record ReferralConverted(
        UUID eventId,
        Instant occurredAt,
        UUID referralId,
        UUID referrerAccountId,
        String target,
        String type,
        String code,
        UUID leadId,
        UUID opportunityId,
        long timestampMillis) implements DomainEvent {

    public ReferralConverted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(referralId, "referralId cannot be null");
        Objects.requireNonNull(referrerAccountId, "referrerAccountId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
    }

    public static ReferralConverted of(UUID referralId,
                                       UUID referrerAccountId,
                                       String target,
                                       String type,
                                       String code,
                                       UUID leadId,
                                       UUID opportunityId,
                                       long timestampMillis) {
        return new ReferralConverted(
                UUID.randomUUID(),
                Instant.now(),
                referralId,
                referrerAccountId,
                target,
                type,
                code,
                leadId,
                opportunityId,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.referral.converted";
    }
}