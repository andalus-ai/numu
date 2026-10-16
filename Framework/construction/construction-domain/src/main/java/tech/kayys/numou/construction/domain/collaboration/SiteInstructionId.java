package tech.kayys.syirkah.construction.domain.collaboration;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record SiteInstructionId(UUID value) implements DomainId<UUID> {
    public SiteInstructionId { Objects.requireNonNull(value); }
    public static SiteInstructionId generate() { return new SiteInstructionId(UUID.randomUUID()); }
    public static SiteInstructionId of(UUID value) { return new SiteInstructionId(value); }
}
