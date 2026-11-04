package tech.kayys.numou.organization.adapter.jdbc;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.domain.OrganizationMembershipStatus;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for tenant-owned organization memberships.
 *
 * <p>All blocking JDBC work is dispatched to the supplied bounded executor.
 * Tenant-scoped writes use row locking and aggregate versions; uniqueness and
 * ownership constraints are additionally enforced by PostgreSQL.</p>
 */
public final class JdbcOrganizationMembershipRepository implements OrganizationMembershipRepository {
    private static final String COLUMNS = """
            membership_id, tenant_id, organization_id, user_id, status, role,
            version, created_at, updated_at
            """;

    private final DataSource dataSource;
    private final Executor jdbcExecutor;

    public JdbcOrganizationMembershipRepository(DataSource dataSource, Executor jdbcExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcExecutor = Objects.requireNonNull(jdbcExecutor, "jdbcExecutor");
    }

    @Override
    @Deprecated(forRemoval = false)
    public CompletionStage<OrganizationMembership> save(OrganizationMembership membership) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(
                "TenantId is required to persist an organization membership"));
    }

    @Override
    public CompletionStage<OrganizationMembership> save(OrganizationMembership membership, TenantId tenantId) {
        Objects.requireNonNull(membership, "membership");
        Objects.requireNonNull(tenantId, "tenantId");
        return CompletableFuture.supplyAsync(() -> saveBlocking(membership, tenantId), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findById(OrganizationMembershipId id) {
        Objects.requireNonNull(id, "id");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_memberships WHERE membership_id = ?",
                statement -> statement.setObject(1, id.value())), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByTenantAndId(
            TenantId tenantId, OrganizationMembershipId membershipId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_memberships WHERE tenant_id = ? AND membership_id = ?",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, membershipId.value());
                }), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(userId, "userId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_memberships WHERE organization_id = ? AND user_id = ?",
                statement -> {
                    statement.setObject(1, organizationId.value());
                    statement.setObject(2, userId.value());
                }), jdbcExecutor);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByTenantAndOrganizationAndUser(
            TenantId tenantId, OrganizationId organizationId, UserId userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(userId, "userId");
        return CompletableFuture.supplyAsync(() -> queryOne(
                "SELECT " + COLUMNS + " FROM organization_memberships "
                        + "WHERE tenant_id = ? AND organization_id = ? AND user_id = ?",
                statement -> {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, organizationId.value());
                    statement.setObject(3, userId.value());
                }), jdbcExecutor);
    }

    @Override
    public CompletionStage<Boolean> existsById(OrganizationMembershipId id) {
        Objects.requireNonNull(id, "id");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM organization_memberships WHERE membership_id = ?")) {
                statement.setObject(1, id.value());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            } catch (SQLException e) {
                throw persistenceFailure("check organization membership existence", e);
            }
        }, jdbcExecutor);
    }

    @Override
    public CompletionStage<Void> delete(OrganizationMembership membership) {
        Objects.requireNonNull(membership, "membership");
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Unscoped membership deletion is not supported; use a tenant-scoped lifecycle transition"));
    }

    @Override
    public CompletionStage<Void> deleteById(OrganizationMembershipId id) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Unscoped membership deletion is not supported; use a tenant-scoped lifecycle transition"));
    }

    private OrganizationMembership saveBlocking(OrganizationMembership candidate, TenantId tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<OrganizationMembership> current = findForUpdate(connection, tenantId, candidate.getId());
                if (current.isEmpty()) {
                    if (candidate.getVersion() != 0) {
                        throw new OptimisticMembershipConflictException(
                                "A new membership must start at version zero");
                    }
                    insert(connection, candidate, tenantId);
                } else {
                    OrganizationMembership previous = current.get();
                    if (!previous.organizationId().equals(candidate.organizationId())
                            || !previous.userId().equals(candidate.userId())
                            || !previous.getCreatedAt().equals(candidate.getCreatedAt())) {
                        throw new IllegalArgumentException(
                                "Membership identity and creation timestamp are immutable");
                    }
                    if (sameState(previous, candidate)) {
                        connection.commit();
                        return previous;
                    }
                    if (candidate.getVersion() != previous.getVersion() + 1) {
                        throw new OptimisticMembershipConflictException(
                                "Membership version changed; reload before applying the transition");
                    }
                    update(connection, candidate, tenantId, previous.getVersion());
                }
                connection.commit();
                return candidate;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new MembershipConflictException(
                            "Membership already exists or violates the organization owner constraint", sql);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw persistenceFailure("save organization membership", (SQLException) failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open organization membership transaction", e);
        }
    }

    private Optional<OrganizationMembership> findForUpdate(
            Connection connection, TenantId tenantId, OrganizationMembershipId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM organization_memberships "
                        + "WHERE tenant_id = ? AND membership_id = ? FOR UPDATE")) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, OrganizationMembership membership, TenantId tenantId)
            throws SQLException {
        String sql = """
                INSERT INTO organization_memberships
                    (membership_id, tenant_id, organization_id, user_id, status, role,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, membership.getId().value());
            statement.setObject(2, tenantId.value());
            statement.setObject(3, membership.organizationId().value());
            statement.setObject(4, membership.userId().value());
            statement.setString(5, membership.status().name());
            statement.setString(6, membership.role().name());
            statement.setLong(7, membership.getVersion());
            statement.setTimestamp(8, Timestamp.from(membership.getCreatedAt()));
            statement.setTimestamp(9, Timestamp.from(membership.getUpdatedAt()));
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, OrganizationMembership membership, TenantId tenantId,
                        int expectedVersion) throws SQLException {
        String sql = """
                UPDATE organization_memberships
                SET status = ?, role = ?, version = ?, updated_at = ?
                WHERE tenant_id = ? AND membership_id = ? AND version = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, membership.status().name());
            statement.setString(2, membership.role().name());
            statement.setLong(3, membership.getVersion());
            statement.setTimestamp(4, Timestamp.from(membership.getUpdatedAt()));
            statement.setObject(5, tenantId.value());
            statement.setObject(6, membership.getId().value());
            statement.setInt(7, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticMembershipConflictException("Membership was modified concurrently");
            }
        }
    }

    private Optional<OrganizationMembership> queryOne(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("query organization membership", e);
        }
    }

    private static OrganizationMembership read(ResultSet row) throws SQLException {
        OrganizationMembership membership = OrganizationMembership.restore(
                OrganizationMembershipId.of(row.getObject("membership_id", UUID.class)),
                OrganizationId.of(row.getObject("organization_id", UUID.class)),
                new UserId(row.getObject("user_id", UUID.class)),
                OrganizationMembershipStatus.valueOf(row.getString("status")),
                OrganizationMembership.Role.valueOf(row.getString("role")));
        membership.setCreatedAt(row.getTimestamp("created_at").toInstant());
        membership.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        membership.setVersion(row.getInt("version"));
        return membership;
    }

    private static boolean sameState(OrganizationMembership left, OrganizationMembership right) {
        return left.getVersion() == right.getVersion()
                && left.status() == right.status()
                && left.role() == right.role()
                && left.getUpdatedAt().truncatedTo(ChronoUnit.MICROS)
                        .equals(right.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
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

    public static final class MembershipConflictException extends IllegalStateException {
        public MembershipConflictException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class OptimisticMembershipConflictException extends IllegalStateException {
        public OptimisticMembershipConflictException(String message) { super(message); }
    }
}
