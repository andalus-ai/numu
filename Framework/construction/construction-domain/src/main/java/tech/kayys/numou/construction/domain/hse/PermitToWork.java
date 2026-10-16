package tech.kayys.syirkah.construction.domain.hse;

import tech.kayys.syirkah.construction.domain.hse.event.PermitToWorkIssued;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PermitToWork extends AbstractAggregateRoot<PermitToWorkId> {
    private final UUID siteId;
    private final PermitType type;
    private final String description;
    private PermitStatus status;

    private PermitToWork(PermitToWorkId id, UUID siteId, PermitType type, String description) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
        this.status = PermitStatus.REQUESTED;
    }

    public static PermitToWork request(UUID siteId, PermitType type, String description) {
        return new PermitToWork(PermitToWorkId.generate(), siteId, type, description);
    }

    public void issue() {
        this.status = PermitStatus.ISSUED;
        raise(new PermitToWorkIssued(UUID.randomUUID(), Instant.now(), id().value(), siteId, type));
    }

    public void close() {
        this.status = PermitStatus.CLOSED;
    }

    public UUID siteId() { return siteId; }
    public PermitType type() { return type; }
    public String description() { return description; }
    public PermitStatus status() { return status; }
}
