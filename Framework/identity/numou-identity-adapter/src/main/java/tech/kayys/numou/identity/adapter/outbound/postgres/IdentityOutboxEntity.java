package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_outbox", uniqueConstraints =
        @UniqueConstraint(name = "identity_outbox_event_id_key", columnNames = "event_id"))
public class IdentityOutboxEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "event_id", nullable = false)
    public UUID eventId;

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    public Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    public int attemptCount;

    @Column(nullable = false)
    public String status;

    @Column(name = "last_error")
    public String lastError;
}
