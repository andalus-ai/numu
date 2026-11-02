package tech.kayys.numou.ecosystem.persistence.jdbc;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.application.port.PartyParticipantAssociationRepository;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociationStatus;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantRole;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

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

/** PostgreSQL adapter. Every read and lifecycle update is tenant-scoped. */
@ApplicationScoped
public class JdbcPartyParticipantAssociationRepository implements PartyParticipantAssociationRepository {
    private static final String COLUMNS =
            "id, tenant_id, party_id, participant_id, role, status, effective_from, revoked_at";
    private final DataSource dataSource;

    @Inject
    public JdbcPartyParticipantAssociationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Uni<PartyParticipantAssociation> save(PartyParticipantAssociation association) {
        Objects.requireNonNull(association, "association");
        return Uni.createFrom().item(() -> saveBlocking(association))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<PartyParticipantAssociation>> findActive(
            TenantId tenantId, PartyId partyId, ParticipantId participantId, PartyParticipantRole role) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(role, "role");
        return queryOne("SELECT " + COLUMNS + " FROM ecosystem_party_participant_associations "
                        + "WHERE tenant_id = ? AND party_id = ? AND participant_id = ? AND role = ? AND status = 'ACTIVE'",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, partyId.value());
                    statement.setObject(3, participantId.value());
                    statement.setString(4, role.name());
                });
    }

    @Override
    public Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParty(TenantId tenantId, PartyId partyId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        return queryMany("SELECT " + COLUMNS + " FROM ecosystem_party_participant_associations "
                        + "WHERE tenant_id = ? AND party_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY effective_from DESC, id",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, partyId.value());
                });
    }

    @Override
    public Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParticipant(
            TenantId tenantId, ParticipantId participantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(participantId, "participantId");
        return queryMany("SELECT " + COLUMNS + " FROM ecosystem_party_participant_associations "
                        + "WHERE tenant_id = ? AND participant_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY role, effective_from DESC, id",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, participantId.value());
                });
    }

    @Override
    public Uni<Optional<PartyParticipantAssociation>> findByTenantAndId(TenantId tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(id, "id");
        return queryOne("SELECT " + COLUMNS + " FROM ecosystem_party_participant_associations "
                        + "WHERE tenant_id = ? AND id = ?",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, id);
                });
    }

    private PartyParticipantAssociation saveBlocking(PartyParticipantAssociation candidate) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PartyParticipantAssociation> current = findForUpdate(
                        connection, candidate.tenantId(), candidate.id());
                if (current.isEmpty()) {
                    insert(connection, candidate);
                } else {
                    persistTransition(connection, current.get(), candidate);
                }
                connection.commit();
                return candidate;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new IllegalStateException(
                            "An active Party-to-Participant role already exists in this tenant, or the association id is already used",
                            sql);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw persistenceFailure("save Party-to-Participant association", (SQLException) failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open Party-to-Participant association transaction", e);
        }
    }

    private Optional<PartyParticipantAssociation> findForUpdate(
            Connection connection, TenantId tenantId, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM ecosystem_party_participant_associations "
                        + "WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.<PartyParticipantAssociation>empty();
            }
        }
    }

    private void insert(Connection connection, PartyParticipantAssociation association) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ecosystem_party_participant_associations "
                        + "(id, tenant_id, party_id, participant_id, role, status, effective_from, revoked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            bind(statement, association);
            statement.executeUpdate();
        }
    }

    private void persistTransition(Connection connection, PartyParticipantAssociation current,
                                   PartyParticipantAssociation candidate) throws SQLException {
        if (!current.tenantId().equals(candidate.tenantId())
                || !current.partyId().equals(candidate.partyId())
                || !current.participantId().equals(candidate.participantId())
                || current.role() != candidate.role()
                || !current.effectiveFrom().equals(candidate.effectiveFrom())) {
            throw new IllegalArgumentException("Association identity and relationship fields are immutable");
        }
        if (current.equals(candidate)) return;
        if (current.status() == PartyParticipantAssociationStatus.REVOKED
                || candidate.status() != PartyParticipantAssociationStatus.REVOKED) {
            throw new IllegalStateException("Only ACTIVE to REVOKED is a valid persisted lifecycle transition");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ecosystem_party_participant_associations SET status = 'REVOKED', revoked_at = ? "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'")) {
            statement.setTimestamp(1, Timestamp.from(candidate.revokedAt()));
            statement.setObject(2, candidate.tenantId().value());
            statement.setObject(3, candidate.id());
            if (statement.executeUpdate() != 1)
                throw new IllegalStateException("Party-to-Participant association changed concurrently");
        }
    }

    private Uni<Optional<PartyParticipantAssociation>> queryOne(String sql, Binder binder) {
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? Optional.of(read(row)) : Optional.<PartyParticipantAssociation>empty();
                }
            } catch (SQLException e) {
                throw persistenceFailure("query Party-to-Participant association", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<List<PartyParticipantAssociation>> queryMany(String sql, Binder binder) {
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                List<PartyParticipantAssociation> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rows.add(read(result));
                }
                return List.copyOf(rows);
            } catch (SQLException e) {
                throw persistenceFailure("query Party-to-Participant associations", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static void bind(PreparedStatement statement, PartyParticipantAssociation a) throws SQLException {
        statement.setObject(1, a.id());
        statement.setObject(2, a.tenantId().value());
        statement.setObject(3, a.partyId().value());
        statement.setObject(4, a.participantId().value());
        statement.setString(5, a.role().name());
        statement.setString(6, a.status().name());
        statement.setTimestamp(7, Timestamp.from(a.effectiveFrom()));
        statement.setTimestamp(8, a.revokedAt() == null ? null : Timestamp.from(a.revokedAt()));
    }

    private static PartyParticipantAssociation read(ResultSet row) throws SQLException {
        Timestamp revokedAt = row.getTimestamp("revoked_at");
        return new PartyParticipantAssociation(
                row.getObject("id", UUID.class),
                TenantId.of(row.getObject("tenant_id", UUID.class)),
                PartyId.of(row.getObject("party_id", UUID.class)),
                ParticipantId.of(row.getObject("participant_id", UUID.class)),
                PartyParticipantRole.valueOf(row.getString("role")),
                PartyParticipantAssociationStatus.valueOf(row.getString("status")),
                row.getTimestamp("effective_from").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant());
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException e) { original.addSuppressed(e); }
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Could not " + operation, cause);
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}
