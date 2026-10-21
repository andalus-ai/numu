package tech.kayys.numou.identity.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Drains {@code identity_outbox} into the {@code identity.audit-log} Kafka topic - the immutable
 * audit event stream required for SOC2/ISO 27001/GDPR evidence.
 *
 * <p>Delivery is <b>at-least-once</b>: a row is marked {@code PUBLISHED} only after the broker
 * acknowledged the record, so a crash between publish and commit can re-send an event. Consumers
 * must deduplicate on {@code eventId}, which is part of every payload.
 *
 * <p>Partition key: the event's {@code principalId} when present, otherwise {@code tenantId},
 * otherwise the aggregate id - so all events of one principal/tenant stay ordered on one partition.
 */
@ApplicationScoped
public class IdentityOutboxRelay {

    private static final Logger LOG = Logger.getLogger(IdentityOutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 10;

    private static final String SELECT_PENDING = """
            SELECT id, event_id, tenant_id, aggregate_id, payload, attempt_count
            FROM identity_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= ?
            ORDER BY created_at
            LIMIT %d
            FOR UPDATE SKIP LOCKED
            """.formatted(BATCH_SIZE);

    private static final String MARK_PUBLISHED =
            "UPDATE identity_outbox SET status = 'PUBLISHED', last_error = NULL WHERE id = ?";

    private static final String MARK_FAILED = """
            UPDATE identity_outbox
            SET attempt_count = ?, next_attempt_at = ?, last_error = ?
            WHERE id = ?
            """;

    private final DataSource dataSource;
    private final MutinyEmitter<String> emitter;
    private final ObjectMapper objectMapper;

    @Inject
    public IdentityOutboxRelay(
            DataSource dataSource,
            @Channel("identity-audit-log") MutinyEmitter<String> emitter,
            ObjectMapper objectMapper
    ) {
        this.dataSource = dataSource;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Blocking
    @Scheduled(
            every = "{numou.identity.outbox.poll-interval:PT5S}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void relayBatch() {
        for (PendingRow row : fetchPending()) {
            try {
                publish(row);
                markPublished(row.id());
            } catch (Exception ex) {
                LOG.warnf(ex, "Audit outbox relay failed for event %s", row.eventId());
                markFailed(row, ex);
            }
        }
    }

    private List<PendingRow> fetchPending() {
        List<PendingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PENDING)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID tenantId = (UUID) resultSet.getObject(3);
                    String aggregateId = resultSet.getString(4);
                    rows.add(new PendingRow(
                            (UUID) resultSet.getObject(1),
                            (UUID) resultSet.getObject(2),
                            tenantId,
                            aggregateId,
                            resultSet.getString(5),
                            resultSet.getInt(6)
                    ));
                }
            }
        } catch (Exception ex) {
            LOG.warnf(ex, "Could not read identity_outbox batch");
            rows.clear();
        }
        return rows;
    }

    private void publish(PendingRow row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.payload()); // rejects malformed payloads pre-publish
        Message<String> message = Message.of(row.payload())
                .addMetadata(OutgoingKafkaRecordMetadata.builder()
                        .withKey(partitionKey(payload, row).toString())
                        .build());
        emitter.sendMessageAndAwait(message);
    }

    /** principalId of the event, else tenantId, else aggregate id (event id) - see class javadoc. */
    private static UUID partitionKey(JsonNode payload, PendingRow row) {
        String principalId = payload.path("principalId").asText(null);
        if (principalId != null) {
            try {
                return UUID.fromString(principalId);
            } catch (IllegalArgumentException ignored) {
                // fall through to the tenant/aggregate key
            }
        }
        if (row.tenantId() != null) {
            return row.tenantId();
        }
        try {
            return UUID.fromString(row.aggregateId());
        } catch (RuntimeException ex) {
            return row.eventId();
        }
    }

    private void markPublished(UUID id) {
        executeUpdate(MARK_PUBLISHED, statement -> statement.setObject(1, id));
    }

    private void markFailed(PendingRow row, Exception failure) {
        int attempts = row.attemptCount() + 1;
        String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        if (attempts >= MAX_ATTEMPTS) {
            LOG.errorf("Audit event %s parked after %d attempts: %s", row.eventId(), attempts, reason);
        }
        executeUpdate(MARK_FAILED, statement -> {
            statement.setInt(1, attempts);
            statement.setTimestamp(2, Timestamp.from(Instant.now().plus(backoff(attempts))));
            statement.setString(3, reason.length() > 500 ? reason.substring(0, 500) : reason);
            statement.setObject(4, row.id());
        });
    }

    private void executeUpdate(String sql, SqlConsumer binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            statement.executeUpdate();
        } catch (Exception ex) {
            LOG.warnf(ex, "Could not update identity_outbox row");
        }
    }

    private static Duration backoff(int attempts) {
        long seconds = Math.min(60L << Math.min(attempts - 1, 10), 600L);
        return Duration.ofSeconds(Math.max(1L, seconds));
    }

    private record PendingRow(
            UUID id,
            UUID eventId,
            UUID tenantId,
            String aggregateId,
            String payload,
            int attemptCount
    ) { }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(PreparedStatement statement) throws Exception;
    }
}
