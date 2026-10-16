package tech.kayys.syirkah.ecosystem.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies a participant in the Numou ecosystem.
 *
 * <p>A participant is an entity that takes part in the ecosystem - it may
 * be a tenant's own operation, a Numou-hosted capability, or an external
 * provider. See {@code Docs/Plan/base00.md} §3.
 */
public record ParticipantId(UUID value) implements DomainId<UUID>, Serializable {

    public ParticipantId {
        Objects.requireNonNull(value, "ParticipantId value cannot be null");
    }

    public static ParticipantId of(UUID value) {
        return new ParticipantId(value);
    }

    public static ParticipantId generate() {
        return new ParticipantId(UUID.randomUUID());
    }

    public static ParticipantId fromString(String value) {
        return new ParticipantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ParticipantId{" + value + "}";
    }
}
