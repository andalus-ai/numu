package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.chronology.domain.CalendarSystemId;
import tech.kayys.numou.history.chronology.domain.DatePrecision;
import tech.kayys.numou.history.chronology.domain.HistoricalDate;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;
import tech.kayys.numou.history.entities.domain.HistoricalEntityKind;
import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalRelationship;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipId;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipKindCode;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipVocabulary;
import tech.kayys.numou.history.graph.spi.HistoricalRelationshipRepository;

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

/**
 * JDBC adapter for the append-only relationship revision table.
 *
 * <p>Every operation establishes the trusted tenant setting inside its own
 * transaction. The database RLS policy exposes shared rows plus that tenant's
 * rows and permits this adapter to insert only tenant-owned revisions.</p>
 */
public final class JdbcHistoricalRelationshipRepository implements HistoricalRelationshipRepository {
    private static final String CURRENT_RELATIONSHIP = """
            SELECT DISTINCT ON (relationship_id) *
              FROM historical_relationship_revision
             WHERE tenant_id IS NULL OR tenant_id = ?
             ORDER BY relationship_id, revision DESC
            """;

    private static final String INSERT_REVISION = """
            INSERT INTO historical_relationship_revision (
                relationship_id, revision, tenant_id,
                subject_kind, subject_id, object_kind, object_id, relationship_kind,
                validity_calendar,
                valid_from_year, valid_from_month, valid_from_day, valid_from_precision,
                valid_from_approximate, valid_from_expression,
                valid_through_year, valid_through_month, valid_through_day, valid_through_precision,
                valid_through_approximate, valid_through_expression,
                description, recorded_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?
            )
            """;

    private final DataSource dataSource;
    private final HistoricalRelationshipVocabulary vocabulary;

