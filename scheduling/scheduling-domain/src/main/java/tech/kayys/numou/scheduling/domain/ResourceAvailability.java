package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.scheduling.domain.event.ResourceAvailabilityDeclared;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public final class ResourceAvailability extends AbstractAggregateRoot<ResourceAvailabilityId> {

    private final ResourceRef resource;
    private final LocalDate date;
    private final LocalTime fromTime;
    private final LocalTime toTime;
    private AvailabilityStatus status;

    private ResourceAvailability(ResourceAvailabilityId id, ResourceRef resource,
                                  LocalDate date, LocalTime fromTime, LocalTime toTime,
                                  AvailabilityStatus status) {
        super(id);
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.fromTime = Objects.requireNonNull(fromTime, "fromTime must not be null");
        this.toTime = Objects.requireNonNull(toTime, "toTime must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static ResourceAvailability declare(ResourceAvailabilityId id, ResourceRef resource,
                                               LocalDate date, LocalTime fromTime, LocalTime toTime,
                                               AvailabilityStatus status) {
        ResourceAvailability ra = new ResourceAvailability(id, resource, date, fromTime, toTime, status);
        ra.raise(new ResourceAvailabilityDeclared(id, resource, date, fromTime, toTime, status));
        return ra;
    }

    public ResourceRef getResource() { return resource; }
    public LocalDate getDate() { return date; }
    public LocalTime getFromTime() { return fromTime; }
    public LocalTime getToTime() { return toTime; }
    public AvailabilityStatus getStatus() { return status; }
    public boolean isAvailable() { return status == AvailabilityStatus.AVAILABLE; }
}
