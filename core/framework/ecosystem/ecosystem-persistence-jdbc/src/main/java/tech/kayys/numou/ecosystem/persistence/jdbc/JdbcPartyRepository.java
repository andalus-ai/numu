package tech.kayys.numou.ecosystem.persistence.jdbc;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.application.port.PartyRepository;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC persistence adapter for canonical Party identity.
 *
 * <p>Party identity is global and is deliberately not tenant-owned. JDBC work
 * runs on Mutiny's worker pool. Lifecycle writes use optimistic version checks
 * so concurrent deactivation/reactivation cannot silently overwrite each other.
 */
@ApplicationScoped
public class JdbcPartyRepository implements PartyRepository {

    private final DataSource dataSource;

    @Inject
    public JdbcPartyRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public Uni<Party> save(Party party) {
        Objects.requireNonNull(party, "party cannot be null");
        return Uni.createFrom().item(() -> saveBlocking(party))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<Party>> findById(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        return Uni.createFrom().item(() -> findBlocking(partyId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Party saveBlocking(Party party) {
        UUID id = party.getId().value();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<StoredParty> stored = findStored(connection, id, true);
                if (stored.isEmpty()) {
                    if (party.getVersion() != 0) {
                        throw new OptimisticPartyConflictException(
                                "A new Party must start at version zero");
                    }
                    insert(connection, party);
                } else {
                    updateLifecycle(connection, stored.get(), party);
                }
                connection.commit();
                return party;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new IllegalStateException("Party identifier already exists", sql);
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw persistenceFailure("save Party", (SQLException) failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open Party transaction", e);
        }
    }

    private Optional<Party> findBlocking(PartyId partyId) {
        String sql = "SELECT party_id, kind, status, created_at, updated_at, version "
                + "FROM ecosystem_party WHERE party_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(row));
            }
        } catch (SQLException e) {
            throw persistenceFailure("load Party", e);
        }
    }

    private Optional<StoredParty> findStored(Connection connection, UUID id, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT kind, status, version FROM ecosystem_party WHERE party_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredParty(
                        PartyKind.valueOf(row.getString("kind")),
                        PartyStatus.valueOf(row.getString("status")),
                        row.getInt("version")));
            }
        }
    }

    private void insert(Connection connection, Party party) throws SQLException {
        String sql = "INSERT INTO ecosystem_party "
                + "(party_id, kind, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, party.getId().value());
            statement.setString(2, party.kind().name());
            statement.setString(3, party.status().name());
            statement.setTimestamp(4, Timestamp.from(party.getCreatedAt()));
            statement.setTimestamp(5, Timestamp.from(party.getUpdatedAt()));
            statement.setInt(6, party.getVersion());
            statement.executeUpdate();
        }
    }

    private void updateLifecycle(Connection connection, StoredParty stored, Party candidate)
            throws SQLException {
        if (stored.kind() != candidate.kind()) {
            throw new IllegalArgumentException("Party kind is immutable after registration");
        }
        int expectedVersion = candidate.getVersion() - 1;
        if (expectedVersion < 0 || stored.version() != expectedVersion) {
            throw new OptimisticPartyConflictException(
                    "Party changed concurrently or the candidate has no new lifecycle transition");
        }
        String sql = "UPDATE ecosystem_party SET status = ?, updated_at = ?, version = ? "
                + "WHERE party_id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candidate.status().name());
            statement.setTimestamp(2, Timestamp.from(candidate.getUpdatedAt()));
            statement.setInt(3, candidate.getVersion());
            statement.setObject(4, candidate.getId().value());
            statement.setInt(5, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticPartyConflictException(
                        "Party changed concurrently; reload before saving");
            }
        }
    }

    private Party read(ResultSet row) throws SQLException {
        Party party = Party.rehydrate(
                PartyId.of(row.getObject("party_id", UUID.class)),
                PartyKind.valueOf(row.getString("kind")),
                PartyStatus.valueOf(row.getString("status")));
        party.setCreatedAt(row.getTimestamp("created_at").toInstant());
        party.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        party.setVersion(row.getInt("version"));
        return party;
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

    private record StoredParty(PartyKind kind, PartyStatus status, int version) {}

    public static final class OptimisticPartyConflictException extends RuntimeException {
        public OptimisticPartyConflictException(String message) {
            super(message);
        }
    }
}