    public JdbcHistoricalRelationshipRepository(
            DataSource dataSource, HistoricalRelationshipVocabulary vocabulary) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    @Override
    public Optional<HistoricalRelationship> findVisibleTo(UUID tenantId, HistoricalRelationshipId id) {
        requireTenant(tenantId);
        Objects.requireNonNull(id, "relationship id is required");
        String sql = "SELECT * FROM (" + CURRENT_RELATIONSHIP + ") current_relationship WHERE relationship_id = ?";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, tenantId);
                Optional<HistoricalRelationship> result;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, tenantId);
                    statement.setObject(2, id.value());
                    try (ResultSet rows = statement.executeQuery()) {
                        result = rows.next() ? Optional.of(map(rows)) : Optional.empty();
                    }
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            throw persistenceFailure("find visible historical relationship", failure);
        }
    }

    @Override
    public List<HistoricalRelationship> findBySubject(
            UUID tenantId, HistoricalEntityReference subject, int limit) {
        return findByEndpoint(tenantId, Objects.requireNonNull(subject, "subject is required"), true, limit);
    }

    @Override
    public List<HistoricalRelationship> findByObject(
            UUID tenantId, HistoricalEntityReference object, int limit) {
        return findByEndpoint(tenantId, Objects.requireNonNull(object, "object is required"), false, limit);
    }

    private List<HistoricalRelationship> findByEndpoint(
            UUID tenantId, HistoricalEntityReference endpoint, boolean subject, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        String kindColumn = subject ? "subject_kind" : "object_kind";
        String idColumn = subject ? "subject_id" : "object_id";
        String sql = "SELECT * FROM (" + CURRENT_RELATIONSHIP + ") current_relationship "
                + "WHERE " + kindColumn + " = ? AND " + idColumn + " = ? "
                + "ORDER BY relationship_kind, subject_kind, subject_id, object_kind, object_id, relationship_id "
                + "LIMIT ?";
        List<HistoricalRelationship> relationships = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, tenantId);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, tenantId);
                    statement.setString(2, endpoint.kind().name());
                    statement.setObject(3, endpoint.id());
                    statement.setInt(4, limit);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) relationships.add(map(rows));
                    }
                }
                connection.commit();
                return List.copyOf(relationships);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            throw persistenceFailure("find historical relationships by endpoint", failure);
        }
    }

    @Override
    public boolean saveTenantScoped(UUID tenantId, HistoricalRelationship relationship, long expectedRevision) {
        requireTenant(tenantId);
        Objects.requireNonNull(relationship, "relationship is required");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision cannot be negative");
        if (relationship.revision() != Math.addExact(expectedRevision, 1)) {
            throw new IllegalArgumentException("relationship revision must equal expectedRevision + 1");
        }
        vocabulary.require(relationship.kind()).validateEndpoints(relationship.subject(), relationship.object());

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection, tenantId);
                lockRelationship(connection, relationship.id().value());
                long actualRevision = latestTenantRevision(connection, tenantId, relationship.id().value());
                if (actualRevision != expectedRevision) {
                    connection.commit();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(INSERT_REVISION)) {
                    bind(statement, tenantId, relationship);
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException failure) {
                rollback(connection, failure);
                if ("23505".equals(failure.getSQLState()) || "P0001".equals(failure.getSQLState())) return false;
                throw persistenceFailure("append historical relationship revision", failure);
            } catch (RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            throw persistenceFailure("append historical relationship revision", failure);
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private static void lockRelationship(Connection connection, UUID relationshipId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, relationshipId.toString());
            statement.execute();
        }
    }

    private static long latestTenantRevision(Connection connection, UUID tenantId, UUID relationshipId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(MAX(revision), 0)
                  FROM historical_relationship_revision
                 WHERE tenant_id = ? AND relationship_id = ?
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, relationshipId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void bind(
            PreparedStatement statement, UUID tenantId, HistoricalRelationship relationship) throws SQLException {
        int i = 1;
        statement.setObject(i++, relationship.id().value());
        statement.setLong(i++, relationship.revision());
        statement.setObject(i++, tenantId);
        statement.setString(i++, relationship.subject().kind().name());
        statement.setObject(i++, relationship.subject().id());
        statement.setString(i++, relationship.object().kind().name());
        statement.setObject(i++, relationship.object().id());
        statement.setString(i++, relationship.kind().value());

        HistoricalDateRange range = relationship.validDuring();
        statement.setString(i++, range == null ? null : range.calendarSystem().value());
        i = bindDate(statement, i, range == null ? null : range.from());
        i = bindDate(statement, i, range == null ? null : range.through());
        statement.setString(i++, relationship.description());
        statement.setTimestamp(i++, Timestamp.from(relationship.recordedAt()));
        statement.setTimestamp(i, Timestamp.from(relationship.updatedAt()));
    }

    private static int bindDate(PreparedStatement statement, int index, HistoricalDate date) throws SQLException {
        if (date == null) {
            statement.setNull(index++, java.sql.Types.BIGINT);
            statement.setNull(index++, java.sql.Types.SMALLINT);
            statement.setNull(index++, java.sql.Types.SMALLINT);
            statement.setNull(index++, java.sql.Types.VARCHAR);
            statement.setNull(index++, java.sql.Types.BOOLEAN);
            statement.setNull(index++, java.sql.Types.VARCHAR);
            return index;
        }
        statement.setLong(index++, date.astronomicalYear());
        statement.setInt(index++, date.month());
        statement.setInt(index++, date.day());
        statement.setString(index++, date.precision().name());
        statement.setBoolean(index++, date.approximate());
        statement.setString(index++, date.originalExpression());
        return index;
    }

    private HistoricalRelationship map(ResultSet rows) throws SQLException {
        HistoricalEntityReference subject = new HistoricalEntityReference(
                HistoricalEntityKind.valueOf(rows.getString("subject_kind")),
                rows.getObject("subject_id", UUID.class));
        HistoricalEntityReference object = new HistoricalEntityReference(
                HistoricalEntityKind.valueOf(rows.getString("object_kind")),
                rows.getObject("object_id", UUID.class));
        HistoricalRelationshipKindCode kind = HistoricalRelationshipKindCode.of(rows.getString("relationship_kind"));
        vocabulary.require(kind).validateEndpoints(subject, object);
        String calendar = rows.getString("validity_calendar");
        HistoricalDateRange range = calendar == null ? null : new HistoricalDateRange(
                readDate(rows, "valid_from", calendar), readDate(rows, "valid_through", calendar));
        return new HistoricalRelationship(
                new HistoricalRelationshipId(rows.getObject("relationship_id", UUID.class)),
                subject, object, kind, range, rows.getString("description"),
                rows.getLong("revision"), rows.getObject("recorded_at", java.time.OffsetDateTime.class).toInstant(),
                rows.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }

    private static HistoricalDate readDate(ResultSet rows, String prefix, String calendar) throws SQLException {
        return new HistoricalDate(
                rows.getLong(prefix + "_year"),
                rows.getInt(prefix + "_month"),
                rows.getInt(prefix + "_day"),
                DatePrecision.valueOf(rows.getString(prefix + "_precision")),
                CalendarSystemId.of(calendar),
                rows.getBoolean(prefix + "_approximate"),
                rows.getString(prefix + "_expression"));
    }

    private static void requireTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must come from the authenticated tenant context");
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException failure) {
        return new IllegalStateException("Unable to " + operation, failure);
    }
}
