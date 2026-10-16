package tech.kayys.syirkah.construction.domain.hse;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record PermitToWorkId(UUID value) implements DomainId<UUID> {
    public PermitToWorkId { Objects.requireNonNull(value); }
    public static PermitToWorkId generate() { return new PermitToWorkId(UUID.randomUUID()); }
    public static PermitToWorkId of(UUID value) { return new PermitToWorkId(value); }
}
