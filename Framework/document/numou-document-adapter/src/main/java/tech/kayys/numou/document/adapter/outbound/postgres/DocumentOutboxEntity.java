package tech.kayys.syirkah.document.adapter.outbound.postgres;

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
@Table(name = "document_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uq_document_outbox_event_id", columnNames = "event_id"))
public class DocumentOutboxEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "event_id", nullable = false)
    public UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 150)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 150)
    public String aggregateId;

    @Column(name = "event_type", nullable = false, length = 200)
    public String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    public Instant nextAttemptAt;

    @Column(name = "lease_until")
    public Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    public int attemptCount;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    public String lastError;
}
