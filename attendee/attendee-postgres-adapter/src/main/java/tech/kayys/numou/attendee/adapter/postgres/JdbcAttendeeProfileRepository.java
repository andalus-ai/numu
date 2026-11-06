package tech.kayys.numou.attendee.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.postgresql.util.PSQLException;
import tech.kayys.numou.attendee.application.port.AttendeeProfileRepository;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.attendee.domain.AttendeeProfileStatus;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

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
 * PostgreSQL adapter. Every read predicate is tenant-scoped and every update
 * uses the expected previous aggregate revision.
 */
@ApplicationScoped
public class JdbcAttendeeProfileRepository implements AttendeeProfileRepository {

    private static final String SELECT_COLUMNS = """
            profile_id, tenant_id, party_id, profile_kind, display_name,
            preferred_name, preferred_language, status, created_at, updated_at, revision
            """;

    private final DataSource dataSource;

    @Inject
    public JdbcAttendeeProfileRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public Uni<AttendeeProfile> save(AttendeeProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        return Uni.createFrom().item(() -> saveBlocking(profile))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<AttendeeProfile>> findByTenantAndId(TenantId tenantId, UUID profileId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(profileId, "profileId cannot be null");
        return Uni.createFrom().item(() -> findOne(
                        "SELECT " + SELECT_COLUMNS + " FROM attendee_profile "
                                + "WHERE tenant_id = ? AND profile_id = ?",
                        statement -> {
                            statement.setObject(1, tenantId.value());
                            statement.setObject(2, profileId);
                        }))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<AttendeeProfile>> findByTenantPartyAndKind(
            TenantId tenantId, UUID partyId, String profileKind) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        if (profileKind == null || profileKind.isBlank()) {
            throw new IllegalArgumentException("profileKind is required");
        }
        return Uni.createFrom().item(() -> findOne(
                        "SELECT " + SELECT_COLUMNS + " FROM attendee_profile "
                                + "WHERE tenant_id = ? AND party_id = ? AND profile_kind = ?",
                        statement -> {
                            statement.setObject(1, tenantId.value());
                            statement.setObject(2, partyId);
                            statement.setString(3, profileKind.trim());
                        }))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private AttendeeProfile saveBlocking(AttendeeProfile profile) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (profile.revision() == 0) {
                    insert(connection, profile);
                } else {
                    update(connection, profile);
                }
                connection.commit();
                return profile;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql) {
                    if (isProfileUniqueViolation(sql)) {
                        throw new AttendeeProfileAlreadyExistsException(
                                "An attendee profile already exists for this Party and profile kind", sql);
                    }
                    if ("23503".equals(sql.getSQLState())) {
                        throw new IllegalArgumentException(
                                "The referenced canonical Party does not exist", sql);
                    }
                    throw persistenceFailure("save attendee profile", sql);
                }
                throw failure;
            }
        } catch (SQLException e) {
            throw persistenceFailure("open attendee profile transaction", e);
        }
    }

    private void insert(Connection connection, AttendeeProfile profile) throws SQLException {
        String sql = """
                INSERT INTO attendee_profile (
                    profile_id, tenant_id, party_id, profile_kind, display_name,
                    preferred_name, preferred_language, status, created_at, updated_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, profile);
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, AttendeeProfile profile) throws SQLException {
        long expectedRevision = profile.revision() - 1;
        String sql = """
                UPDATE attendee_profile
                   SET display_name = ?, preferred_name = ?, preferred_language = ?,
                       status = ?, updated_at = ?, revision = ?
                 WHERE tenant_id = ? AND profile_id = ? AND revision = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.displayName());
            statement.setString(2, profile.preferredName());
            statement.setString(3, profile.preferredLanguage());
            statement.setString(4, profile.status().name());
            statement.setTimestamp(5, Timestamp.from(profile.updatedAt()));
            statement.setLong(6, profile.revision());
            statement.setObject(7, profile.tenantId().value());
            statement.setObject(8, profile.id());
            statement.setLong(9, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new AttendeeProfileOptimisticLockException(
                        "Attendee profile changed concurrently or does not exist in this tenant");
            }
        }
    }

    private void bind(PreparedStatement statement, AttendeeProfile profile) throws SQLException {
        statement.setObject(1, profile.id());
        statement.setObject(2, profile.tenantId().value());
        statement.setObject(3, profile.partyId().value());
        statement.setString(4, profile.profileKind());
        statement.setString(5, profile.displayName());
        statement.setString(6, profile.preferredName());
        statement.setString(7, profile.preferredLanguage());
        statement.setString(8, profile.status().name());
        statement.setTimestamp(9, Timestamp.from(profile.createdAt()));
        statement.setTimestamp(10, Timestamp.from(profile.updatedAt()));
        statement.setLong(11, profile.revision());
    }

    private Optional<AttendeeProfile> findOne(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load attendee profile", e);
        }
    }

    private AttendeeProfile read(ResultSet row) throws SQLException {
        return new AttendeeProfile(
                row.getObject("profile_id", UUID.class),
                TenantId.of(row.getObject("tenant_id", UUID.class)),
                PartyId.of(row.getObject("party_id", UUID.class)),
                row.getString("profile_kind"),
                row.getString("display_name"),
                row.getString("preferred_name"),
                row.getString("preferred_language"),
                AttendeeProfileStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getLong("revision"));
    }

    private static boolean isProfileUniqueViolation(SQLException failure) {
        if (!(failure instanceof PSQLException postgres) || !"23505".equals(postgres.getSQLState())) {
            return false;
        }
        var serverError = postgres.getServerErrorMessage();
        return serverError != null
                && "uq_attendee_profile_tenant_party_kind".equals(serverError.getConstraint());
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
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    public static final class AttendeeProfileAlreadyExistsException extends RuntimeException {
        public AttendeeProfileAlreadyExistsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AttendeeProfileOptimisticLockException extends RuntimeException {
        public AttendeeProfileOptimisticLockException(String message) {
            super(message);
        }
    }
}
