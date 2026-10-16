package tech.kayys.syirkah.construction.domain.site;

import tech.kayys.syirkah.construction.domain.site.event.ConstructionSiteClosed;
import tech.kayys.syirkah.construction.domain.site.event.ConstructionSiteCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionSite extends AbstractAggregateRoot<ConstructionSiteId> {
    private final UUID projectId;
    private final String siteCode;
    private String name;
    private SiteType type;
    private SiteStatus status;
    private SiteAddress address;
    private Double latitude;
    private Double longitude;
    private String timezone;

    private ConstructionSite(
            ConstructionSiteId id,
            UUID projectId,
            String siteCode,
            String name,
            SiteType type,
            SiteAddress address,
            Double latitude,
            Double longitude,
            String timezone
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.siteCode = Objects.requireNonNull(siteCode, "Site code cannot be null");
        this.name = Objects.requireNonNull(name, "Site name cannot be null");
        this.type = Objects.requireNonNull(type, "Site type cannot be null");
        this.address = Objects.requireNonNull(address, "Site address cannot be null");
        this.status = SiteStatus.PLANNED;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
    }

    public static ConstructionSite create(
            UUID projectId,
            String siteCode,
            String name,
            SiteType type,
            SiteAddress address,
            Double latitude,
            Double longitude,
            String timezone
    ) {
        var site = new ConstructionSite(
                ConstructionSiteId.generate(),
                projectId,
                siteCode,
                name,
                type,
                address,
                latitude,
                longitude,
                timezone
        );
        site.raise(new ConstructionSiteCreated(UUID.randomUUID(), Instant.now(), site.id().value(), projectId, siteCode));
        return site;
    }

    public void activate() {
        if (status != SiteStatus.PLANNED && status != SiteStatus.SUSPENDED) {
            throw new IllegalStateException("Site cannot be activated from " + status);
        }
        status = SiteStatus.ACTIVE;
    }

    public void suspend() {
        if (status != SiteStatus.ACTIVE) {
            throw new IllegalStateException("Only active sites can be suspended");
        }
        status = SiteStatus.SUSPENDED;
    }

    public void close() {
        if (status == SiteStatus.CLOSED) {
            throw new IllegalStateException("Site is already closed");
        }
        status = SiteStatus.CLOSED;
        raise(new ConstructionSiteClosed(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public UUID projectId() { return projectId; }
    public String siteCode() { return siteCode; }
    public String name() { return name; }
    public SiteType type() { return type; }
    public SiteStatus status() { return status; }
    public SiteAddress address() { return address; }
    public Double latitude() { return latitude; }
    public Double longitude() { return longitude; }
    public String timezone() { return timezone; }
}
