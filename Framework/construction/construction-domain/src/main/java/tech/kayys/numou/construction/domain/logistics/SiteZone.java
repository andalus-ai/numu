package tech.kayys.syirkah.construction.domain.logistics;

import tech.kayys.syirkah.construction.domain.logistics.event.SiteZoneCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SiteZone extends AbstractAggregateRoot<SiteZoneId> {
    private final UUID siteId;
    private final String zoneCode;
    private String name;
    private SiteZoneType type;
    private boolean active;

    private SiteZone(SiteZoneId id, UUID siteId, String zoneCode, String name, SiteZoneType type) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.zoneCode = Objects.requireNonNull(zoneCode);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.active = true;
    }

    public static SiteZone create(UUID siteId, String zoneCode, String name, SiteZoneType type) {
        var zone = new SiteZone(SiteZoneId.generate(), siteId, zoneCode, name, type);
        zone.raise(new SiteZoneCreated(UUID.randomUUID(), Instant.now(), zone.id().value(), siteId, zoneCode, type));
        return zone;
    }

    public UUID siteId() { return siteId; }
    public String zoneCode() { return zoneCode; }
    public String name() { return name; }
    public SiteZoneType type() { return type; }
    public boolean isActive() { return active; }
}
