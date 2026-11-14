package tech.kayys.numou.applicationplatform.postgres;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOperationsPort;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationQueryPort;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationStore;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationTransitionStore;
import tech.kayys.numou.applicationplatform.domain.installation.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL adapter for tenant-scoped installation state and its transactional outbox.
 * Every operation sets the trusted tenant as a transaction-local RLS variable.
 */
public final class PostgresApplicationInstallationStore
        implements ApplicationInstallationStore, ApplicationInstallationTransitionStore,
                   ApplicationInstallationOutboxStore, ApplicationInstallationQueryPort,
                   ApplicationInstallationOperationsPort {
    private static final String SELECT_INSTALLATION = """
            SELECT id, tenant_id, application_id, package_version, package_digest, status,
                   revision, created_at, updated_at, created_by, updated_by, failure_code
            FROM application_installations WHERE tenant_id=? AND id=?
            """;
    private final DataSource dataSource;

    public PostgresApplicationInstallationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public CompletionStage<CreateResult> createIfAbsent(ApplicationInstallation installation,
            String idempotencyKeySha256, ApplicationInstallationTransition requestedEvent) {
        try {
            Objects.requireNonNull(installation, "installation");
            Objects.requireNonNull(requestedEvent, "requestedEvent");
            requireHash(idempotencyKeySha256, "idempotency key hash");
            assertInitialEvent(installation, requestedEvent);
            String fingerprint = fingerprint(installation);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, installation.tenantId());
                    lockIdempotencyKey(connection, installation.tenantId(), idempotencyKeySha256);
                    Optional<CreateResult> replay = findReplay(connection, installation.tenantId(),
                            idempotencyKeySha256, fingerprint);
                    if (replay.isPresent()) {
                        connection.commit();
                        return CompletableFuture.completedFuture(replay.get());
                    }

                    if (!insertInstallation(connection, installation)) {
                        connection.commit();
                        return CompletableFuture.completedFuture(
                                new CreateResult(CreateResult.Outcome.CONFLICT, Optional.empty()));
                    }
                    insertIdempotency(connection, installation, idempotencyKeySha256, fingerprint);
                    insertTransitionAndOutbox(connection, installation, requestedEvent);
                    connection.commit();
                    return CompletableFuture.completedFuture(
                            new CreateResult(CreateResult.Outcome.CREATED, Optional.of(installation)));
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("create installation", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<Optional<ApplicationInstallation>> find(TenantId tenantId, ApplicationInstallationId id) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTALLATION)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, id.value());
                try (ResultSet result = statement.executeQuery()) {
                    Optional<ApplicationInstallation> found = result.next()
                            ? Optional.of(readInstallation(result)) : Optional.empty();
                    connection.commit();
                    return CompletableFuture.completedFuture(found);
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("find installation", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<List<ApplicationInstallation>> findEnabled(TenantId tenantId) {
        try {
            Objects.requireNonNull(tenantId, "tenantId");
            String sql = """
                    SELECT id, tenant_id, application_id, package_version, package_digest, status,
                           revision, created_at, updated_at, created_by, updated_by, failure_code
                    FROM application_installations
                    WHERE tenant_id=? AND status='ENABLED'
                    ORDER BY application_id ASC
                    """;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, tenantId);
                    List<ApplicationInstallation> installations = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setObject(1, tenantId.value());
                        try (ResultSet result = statement.executeQuery()) {
                            while (result.next()) installations.add(readInstallation(result));
                        }
                    }
                    connection.commit();
                    return CompletableFuture.completedFuture(List.copyOf(installations));
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("find enabled application installations", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<Boolean> appendTransition(ApplicationInstallation installation,
            ApplicationInstallationTransition transition, long expectedRevision) {
        try {
            Objects.requireNonNull(installation, "installation");
            Objects.requireNonNull(transition, "transition");
            if (expectedRevision < 1 || transition.revision() != expectedRevision + 1
                    || !transition.installationId().equals(installation.id())
                    || !transition.tenantId().equals(installation.tenantId())
                    || !transition.applicationId().equals(installation.applicationId())
                    || transition.toStatus() != installation.status()
                    || transition.revision() != installation.revision()
                    || transition.fromStatus().isEmpty()
                    || !transition.actorId().equals(installation.updatedBy())
                    || !transition.occurredAt().equals(installation.updatedAt()))
                throw new IllegalArgumentException("transition does not match expected installation revision and identity");
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, installation.tenantId());
                    String update = """
                            UPDATE application_installations
                            SET status=?, revision=?, updated_at=?, updated_by=?, failure_code=?
                            WHERE tenant_id=? AND id=? AND revision=? AND status=?
                            """;
                    int changed;
                    try (PreparedStatement statement = connection.prepareStatement(update)) {
                        statement.setString(1, installation.status().name());
                        statement.setLong(2, installation.revision());
                        statement.setTimestamp(3, Timestamp.from(installation.updatedAt()));
                        statement.setString(4, installation.updatedBy());
                        statement.setString(5, installation.failureCode().orElse(null));
                        statement.setObject(6, installation.tenantId().value());
                        statement.setObject(7, installation.id().value());
                        statement.setLong(8, expectedRevision);
                        statement.setString(9, transition.fromStatus().orElseThrow().name());
                        changed = statement.executeUpdate();
                    }
                    if (changed != 1) {
                        connection.rollback();
                        return CompletableFuture.completedFuture(false);
                    }
                    insertTransitionAndOutbox(connection, installation, transition);
                    connection.commit();
                    return CompletableFuture.completedFuture(true);
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("append installation transition", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<List<ClaimedEvent>> claimDue(TenantId tenantId, String workerId,
            Instant now, Duration lease, int limit) {
        try {
            Objects.requireNonNull(tenantId, "tenantId");
            requireText(workerId, "workerId");
            Objects.requireNonNull(now, "now");
            if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofMinutes(10)) > 0)
                throw new IllegalArgumentException("lease must be positive and no longer than ten minutes");
            if (limit < 1 || limit > 100) throw new IllegalArgumentException("claim limit must be between 1 and 100");
            String sql = """
                    WITH candidates AS (
                        SELECT event_id FROM application_installation_outbox
                        WHERE tenant_id=? AND available_at<=?
                          AND (delivery_status='PENDING' OR (delivery_status='LEASED' AND lease_until<=?))
                        ORDER BY occurred_at,event_id
                        LIMIT ? FOR UPDATE SKIP LOCKED
                    )
                    UPDATE application_installation_outbox o
                    SET delivery_status='LEASED', lease_owner=?, lease_token=gen_random_uuid(),
                        lease_until=?, attempts=attempts+1, version=version+1
                    FROM candidates c WHERE o.event_id=c.event_id
                    RETURNING o.event_id,o.installation_id,o.application_id,o.package_version,o.package_digest,
                              o.event_type,o.to_status,o.revision,o.actor_id,o.correlation_id,o.reason_code,o.occurred_at,
                              o.attempts,o.lease_owner,o.lease_token,o.lease_until,o.operation_id
                    """;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    setTenant(connection, tenantId);
                    List<ClaimedEvent> events = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setObject(1, tenantId.value());
                        statement.setTimestamp(2, Timestamp.from(now));
                        statement.setTimestamp(3, Timestamp.from(now));
                        statement.setInt(4, limit);
                        statement.setString(5, workerId);
                        statement.setTimestamp(6, Timestamp.from(now.plus(lease)));
                        try (ResultSet result = statement.executeQuery()) {
                            while (result.next()) events.add(new ClaimedEvent(
                                    result.getObject(1, UUID.class), result.getObject(2, UUID.class),
                                    result.getString(3), result.getString(4), result.getString(5),
                                    result.getString(6), result.getString(7), result.getLong(8),
                                    result.getString(9), result.getString(10), result.getString(11), result.getTimestamp(12).toInstant(),
                                    result.getInt(13), result.getString(14), result.getObject(15, UUID.class),
                                    result.getTimestamp(16).toInstant(), result.getObject(17, UUID.class)));
                        }
                    }
                    connection.commit();
                    return CompletableFuture.completedFuture(List.copyOf(events));
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("claim installation outbox", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<Boolean> markDelivered(TenantId tenantId, UUID eventId, String workerId,
            UUID leaseToken, Instant now) {
        String sql = """
                UPDATE application_installation_outbox
                SET delivery_status='DELIVERED', delivered_at=?, lease_owner=NULL, lease_token=NULL,
                    lease_until=NULL, last_failure_code=NULL, version=version+1
                WHERE tenant_id=? AND event_id=? AND delivery_status='LEASED'
                  AND lease_owner=? AND lease_token=? AND lease_until>?
                """;
        return fencedBooleanUpdate("mark installation event delivered", tenantId, sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setObject(2, tenantId.value());
            statement.setObject(3, eventId);
            statement.setString(4, workerId);
            statement.setObject(5, leaseToken);
            statement.setTimestamp(6, Timestamp.from(now));
        });
    }

    @Override
    public CompletionStage<RetryOutcome> retry(TenantId tenantId, UUID eventId, String workerId,
            UUID leaseToken, String stableFailureCode, Instant retryAt, Instant now, int maxAttempts) {
        if (stableFailureCode == null || !stableFailureCode.matches("^[A-Z][A-Z0-9_]{1,119}$")
                || retryAt == null || now == null || retryAt.isBefore(now)
                || maxAttempts < 1 || maxAttempts > 100)
            return CompletableFuture.failedFuture(new IllegalArgumentException("invalid failure code or retry limit"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, tenantId);
                String select = """
                        SELECT attempts,installation_id,application_id,package_version,package_digest,correlation_id
                        FROM application_installation_outbox
                        WHERE tenant_id=? AND event_id=? AND delivery_status='LEASED'
                          AND lease_owner=? AND lease_token=? AND lease_until>?
                        FOR UPDATE
                        """;
                int attempts;
                UUID installationId;
                String applicationId;
                String packageVersion;
                String packageDigest;
                String correlationId;
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, eventId);
                    statement.setString(3, workerId);
                    statement.setObject(4, leaseToken);
                    statement.setTimestamp(5, Timestamp.from(now));
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.commit();
                            return CompletableFuture.completedFuture(RetryOutcome.LEASE_LOST);
                        }
                        attempts = result.getInt(1);
                        installationId = result.getObject(2, UUID.class);
                        applicationId = result.getString(3);
                        packageVersion = result.getString(4);
                        packageDigest = result.getString(5);
                        correlationId = result.getString(6);
                    }
                }

                boolean dead = attempts >= maxAttempts;
                String update = """
                        UPDATE application_installation_outbox
                        SET delivery_status=?,available_at=?,lease_owner=NULL,lease_token=NULL,lease_until=NULL,
                            last_failure_code=?,version=version+1
                        WHERE tenant_id=? AND event_id=? AND delivery_status='LEASED'
                          AND lease_owner=? AND lease_token=? AND lease_until>?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, dead ? "DEAD" : "PENDING");
                    statement.setTimestamp(2, Timestamp.from(retryAt));
                    statement.setString(3, stableFailureCode);
                    statement.setObject(4, tenantId.value());
                    statement.setObject(5, eventId);
                    statement.setString(6, workerId);
                    statement.setObject(7, leaseToken);
                    statement.setTimestamp(8, Timestamp.from(now));
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return CompletableFuture.completedFuture(RetryOutcome.LEASE_LOST);
                    }
                }
                if (dead) {
                    markInstallationFailedAtomically(connection, tenantId, installationId, applicationId,
                            packageVersion, packageDigest, correlationId, stableFailureCode, now);
                }
                connection.commit();
                return CompletableFuture.completedFuture(dead ? RetryOutcome.DEAD_LETTERED : RetryOutcome.RESCHEDULED);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("retry installation outbox event", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * The terminal outbox transition and aggregate failure transition share the
     * caller's transaction. A process crash can therefore never commit DEAD while
     * leaving an active installation permanently stuck in PROVISIONING.
     */
    private static void markInstallationFailedAtomically(Connection connection, TenantId tenantId,
            UUID installationId, String applicationId, String packageVersion, String packageDigest,
            String correlationId, String failureCode, Instant now) throws SQLException {
        String select = """
                SELECT status,revision FROM application_installations
                WHERE tenant_id=? AND id=? FOR UPDATE
                """;
        String status;
        long revision;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, installationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("dead-letter installation aggregate is missing");
                status = result.getString(1);
                revision = result.getLong(2);
            }
        }
        if (!Set.of("REQUESTED", "VALIDATING", "AWAITING_APPROVAL", "PROVISIONING").contains(status)) return;

        String update = """
                UPDATE application_installations
                SET status='FAILED',revision=?,updated_at=?,updated_by='system:application-platform',failure_code=?
                WHERE tenant_id=? AND id=? AND revision=? AND status=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, revision + 1);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, failureCode);
            statement.setObject(4, tenantId.value());
            statement.setObject(5, installationId);
            statement.setLong(6, revision);
            statement.setString(7, status);
            if (statement.executeUpdate() != 1) throw new SQLException("installation changed while reconciling dead letter");
        }

        UUID transitionId = UUID.randomUUID();
        String transition = """
                INSERT INTO application_installation_transitions
                  (event_id,tenant_id,installation_id,application_id,from_status,to_status,revision,
                   actor_id,correlation_id,occurred_at,reason_code)
                VALUES (?,?,?,?,?,'FAILED',?,'system:application-platform',?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(transition)) {
            statement.setObject(1, transitionId);
            statement.setObject(2, tenantId.value());
            statement.setObject(3, installationId);
            statement.setString(4, applicationId);
            statement.setString(5, status);
            statement.setLong(6, revision + 1);
            statement.setString(7, correlationId == null ? "" : correlationId);
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setString(9, failureCode);
            statement.executeUpdate();
        }

        String outbox = """
                INSERT INTO application_installation_outbox
                  (event_id,tenant_id,installation_id,application_id,package_version,package_digest,
                   event_type,to_status,revision,actor_id,correlation_id,reason_code,occurred_at,available_at)
                VALUES (?,?,?,?,?,?,'APPLICATION_INSTALLATION_STATUS_CHANGED','FAILED',?,
                        'system:application-platform',?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(outbox)) {
            statement.setObject(1, transitionId);
            statement.setObject(2, tenantId.value());
            statement.setObject(3, installationId);
            statement.setString(4, applicationId);
            statement.setString(5, packageVersion);
            statement.setString(6, packageDigest);
            statement.setLong(7, revision + 1);
            statement.setString(8, correlationId == null ? "" : correlationId);
            statement.setString(9, failureCode);
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    @Override
    public CompletionStage<List<TransitionEntry>> history(TenantId tenantId,
            ApplicationInstallationId installationId, int limit) {
        if (tenantId == null || installationId == null || limit < 1 || limit > 100)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenant, installation and bounded limit are required"));
        String sql = """
                SELECT event_id,from_status,to_status,revision,actor_id,correlation_id,occurred_at,reason_code
                FROM application_installation_transitions
                WHERE tenant_id=? AND installation_id=?
                ORDER BY revision DESC,event_id DESC LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            List<TransitionEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, installationId.value());
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) entries.add(new TransitionEntry(
                            result.getObject(1, UUID.class), result.getString(2), result.getString(3),
                            result.getLong(4), result.getString(5), result.getString(6),
                            result.getTimestamp(7).toInstant(), result.getString(8)));
                }
            }
            connection.commit();
            return CompletableFuture.completedFuture(List.copyOf(entries));
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("read installation transition history", failure));
        }
    }

    @Override
    public CompletionStage<List<DeadLetterEntry>> deadLetters(TenantId tenantId, int limit) {
        if (tenantId == null || limit < 1 || limit > 100)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenant and bounded limit are required"));
        String sql = """
                SELECT event_id,installation_id,application_id,package_version,last_failure_code,attempts,occurred_at
                FROM application_installation_outbox
                WHERE tenant_id=? AND delivery_status='DEAD'
                  AND event_type IN ('APPLICATION_INSTALLATION_REQUESTED','APPLICATION_INSTALLATION_RETRY_REQUESTED')
                ORDER BY occurred_at DESC,event_id DESC LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            List<DeadLetterEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) entries.add(new DeadLetterEntry(
                            result.getObject(1, UUID.class), result.getObject(2, UUID.class),
                            result.getString(3), result.getString(4), result.getString(5),
                            result.getInt(6), result.getTimestamp(7).toInstant()));
                }
            }
            connection.commit();
            return CompletableFuture.completedFuture(List.copyOf(entries));
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("inspect installation dead letters", failure));
        }
    }

    @Override
    public CompletionStage<RedriveResult> redrive(RedriveCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = sha256(command.installationId().value() + "\u0000"
                + command.actorId() + "\u0000" + command.reason().trim());
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, command.tenantId());
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))")) {
                    lock.setString(1, command.tenantId().value().toString());
                    lock.setString(2, command.idempotencyKeySha256());
                    lock.execute();
                }
                String replaySql = """
                        SELECT request_fingerprint,redrive_event_id,operation_id
                        FROM application_installation_redrive_audit
                        WHERE tenant_id=? AND idempotency_key_sha256=? FOR UPDATE
                        """;
                try (PreparedStatement statement = connection.prepareStatement(replaySql)) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setString(2, command.idempotencyKeySha256());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            if (!fingerprint.equals(result.getString(1))) {
                                connection.commit();
                                return CompletableFuture.completedFuture(new RedriveResult(
                                        RedriveResult.Outcome.CONFLICT, null, null));
                            }
                            RedriveResult replay = new RedriveResult(RedriveResult.Outcome.REPLAYED,
                                    result.getObject(2, UUID.class), result.getObject(3, UUID.class));
                            connection.commit();
                            return CompletableFuture.completedFuture(replay);
                        }
                    }
                }

                Optional<ApplicationInstallation> found;
                try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTALLATION + " FOR UPDATE")) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setObject(2, command.installationId().value());
                    try (ResultSet result = statement.executeQuery()) {
                        found = result.next() ? Optional.of(readInstallation(result)) : Optional.empty();
                    }
                }
                if (found.isEmpty() || found.get().status() != ApplicationInstallationStatus.FAILED) {
                    connection.commit();
                    return CompletableFuture.completedFuture(new RedriveResult(
                            RedriveResult.Outcome.NOT_REDRIVABLE, null, null));
                }
                ApplicationInstallation installation = found.get();

                UUID deadEventId;
                UUID operationId;
                String deadSql = """
                        SELECT event_id,operation_id FROM application_installation_outbox
                        WHERE tenant_id=? AND installation_id=? AND delivery_status='DEAD'
                          AND event_type IN ('APPLICATION_INSTALLATION_REQUESTED','APPLICATION_INSTALLATION_RETRY_REQUESTED')
                        ORDER BY occurred_at DESC,event_id DESC LIMIT 1 FOR UPDATE
                        """;
                try (PreparedStatement statement = connection.prepareStatement(deadSql)) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setObject(2, command.installationId().value());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.commit();
                            return CompletableFuture.completedFuture(new RedriveResult(
                                    RedriveResult.Outcome.NOT_REDRIVABLE, null, null));
                        }
                        deadEventId = result.getObject(1, UUID.class);
                        operationId = result.getObject(2, UUID.class);
                    }
                }

                long expectedRevision = installation.revision();
                ApplicationInstallationTransition transition = installation.retry(
                        command.actorId(), "OPERATOR_REDRIVE", command.now());
                String update = """
                        UPDATE application_installations
                        SET status='VALIDATING',revision=?,updated_at=?,updated_by=?,failure_code=NULL
                        WHERE tenant_id=? AND id=? AND revision=? AND status='FAILED'
                        """;
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setLong(1, installation.revision());
                    statement.setTimestamp(2, Timestamp.from(installation.updatedAt()));
                    statement.setString(3, installation.updatedBy());
                    statement.setObject(4, command.tenantId().value());
                    statement.setObject(5, command.installationId().value());
                    statement.setLong(6, expectedRevision);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return CompletableFuture.completedFuture(new RedriveResult(
                                RedriveResult.Outcome.NOT_REDRIVABLE, null, null));
                    }
                }

                UUID redriveEventId = UUID.randomUUID();
                String transitionSql = """
                        INSERT INTO application_installation_transitions
                          (event_id,tenant_id,installation_id,application_id,from_status,to_status,revision,
                           actor_id,correlation_id,occurred_at,reason_code)
                        VALUES (?,?,?,?,'FAILED','VALIDATING',?,?,?,?,'OPERATOR_REDRIVE')
                        """;
                try (PreparedStatement statement = connection.prepareStatement(transitionSql)) {
                    statement.setObject(1, redriveEventId);
                    statement.setObject(2, command.tenantId().value());
                    statement.setObject(3, command.installationId().value());
                    statement.setString(4, installation.applicationId().value());
                    statement.setLong(5, installation.revision());
                    statement.setString(6, command.actorId());
                    statement.setString(7, "OPERATOR_REDRIVE");
                    statement.setTimestamp(8, Timestamp.from(command.now()));
                    statement.executeUpdate();
                }
                String outboxSql = """
                        INSERT INTO application_installation_outbox
                          (event_id,tenant_id,installation_id,application_id,package_version,package_digest,
                           event_type,to_status,revision,actor_id,correlation_id,reason_code,occurred_at,available_at,operation_id)
                        VALUES (?,?,?,?,?,?,'APPLICATION_INSTALLATION_RETRY_REQUESTED','VALIDATING',?,
                                ?,?,'OPERATOR_REDRIVE',?,?,?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(outboxSql)) {
                    statement.setObject(1, redriveEventId);
                    statement.setObject(2, command.tenantId().value());
                    statement.setObject(3, command.installationId().value());
                    statement.setString(4, installation.applicationId().value());
                    statement.setString(5, installation.packageVersion().value());
                    statement.setString(6, installation.packageDigest());
                    statement.setLong(7, installation.revision());
                    statement.setString(8, command.actorId());
                    statement.setString(9, "OPERATOR_REDRIVE");
                    statement.setTimestamp(10, Timestamp.from(command.now()));
                    statement.setTimestamp(11, Timestamp.from(command.now()));
                    statement.setObject(12, operationId);
                    statement.executeUpdate();
                }
                String auditSql = """
                        INSERT INTO application_installation_redrive_audit
                          (audit_id,tenant_id,installation_id,dead_event_id,redrive_event_id,operation_id,
                           idempotency_key_sha256,request_fingerprint,actor_id,reason,occurred_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(auditSql)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, command.tenantId().value());
                    statement.setObject(3, command.installationId().value());
                    statement.setObject(4, deadEventId);
                    statement.setObject(5, redriveEventId);
                    statement.setObject(6, operationId);
                    statement.setString(7, command.idempotencyKeySha256());
                    statement.setString(8, fingerprint);
                    statement.setString(9, command.actorId());
                    statement.setString(10, command.reason().trim());
                    statement.setTimestamp(11, Timestamp.from(command.now()));
                    statement.executeUpdate();
                }
                connection.commit();
                return CompletableFuture.completedFuture(new RedriveResult(
                        RedriveResult.Outcome.QUEUED, redriveEventId, operationId));
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure("redrive failed installation", failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Boolean> fencedBooleanUpdate(String operation, TenantId tenantId, String sql,
            StatementBinder binder) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                boolean updated = statement.executeUpdate() == 1;
                connection.commit();
                return CompletableFuture.completedFuture(updated);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure(operation, failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<RetryOutcome> fencedRetryUpdate(String operation, TenantId tenantId, String sql,
            StatementBinder binder) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return CompletableFuture.completedFuture(RetryOutcome.LEASE_LOST);
                    }
                    RetryOutcome outcome = "DEAD".equals(result.getString(1))
                            ? RetryOutcome.DEAD_LETTERED : RetryOutcome.RESCHEDULED;
                    connection.commit();
                    return CompletableFuture.completedFuture(outcome);
                }
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(storageFailure(operation, failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void assertInitialEvent(ApplicationInstallation installation,
            ApplicationInstallationTransition event) {
        if (event.revision() != 1 || event.fromStatus().isPresent()
                || event.toStatus() != ApplicationInstallationStatus.REQUESTED
                || !event.installationId().equals(installation.id())
                || !event.tenantId().equals(installation.tenantId())
                || !event.applicationId().equals(installation.applicationId())
                || !event.actorId().equals(installation.createdBy())
                || !event.occurredAt().equals(installation.createdAt())
                || !"INSTALL_REQUESTED".equals(event.reasonCode()))
            throw new IllegalArgumentException("initial transition does not match installation request");
    }

    private static boolean insertInstallation(Connection connection, ApplicationInstallation value) throws SQLException {
        String sql = """
                INSERT INTO application_installations
                  (id,tenant_id,application_id,package_version,package_digest,status,revision,
                   created_at,updated_at,created_by,updated_by,failure_code)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (tenant_id,application_id) DO NOTHING
                RETURNING id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value.id().value());
            statement.setObject(2, value.tenantId().value());
            statement.setString(3, value.applicationId().value());
            statement.setString(4, value.packageVersion().value());
            statement.setString(5, value.packageDigest());
            statement.setString(6, value.status().name());
            statement.setLong(7, value.revision());
            statement.setTimestamp(8, Timestamp.from(value.createdAt()));
            statement.setTimestamp(9, Timestamp.from(value.updatedAt()));
            statement.setString(10, value.createdBy());
            statement.setString(11, value.updatedBy());
            statement.setString(12, value.failureCode().orElse(null));
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static void insertIdempotency(Connection connection, ApplicationInstallation value,
            String keyHash, String fingerprint) throws SQLException {
        String sql = """
                INSERT INTO application_installation_idempotency
                  (tenant_id,idempotency_key_sha256,request_fingerprint,installation_id,created_at)
                VALUES (?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value.tenantId().value());
            statement.setString(2, keyHash);
            statement.setString(3, fingerprint);
            statement.setObject(4, value.id().value());
            statement.setTimestamp(5, Timestamp.from(value.createdAt()));
            statement.executeUpdate();
        }
    }

    private Optional<CreateResult> findReplay(Connection connection, TenantId tenantId,
            String keyHash, String fingerprint) throws SQLException {
        String sql = """
                SELECT request_fingerprint,installation_id
                FROM application_installation_idempotency
                WHERE tenant_id=? AND idempotency_key_sha256=? FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setString(2, keyHash);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                if (!fingerprint.equals(result.getString(1)))
                    return Optional.of(new CreateResult(CreateResult.Outcome.CONFLICT, Optional.empty()));
                UUID id = result.getObject(2, UUID.class);
                Optional<ApplicationInstallation> existing = findInstallation(connection, tenantId, id);
                if (existing.isEmpty()) throw new SQLException("idempotency record references a missing installation");
                return Optional.of(new CreateResult(CreateResult.Outcome.REPLAYED, existing));
            }
        }
    }

    private Optional<ApplicationInstallation> findInstallation(Connection connection, TenantId tenantId, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTALLATION)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readInstallation(result)) : Optional.empty();
            }
        }
    }

    private static ApplicationInstallation readInstallation(ResultSet result) throws SQLException {
        Timestamp created = result.getTimestamp("created_at");
        Timestamp updated = result.getTimestamp("updated_at");
        return ApplicationInstallation.reconstitute(
                new ApplicationInstallationId(result.getObject("id", UUID.class)),
                TenantId.of(result.getObject("tenant_id", UUID.class)),
                new ApplicationId(result.getString("application_id")),
                new PackageVersion(result.getString("package_version")),
                result.getString("package_digest"),
                ApplicationInstallationStatus.valueOf(result.getString("status")),
                result.getLong("revision"), created.toInstant(), updated.toInstant(),
                result.getString("created_by"), result.getString("updated_by"),
                result.getString("failure_code"));
    }

    private static void insertTransitionAndOutbox(Connection connection, ApplicationInstallation installation,
            ApplicationInstallationTransition event) throws SQLException {
        if (event.toStatus() != installation.status() || event.revision() != installation.revision())
            throw new IllegalArgumentException("outbox transition must match the current installation snapshot");
        UUID eventId = UUID.randomUUID();
        String transitionSql = """
                INSERT INTO application_installation_transitions
                  (event_id,tenant_id,installation_id,application_id,from_status,to_status,revision,
                   actor_id,correlation_id,occurred_at,reason_code)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(transitionSql)) {
            statement.setObject(1, eventId);
            statement.setObject(2, event.tenantId().value());
            statement.setObject(3, event.installationId().value());
            statement.setString(4, event.applicationId().value());
            if (event.fromStatus().isPresent()) statement.setString(5, event.fromStatus().get().name());
            else statement.setNull(5, Types.VARCHAR);
            statement.setString(6, event.toStatus().name());
            statement.setLong(7, event.revision());
            statement.setString(8, event.actorId());
            statement.setString(9, event.correlationId());
            statement.setTimestamp(10, Timestamp.from(event.occurredAt()));
            statement.setString(11, event.reasonCode());
            statement.executeUpdate();
        }
        String outboxSql = """
                INSERT INTO application_installation_outbox
                  (event_id,tenant_id,installation_id,application_id,package_version,package_digest,
                   event_type,to_status,revision,actor_id,correlation_id,reason_code,occurred_at,available_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(outboxSql)) {
            statement.setObject(1, eventId);
            statement.setObject(2, event.tenantId().value());
            statement.setObject(3, event.installationId().value());
            statement.setString(4, event.applicationId().value());
            statement.setString(5, installation.packageVersion().value());
            statement.setString(6, installation.packageDigest());
            statement.setString(7, event.toStatus() == ApplicationInstallationStatus.REQUESTED
                    ? "APPLICATION_INSTALLATION_REQUESTED" : "APPLICATION_INSTALLATION_STATUS_CHANGED");
            statement.setString(8, event.toStatus().name());
            statement.setLong(9, event.revision());
            statement.setString(10, event.actorId());
            statement.setString(11, event.correlationId());
            statement.setString(12, event.reasonCode());
            statement.setTimestamp(13, Timestamp.from(event.occurredAt()));
            statement.setTimestamp(14, Timestamp.from(event.occurredAt()));
            statement.executeUpdate();
        }
    }

    private static void setTenant(Connection connection, TenantId tenantId) throws SQLException {
        Objects.requireNonNull(tenantId, "tenantId");
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.value().toString());
            statement.execute();
        }
    }

    private static void lockIdempotencyKey(Connection connection, TenantId tenantId, String keyHash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))")) {
            statement.setString(1, tenantId.value().toString());
            statement.setString(2, keyHash);
            statement.execute();
        }
    }

    private static String fingerprint(ApplicationInstallation value) {
        return sha256(value.applicationId().value() + "\u0000" + value.packageVersion().value()
                + "\u0000" + value.packageDigest());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private static void requireHash(String value, String label) {
        if (value == null || !value.matches("^[a-f0-9]{64}$"))
            throw new IllegalArgumentException(label + " must be lowercase SHA-256 hex");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 160)
            throw new IllegalArgumentException(field + " must be present and at most 160 characters");
        return value.trim();
    }

    private static void rollback(Connection connection, Throwable failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    private static IllegalStateException storageFailure(String operation, SQLException failure) {
        return new IllegalStateException("Unable to " + operation + " in application platform storage", failure);
    }

    @FunctionalInterface private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
}
