package tech.kayys.numou.ecosystem.persistence.jdbc;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociationStatus;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC adapter for tenant-scoped Party identity associations.
 *
 * JDBC work is dispatched to Mutiny's worker pool; callers must provide a pooled
 * DataSource. Every lookup includes tenant_id, and the database's partial unique
 * index is the concurrency-safe authority for active subject ownership.
 */
@ApplicationScoped
public class JdbcPartyIdentityAssociationRepository implements PartyIdentityAssociationRepository {
    private static final String COLUMNS =
            "id, tenant_id, party_id, issuer, subject, status, linked_at, revoked_at";

    private final DataSource dataSource;

    @Inject
    public JdbcPartyIdentityAssociationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Uni<PartyIdentityAssociation> save(PartyIdentityAssociation association) {
        Objects.requireNonNull(association, "association");
        return Uni.createFrom().item(() -> saveBlocking(association))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
            TenantId tenantId, IdentitySubjectRef identity) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(identity, "identity");
        return Uni.createFrom().item(() -> {
            String sql = "SELECT " + COLUMNS + " FROM ecosystem_party_identity_associations "
                    + "WHERE tenant_id = ? AND issuer = ? AND subject = ? AND status = 'ACTIVE'";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setString(2, identity.issuer());
                statement.setString(3, identity.subject());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(read(result)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw persistenceFailure("find active Party identity association", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<List<PartyIdentityAssociation>> findByTenantAndParty(TenantId tenantId, PartyId partyId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        return Uni.createFrom().item(() -> {
            String sql = "SELECT " + COLUMNS + " FROM ecosystem_party_identity_associations "
                    + "WHERE tenant_id = ? AND party_id = ? ORDER BY linked_at DESC, id";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, partyId.value());
                List<PartyIdentityAssociation> associations = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        associations.add(read(result));
                    }
                }
                return List.copyOf(associations);
            } catch (SQLException e) {
                throw persistenceFailure("find Party identity association history", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private PartyIdentityAssociation saveBlocking(PartyIdentityAssociation candidate) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PartyIdentityAssociation> current = findByIdForUpdate(connection, candidate.tenantId(), candidate.id());
                if (current.isEmpty()) {
                    insert(connection, candidate);
                } else {
                    persistLifecycleTransition(connection, current.get(), candidate);
                }
                connection.commit();
                return candidate;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                if (e instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                    throw new IllegalStateException(
                            "An active identity subject is already associated within this tenant, or the association id is already used",
                            sqlException);
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw persistenceFailure("save Party identity association", (SQLException) e);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open transaction for Party identity association", e);
        }
    }

    private Optional<PartyIdentityAssociation> findByIdForUpdate(Connection connection, TenantId tenantId, UUID id)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM ecosystem_party_identity_associations "
                + "WHERE tenant_id = ? AND id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, PartyIdentityAssociation association) throws SQLException {
        String sql = "INSERT INTO ecosystem_party_identity_associations "
                + "(id, tenant_id, party_id, issuer, subject, status, linked_at, revoked_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, association);
            statement.executeUpdate();
        }
    }

    private void persistLifecycleTransition(Connection connection, PartyIdentityAssociation current,
                                             PartyIdentityAssociation candidate) throws SQLException {
        if (!current.tenantId().equals(candidate.tenantId())
                || !current.partyId().equals(candidate.partyId())
                || !current.identity().equals(candidate.identity())
                || !current.linkedAt().equals(candidate.linkedAt())) {
            throw new IllegalArgumentException("Association identity and ownership fields are immutable");
        }
        if (current.equals(candidate)) {
            return; // idempotent replay
        }
        if (current.status() == PartyIdentityAssociationStatus.REVOKED) {
            throw new IllegalStateException("A revoked Party identity association cannot be reactivated or rewritten");
        }
        if (candidate.status() != PartyIdentityAssociationStatus.REVOKED) {
            throw new IllegalArgumentException("An active association can only transition to REVOKED");
        }
        String sql = "UPDATE ecosystem_party_identity_associations SET status = 'REVOKED', revoked_at = ? "
                + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(candidate.revokedAt()));
            statement.setObject(2, candidate.id());
            statement.setObject(3, candidate.tenantId().value());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Party identity association changed concurrently");
            }
        }
    }

    private static void bind(PreparedStatement statement, PartyIdentityAssociation association) throws SQLException {
        statement.setObject(1, association.id());
        statement.setObject(2, association.tenantId().value());
        statement.setObject(3, association.partyId().value());
        statement.setString(4, association.identity().issuer());
        statement.setString(5, association.identity().subject());
        statement.setString(6, association.status().name());
        statement.setTimestamp(7, Timestamp.from(association.linkedAt()));
        statement.setTimestamp(8, association.revokedAt() == null ? null : Timestamp.from(association.revokedAt()));
    }

    private static PartyIdentityAssociation read(ResultSet result) throws SQLException {
        Timestamp revokedAt = result.getTimestamp(8);
        return new PartyIdentityAssociation(
                result.getObject(1, UUID.class),
                TenantId.of(result.getObject(2, UUID.class)),
                PartyId.of(result.getObject(3, UUID.class)),
                new IdentitySubjectRef(result.getString(4), result.getString(5)),
                PartyIdentityAssociationStatus.valueOf(result.getString(6)),
                result.getTimestamp(7).toInstant(),
                revokedAt == null ? null : revokedAt.toInstant());
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static RuntimeException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Could not " + operation, cause);
    }
}
