package tech.kayys.numou.organization.adapter.jdbc;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociation;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociationHistory;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociationStatus;
import tech.kayys.numou.organization.spi.OrganizationPartyAssociationRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * PostgreSQL JDBC adapter for Organization-to-Party associations.
 *
 * <p>The caller supplies a bounded JDBC executor; blocking JDBC work is never run on the
 * caller's request/event-loop thread. Each save and its immutable history entry commit together.</p>
 */
public final class JdbcOrganizationPartyAssociationRepository
        implements OrganizationPartyAssociationRepository {

    private static final String COLUMNS = """
            association_id, organization_id, party_id, status, created_at, updated_at,
            verified_at, closed_at, created_by, updated_by, evidence_reference, reason, version
            """;

    private final DataSource dataSource;
    private final Executor jdbcExecutor;

    public JdbcOrganizationPartyAssociationRepository(DataSource dataSource, Executor jdbcExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcExecutor = Objects.requireNonNull(jdbcExecutor, "jdbcExecutor");
    }

    @Override
    public CompletionStage<OrganizationPartyAssociation> save(OrganizationPartyAssociation association) {
        Objects.requireNonNull(association, "association");
        return CompletableFuture.supplyAsync(() -> saveBlocking(association, null), jdbcExecutor);
    }

    @Override
    public CompletionStage<OrganizationPartyAssociation> save(OrganizationPartyAssociation association, UUID tenantId) {
        Objects.requireNonNull(association, "association");
        Objects.requireNonNull(tenantId, "tenantId");
        return CompletableFuture.supplyAsync(() -> saveBlocking(association, tenantId), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationPartyAssociation>> findById(UUID associationId) {
        Objects.requireNonNull(associationId, "associationId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_party_associations WHERE association_id = ?",
                statement -> statement.setObject(1, associationId)), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByOrganizationId(
            OrganizationId organizationId) {
        Objects.requireNonNull(organizationId, "organizationId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_party_associations "
                        + "WHERE organization_id = ? "
                        + "AND status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED')",
                statement -> statement.setObject(1, organizationId.value())), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByPartyId(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_party_associations "
                        + "WHERE party_id = ? "
                        + "AND status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED')",
                statement -> statement.setObject(1, partyId.value())), jdbcExecutor);
    }

    @Override
    public CompletionStage<List<OrganizationPartyAssociationHistory>> findHistory(UUID associationId) {
        Objects.requireNonNull(associationId, "associationId");
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT event_id, association_id, event_type, from_status, to_status,
                           occurred_at, actor_id, reason
                    FROM organization_party_association_history
                    WHERE association_id = ?
                    ORDER BY occurred_at, event_id
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, associationId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<OrganizationPartyAssociationHistory> history = new ArrayList<>();
                    while (rows.next()) {
                        String from = rows.getString("from_status");
                        history.add(new OrganizationPartyAssociationHistory(
                                rows.getObject("event_id", UUID.class),
                                rows.getObject("association_id", UUID.class),
                                rows.getString("event_type"),
                                from == null ? null : OrganizationPartyAssociationStatus.valueOf(from),
                                OrganizationPartyAssociationStatus.valueOf(rows.getString("to_status")),
                                rows.getTimestamp("occurred_at").toInstant(),
                                rows.getString("actor_id"),
                                rows.getString("reason")));
                    }
                    return List.copyOf(history);
                }
            } catch (SQLException e) {
                throw persistenceFailure("read Organization-Party association history", e);
            }
        }, jdbcExecutor);
    }

    private OrganizationPartyAssociation saveBlocking(OrganizationPartyAssociation candidate, UUID tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<OrganizationPartyAssociation> current =
                        findByIdForUpdate(connection, candidate.id());
                if (current.isEmpty()) {
                    if (candidate.version() != 0
                            || candidate.status() != OrganizationPartyAssociationStatus.PENDING_VERIFICATION) {
                        throw new IllegalArgumentException(
                                "A new association must start as PENDING_VERIFICATION at version zero");
                    }
                    insert(connection, candidate);
                    UUID eventId = appendHistory(connection, candidate.id(), "PROPOSED", null,
                            candidate.status(), candidate.updatedAt(), candidate.updatedBy(), candidate.reason());
                    if (tenantId != null) appendOutbox(connection, candidate, tenantId, eventId, "PROPOSED");
                } else {
                    OrganizationPartyAssociation previous = current.get();
                    if (!previous.organizationId().equals(candidate.organizationId())
                            || !previous.partyId().equals(candidate.partyId())
                            || !previous.createdAt().equals(candidate.createdAt())
                            || !previous.createdBy().equals(candidate.createdBy())
                            || !Objects.equals(previous.evidenceReference(), candidate.evidenceReference())) {
                        throw new IllegalArgumentException("Association identity and provenance are immutable");
                    }
                    if (previous.equals(candidate)) {
                        connection.commit();
                        return candidate; // exact retry: no duplicate history row
                    }
                    if (candidate.version() != previous.version() + 1) {
                        throw new OptimisticAssociationConflictException(
                                "Association version changed; reload before applying the transition");
                    }
                    if (!allowed(previous.status(), candidate.status())) {
                        throw new IllegalArgumentException("Invalid association lifecycle transition: "
                                + previous.status() + " -> " + candidate.status());
                    }
                    update(connection, candidate, previous.version());
                    String transition = previous.status().name() + "_TO_" + candidate.status().name();
                    UUID eventId = appendHistory(connection, candidate.id(), transition,
                            previous.status(), candidate.status(), candidate.updatedAt(),
                            candidate.updatedBy(), candidate.reason());
                    if (tenantId != null) appendOutbox(connection, candidate, tenantId, eventId, transition);
                }
                connection.commit();
                return candidate;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new AssociationConflictException(
                            "An Organization or Party already has a current association", sql);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw persistenceFailure("save Organization-Party association", (SQLException) failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open Organization-Party association transaction", e);
        }
    }

    private Optional<OrganizationPartyAssociation> findByIdForUpdate(Connection connection, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM organization_party_associations WHERE association_id = ? FOR UPDATE")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, OrganizationPartyAssociation a) throws SQLException {
        String sql = """
                INSERT INTO organization_party_associations
                (association_id, organization_id, party_id, status, created_at, updated_at,
                 verified_at, closed_at, created_by, updated_by, evidence_reference, reason, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, a);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, OrganizationPartyAssociation a, long expectedVersion)
            throws SQLException {
        String sql = """
                UPDATE organization_party_associations
                SET status = ?, updated_at = ?, verified_at = ?, closed_at = ?,
                    updated_by = ?, reason = ?, version = ?
                WHERE association_id = ? AND version = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, a.status().name());
            statement.setTimestamp(2, Timestamp.from(a.updatedAt()));
            setInstant(statement, 3, a.verifiedAt());
            setInstant(statement, 4, a.closedAt());
            statement.setString(5, a.updatedBy());
            statement.setString(6, a.reason());
            statement.setLong(7, a.version());
            statement.setObject(8, a.id());
            statement.setLong(9, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticAssociationConflictException("Association was modified concurrently");
            }
        }
    }

    private UUID appendHistory(Connection connection, UUID associationId, String eventType,
                               OrganizationPartyAssociationStatus fromStatus,
                               OrganizationPartyAssociationStatus toStatus, Instant occurredAt,
                               String actorId, String reason) throws SQLException {
        UUID eventId = UUID.randomUUID();
        String sql = """
                INSERT INTO organization_party_association_history
                (event_id, association_id, event_type, from_status, to_status, occurred_at, actor_id, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, eventId);
            statement.setObject(2, associationId);
            statement.setString(3, eventType);
            statement.setString(4, fromStatus == null ? null : fromStatus.name());
            statement.setString(5, toStatus.name());
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            statement.setString(7, actorId);
            statement.setString(8, reason);
            statement.executeUpdate();
        }
        return eventId;
    }

    /** Insert the standard platform envelope using the association transaction's JDBC connection. */
    private void appendOutbox(Connection connection, OrganizationPartyAssociation a, UUID tenantId,
                              UUID eventId, String transition) throws SQLException {
        String sql = """
                INSERT INTO platform_outbox
                    (entry_id, event_id, tenant_id, event_type, payload, created_at, available_at)
                VALUES (?, ?, ?, ?, jsonb_build_object(
                    'specVersion', '1', 'eventId', ?::text, 'eventType', ?, 'eventVersion', '1.0',
                    'correlationId', NULL, 'causationId', NULL, 'tenantId', ?::text,
                    'participantId', NULL, 'actorId', ?, 'occurredAt', ?, 'traceId', NULL,
                    'payload', jsonb_build_object('eventId', ?::text, 'occurredAtIso', ?,
                        'eventType', ?, 'data', jsonb_build_object(
                            'associationId', ?::text, 'organizationId', ?::text,
                            'partyId', ?::text, 'status', ?, 'version', ?,
                            'transition', ?, 'reason', ?)))), ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, eventId);
            statement.setObject(3, tenantId);
            statement.setString(4, "organization.party-association.changed");
            statement.setObject(5, eventId);
            statement.setString(6, "organization.party-association.changed");
            statement.setObject(7, tenantId);
            statement.setString(8, a.updatedBy());
            statement.setString(9, a.updatedAt().toString());
            statement.setObject(10, eventId);
            statement.setString(11, a.updatedAt().toString());
            statement.setString(12, "organization.party-association.changed");
            statement.setObject(13, a.id());
            statement.setObject(14, a.organizationId().value());
            statement.setObject(15, a.partyId().value());
            statement.setString(16, a.status().name());
            statement.setLong(17, a.version());
            statement.setString(18, transition);
            statement.setString(19, a.reason());
            statement.setTimestamp(20, Timestamp.from(a.updatedAt()));
            statement.setTimestamp(21, Timestamp.from(a.updatedAt()));
            statement.executeUpdate();
        }
    }

    private Optional<OrganizationPartyAssociation> queryOne(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("query Organization-Party association", e);
        }
    }

    private static OrganizationPartyAssociation read(ResultSet row) throws SQLException {
        Timestamp verifiedAt = row.getTimestamp("verified_at");
        Timestamp closedAt = row.getTimestamp("closed_at");
        return OrganizationPartyAssociation.rehydrate(
                row.getObject("association_id", UUID.class),
                OrganizationId.of(row.getObject("organization_id", UUID.class)),
                PartyId.of(row.getObject("party_id", UUID.class)),
                OrganizationPartyAssociationStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                verifiedAt == null ? null : verifiedAt.toInstant(),
                closedAt == null ? null : closedAt.toInstant(),
                row.getString("created_by"), row.getString("updated_by"),
                row.getString("evidence_reference"), row.getString("reason"),
                row.getLong("version"));
    }

    private static void bind(PreparedStatement statement, OrganizationPartyAssociation a) throws SQLException {
        statement.setObject(1, a.id());
        statement.setObject(2, a.organizationId().value());
        statement.setObject(3, a.partyId().value());
        statement.setString(4, a.status().name());
        statement.setTimestamp(5, Timestamp.from(a.createdAt()));
        statement.setTimestamp(6, Timestamp.from(a.updatedAt()));
        setInstant(statement, 7, a.verifiedAt());
        setInstant(statement, 8, a.closedAt());
        statement.setString(9, a.createdBy());
        statement.setString(10, a.updatedBy());
        statement.setString(11, a.evidenceReference());
        statement.setString(12, a.reason());
        statement.setLong(13, a.version());
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    private static boolean allowed(OrganizationPartyAssociationStatus from,
                                   OrganizationPartyAssociationStatus to) {
        return switch (from) {
            case PENDING_VERIFICATION -> to == OrganizationPartyAssociationStatus.ACTIVE
                    || to == OrganizationPartyAssociationStatus.REVOKED;
            case ACTIVE -> to == OrganizationPartyAssociationStatus.SUSPENDED
                    || to == OrganizationPartyAssociationStatus.SUPERSEDED
                    || to == OrganizationPartyAssociationStatus.REVOKED;
            case SUSPENDED -> to == OrganizationPartyAssociationStatus.ACTIVE
                    || to == OrganizationPartyAssociationStatus.SUPERSEDED
                    || to == OrganizationPartyAssociationStatus.REVOKED;
            case SUPERSEDED, REVOKED -> false;
        };
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Could not " + operation, cause);
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    public static final class AssociationConflictException extends IllegalStateException {
        public AssociationConflictException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class OptimisticAssociationConflictException extends IllegalStateException {
        public OptimisticAssociationConflictException(String message) { super(message); }
    }
}
