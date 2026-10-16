package tech.kayys.syirkah.construction.domain.warranty;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record WarrantyId(UUID value) implements DomainId<UUID> {
    public WarrantyId { Objects.requireNonNull(value); }
    public static WarrantyId generate() { return new WarrantyId(UUID.randomUUID()); }
    public static WarrantyId of(UUID value) { return new WarrantyId(value); }
}
