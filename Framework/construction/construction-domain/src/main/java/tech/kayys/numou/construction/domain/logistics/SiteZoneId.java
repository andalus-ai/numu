package tech.kayys.syirkah.construction.domain.logistics;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record SiteZoneId(UUID value) implements DomainId<UUID> {
    public SiteZoneId { Objects.requireNonNull(value); }
    public static SiteZoneId generate() { return new SiteZoneId(UUID.randomUUID()); }
    public static SiteZoneId of(UUID value) { return new SiteZoneId(value); }
}
