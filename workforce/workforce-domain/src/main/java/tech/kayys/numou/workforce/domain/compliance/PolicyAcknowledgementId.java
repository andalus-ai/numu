package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity of a PolicyAcknowledgement aggregate.
 */
public record PolicyAcknowledgementId(UUID value) implements DomainId<UUID> {

    public PolicyAcknowledgementId {
        if (value == null) throw new IllegalArgumentException("PolicyAcknowledgementId value must not be null");
    }

    /** Factory method to generate a new random identity. */
    public static PolicyAcknowledgementId generate() {
        return new PolicyAcknowledgementId(UUID.randomUUID());
    }

    /** Reconstruct from an existing UUID string. */
    public static PolicyAcknowledgementId of(String uuid) {
        return new PolicyAcknowledgementId(UUID.fromString(uuid));
    }

    /** Reconstruct from an existing UUID. */
    public static PolicyAcknowledgementId of(UUID uuid) {
        return new PolicyAcknowledgementId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
