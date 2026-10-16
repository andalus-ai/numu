package tech.kayys.numou.foundation.domain.audit;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Standard audit metadata tracking creation and modification context across all aggregates.
 */
public record AuditMeta(
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) implements Serializable {

    public AuditMeta {
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static AuditMeta initial(String actor, Instant timestamp) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        return new AuditMeta(actor, timestamp, actor, timestamp);
    }

    public AuditMeta updated(String actor, Instant timestamp) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        return new AuditMeta(this.createdBy, this.createdAt, actor, timestamp);
    }
}
