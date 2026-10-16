package tech.kayys.syirkah.construction.domain.hse;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record SafetyProfileId(UUID value) implements DomainId<UUID> {
    public SafetyProfileId { Objects.requireNonNull(value); }
    public static SafetyProfileId generate() { return new SafetyProfileId(UUID.randomUUID()); }
    public static SafetyProfileId of(UUID value) { return new SafetyProfileId(value); }
}
