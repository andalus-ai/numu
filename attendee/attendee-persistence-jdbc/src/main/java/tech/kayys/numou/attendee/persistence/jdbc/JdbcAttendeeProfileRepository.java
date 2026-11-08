package tech.kayys.numou.attendee.persistence.jdbc;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.attendee.application.port.AttendeeProfileAlreadyExistsException;
import tech.kayys.numou.attendee.application.port.AttendeeProfileOptimisticLockException;
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

/** PostgreSQL JDBC adapter. All reads and writes include the tenant predicate. */
@ApplicationScoped
public class JdbcAttendeeProfileRepository implements AttendeeProfileRepository {

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
        return Uni.createFrom().item(() -> findByTenantAndIdBlocking(tenantId, profileId))
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
        return Uni.createFrom().item(() -> findByTenantPartyAndKindBlocking(
                        tenantId, partyId, profileKind.trim()))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private AttendeeProfile saveBlocking(AttendeeProfile profile) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<StoredProfile> stored = findStored(
                        connection, profile.tenantId(), profile.id(), true);
                if (stored.isEmpty()) {
                    if (profile.revision() != 0) {
                        throw new AttendeeProfileOptimisticLockException(
                                "A new attendee profile must start at revision zero");
                    }
                    insert(connection, profile);
                } else {
                    update(connection, stored.get(), profile);
                }
                connection.commit();
                return profile;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new AttendeeProfileAlreadyExistsException(
                            "An attendee profile with this tenant/Party/profile identity already exists", sql);
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw persistenceFailure("save attendee profile", (SQLException) failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("open attendee profile transaction", e);
        }
    }

    private Optional<AttendeeProfile> findByTenantAndIdBlocking(TenantId tenantId, UUID profileId) {
        String sql = selectColumns() + " WHERE tenant_id = ? AND profile_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, profileId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load attendee profile by tenant and ID", e);
        }
    }

    private Optional<AttendeeProfile> findByTenantPartyAndKindBlocking(
            TenantId tenantId, UUID partyId, String profileKind) {
        String sql = selectColumns()
                + " WHERE tenant_id = ? AND party_id = ? AND profile_kind = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, partyId);
            statement.setString(3, profileKind);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load attendee profile by tenant, Party and kind", e);
        }
    }

    private Optional<StoredProfile> findStored(
            Connection connection, TenantId tenantId, UUID profileId, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT party_id, profile_kind, created_at, updated_at, revision "
                + "FROM attendee_profile WHERE tenant_id = ? AND profile_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, profileId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredProfile(
                        rows.getObject("party_id", UUID.class),
                        rows.getString("profile_kind"),
                        rows.getTimestamp("created_at").toInstant(),
                        rows.getTimestamp("updated_at").toInstant(),
                        rows.getLong("revision")));
            }
        }
    }

    private void insert(Connection connection, AttendeeProfile profile) throws SQLException {
        String sql = "INSERT INTO attendee_profile (profile_id, tenant_id, party_id, profile_kind, "
                + "display_name, preferred_name, preferred_language, status, created_at, updated_at, revision) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, StoredProfile stored, AttendeeProfile profile)
            throws SQLException {
        if (!stored.partyId().equals(profile.partyId().value())
                || !stored.profileKind().equals(profile.profileKind())
                || !stored.createdAt().equals(profile.createdAt())) {
            throw new IllegalArgumentException(
                    "Attendee profile tenant, Party, profile kind and creation time are immutable");
        }
        if (profile.revision() != stored.revision() + 1) {
            throw new AttendeeProfileOptimisticLockException(
                    "Attendee profile revision is stale or does not advance by exactly one");
        }
        if (profile.updatedAt().isBefore(stored.updatedAt())) {
            throw new IllegalArgumentException("Attendee profile updatedAt cannot move backwards");
        }

        String sql = "UPDATE attendee_profile SET display_name = ?, preferred_name = ?, "
                + "preferred_language = ?, status = ?, updated_at = ?, revision = ? "
                + "WHERE tenant_id = ? AND profile_id = ? AND revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.displayName());
            statement.setString(2, profile.preferredName());
            statement.setString(3, profile.preferredLanguage());
            statement.setString(4, profile.status().name());
            statement.setTimestamp(5, Timestamp.from(profile.updatedAt()));
            statement.setLong(6, profile.revision());
            statement.setObject(7, profile.tenantId().value());
            statement.setObject(8, profile.id());
            statement.setLong(9, stored.revision());
            if (statement.executeUpdate() != 1) {
                throw new AttendeeProfileOptimisticLockException(
                        "Attendee profile changed concurrently; reload before saving");
            }
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

    private static String selectColumns() {
        return "SELECT profile_id, tenant_id, party_id, profile_kind, display_name, preferred_name, "
                + "preferred_language, status, created_at, updated_at, revision FROM attendee_profile";
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

    private record StoredProfile(
            UUID partyId, String profileKind, java.time.Instant createdAt,
            java.time.Instant updatedAt, long revision) {
    }
}
