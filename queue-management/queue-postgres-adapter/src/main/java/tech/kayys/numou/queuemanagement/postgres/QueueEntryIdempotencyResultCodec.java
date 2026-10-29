package tech.kayys.numou.queuemanagement.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueEntryStatus;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;
import java.time.Instant;
import java.util.UUID;

/** Stable JSON wire codec for the QueueEntry command result schema v1. */
@ApplicationScoped
public class QueueEntryIdempotencyResultCodec implements
        tech.kayys.numou.queuemanagement.spi.QueueIdempotencyResultCodec<QueueEntry> {
    private static final String RESULT_TYPE = "queue-entry.v1";

    @Override public String resultType() { return RESULT_TYPE; }
    @Override public Class<QueueEntry> javaType() { return QueueEntry.class; }

    @Override
    public String encode(QueueEntry entry) {
        WireEntry wire = new WireEntry(entry.id().value().toString(),
                entry.tenantId().value().toString(), entry.queueId().value().toString(),
                entry.profile().name(), entry.source().capability(), entry.source().resourceType(),
                entry.source().resourceId(), entry.priority(), entry.enteredAt().toString(),
                entry.updatedAt().toString(), entry.status().name(), string(entry.calledAt()),
                string(entry.servingStartedAt()), string(entry.completedAt()), entry.callCount());
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(wire);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encode queue-entry.v1 idempotency result", e);
        }
    }

    @Override
    public QueueEntry decode(String payload) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            WireEntry wire = jsonb.fromJson(payload, WireEntry.class);
            if (wire == null) throw new IllegalArgumentException("Empty queue-entry.v1 payload");
            return QueueEntry.reconstitute(QueueEntryId.of(UUID.fromString(required(wire.entryId))),
                    TenantId.of(UUID.fromString(required(wire.tenantId))),
                    QueueId.of(UUID.fromString(required(wire.queueId))),
                    QueueProfile.valueOf(required(wire.profile)),
                    new QueueSourceReference(required(wire.sourceCapability),
                            required(wire.sourceResourceType), required(wire.sourceResourceId)),
                    wire.priority, Instant.parse(required(wire.enteredAt)),
                    Instant.parse(required(wire.updatedAt)), QueueEntryStatus.valueOf(required(wire.status)),
                    instant(wire.calledAt), instant(wire.servingStartedAt), instant(wire.completedAt),
                    wire.callCount);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid queue-entry.v1 idempotency result payload", e);
        }
    }

    private static String string(Instant value) { return value == null ? null : value.toString(); }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required result field is missing");
        return value;
    }

    /** Explicit wire DTO; domain entities are never deserialized reflectively. */
    public static class WireEntry {
        public String entryId;
        public String tenantId;
        public String queueId;
        public String profile;
        public String sourceCapability;
        public String sourceResourceType;
        public String sourceResourceId;
        public int priority;
        public String enteredAt;
        public String updatedAt;
        public String status;
        public String calledAt;
        public String servingStartedAt;
        public String completedAt;
        public int callCount;

        public WireEntry() { }
        WireEntry(String entryId, String tenantId, String queueId, String profile,
                  String sourceCapability, String sourceResourceType, String sourceResourceId,
                  int priority, String enteredAt, String updatedAt, String status,
                  String calledAt, String servingStartedAt, String completedAt, int callCount) {
            this.entryId = entryId; this.tenantId = tenantId; this.queueId = queueId;
            this.profile = profile; this.sourceCapability = sourceCapability;
            this.sourceResourceType = sourceResourceType; this.sourceResourceId = sourceResourceId;
            this.priority = priority; this.enteredAt = enteredAt; this.updatedAt = updatedAt;
            this.status = status; this.calledAt = calledAt; this.servingStartedAt = servingStartedAt;
            this.completedAt = completedAt; this.callCount = callCount;
        }
    }
}
