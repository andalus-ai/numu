package tech.kayys.numou.workforce.domain.position;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.position.event.PositionActivated;
import tech.kayys.numou.workforce.domain.position.event.PositionCreated;
import tech.kayys.numou.workforce.domain.position.event.PositionDeactivated;

import java.time.Instant;
import java.util.Objects;

/**
 * Position aggregate root defining a functional role or job position within an Organization.
 */
public final class Position extends AbstractAggregateRoot<PositionId> {

    private final OrganizationRef organization;
    private final String code;
    private String title;
    private String description;
    private PositionStatus status;

    private Position(
            PositionId id,
            OrganizationRef organization,
            String code,
            String title,
            String description
    ) {
        super(id);
        this.organization = Objects.requireNonNull(organization, "OrganizationRef must not be null");
        this.code = Objects.requireNonNull(code, "Position code must not be null");
        this.title = Objects.requireNonNull(title, "Position title must not be null");
        this.description = description;
        this.status = PositionStatus.ACTIVE;
    }

    public static Position create(
            PositionId id,
            OrganizationRef organization,
            String code,
            String title,
            String description
    ) {
        Objects.requireNonNull(id, "PositionId must not be null");
        Position position = new Position(id, organization, code, title, description);
        position.raise(new PositionCreated(id, organization, code, title));
        return position;
    }

    public void activate() {
        if (status == PositionStatus.ACTIVE) {
            return;
        }
        this.status = PositionStatus.ACTIVE;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new PositionActivated(id));
    }

    public void deactivate() {
        if (status == PositionStatus.INACTIVE) {
            return;
        }
        this.status = PositionStatus.INACTIVE;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new PositionDeactivated(id));
    }

    public void updateDetails(String title, String description) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.description = description;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public OrganizationRef organization() {
        return organization;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public PositionStatus status() {
        return status;
    }
}
