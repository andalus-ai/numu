package tech.kayys.syirkah.identity.domain.membership;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record MembershipId(UUID value) implements DomainId<UUID> {
    public MembershipId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static MembershipId generate() {
        return new MembershipId(UUID.randomUUID());
    }
}
