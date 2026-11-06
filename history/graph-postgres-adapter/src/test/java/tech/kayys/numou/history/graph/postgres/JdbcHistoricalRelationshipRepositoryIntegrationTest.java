package tech.kayys.numou.history.graph.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.chronology.domain.CalendarSystemId;
import tech.kayys.numou.history.chronology.domain.HistoricalDate;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;
import tech.kayys.numou.history.entities.domain.HistoricalCollectiveId;
import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalPersonId;
import tech.kayys.numou.history.entities.domain.HistoricalRelationship;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipKindCode;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipVocabulary;
import tech.kayys.numou.history.graph.spi.HistoricalRelationshipRepository;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalRelationshipRepositoryIntegrationTest {
    private static final String APP_USER = "history_app";
    private static final String APP_PASSWORD = "history_app_test_password";
    private static final HistoricalRelationshipVocabulary VOCABULARY =
            HistoricalRelationshipVocabulary.standard();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource appDataSource;
    private static DataSource adminDataSource;

    @BeforeAll
    static void migrateAndCreateRestrictedRole() throws Exception {
        adminDataSource = dataSource(POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(adminDataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection connection = adminDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD
                    + "' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT SELECT, INSERT ON historical_relationship_revision TO " + APP_USER);
        }
        appDataSource = dataSource(APP_USER, APP_PASSWORD);
    }

    @Test
    void tenantRowsAreIsolatedAndSharedCorpusIsVisible() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        HistoricalEntityReference subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        HistoricalEntityReference object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        HistoricalRelationship tenantRelation = relation(subject, object, "tenant A research");
        HistoricalRelationship otherTenantRelation = relation(subject, object, "tenant B research");

        var repository = repository();
        assertTrue(repository.saveTenantScoped(tenantA, tenantRelation, 0));
        assertTrue(repository.saveTenantScoped(tenantB, otherTenantRelation, 0));

        assertEquals(tenantRelation, repository.findVisibleTo(tenantA, tenantRelation.id()).orElseThrow());
        assertTrue(repository.findVisibleTo(tenantA, otherTenantRelation.id()).isEmpty());
        assertEquals(1, repository.findBySubject(tenantA, subject, 20).size());
        assertEquals(1, visibleRowsFor(tenantA));
        assertEquals(1, visibleRowsFor(tenantB));

        HistoricalRelationship shared = relation(subject, object, "public corpus");
        assertThrows(Exception.class, () -> insertShared(shared, appDataSource));
        insertShared(shared, adminDataSource);
        assertEquals(shared, repository.findVisibleTo(tenantA, shared.id()).orElseThrow());
        assertEquals(shared, repository.findVisibleTo(tenantB, shared.id()).orElseThrow());
    }

    @Test
    void revisionsAreAppendOnlyAndCompareAndAppendRejectsStaleWriters() {
        UUID tenant = UUID.randomUUID();
        HistoricalEntityReference subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        HistoricalEntityReference object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        HistoricalRelationship first = relation(subject, object, "first assertion");
        var repository = repository();

        assertTrue(repository.saveTenantScoped(tenant, first, 0));
        HistoricalRelationship second = first.revise(subject, object, first.kind(),
                first.validDuring(), "corrected description", first.updatedAt().plusSeconds(1), VOCABULARY);
        assertTrue(repository.saveTenantScoped(tenant, second, 1));
        assertFalse(repository.saveTenantScoped(tenant, second, 1));
        assertEquals(second, repository.findVisibleTo(tenant, first.id()).orElseThrow());
        assertThrows(Exception.class, () -> insertSkippedRevision(first.id().value()));

        assertThrows(Exception.class, () -> {
            try (Connection connection = adminDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE historical_relationship_revision SET description='tampered'");
            }
        });
    }

    @Test
    void relationshipRevisionAndProjectionOutboxCommitAndRollbackTogether() throws Exception {
        UUID tenant = UUID.randomUUID();
        HistoricalEntityReference subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        HistoricalEntityReference object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        HistoricalRelationship committed = relation(subject, object, "projection event");
        assertTrue(repository().saveTenantScoped(tenant, committed, 0));

        try (Connection connection = adminDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_type, schema_version, tenant_id, aggregate_revision,
                            payload->>'relationshipKind', payload->'subject'->>'kind'
                       FROM historical_relationship_projection_outbox
                      WHERE aggregate_id = ?
                     """)) {
            statement.setObject(1, committed.id().value());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("history.relationship.revision-appended", rows.getString(1));
                assertEquals(1, rows.getInt(2));
                assertEquals(tenant, rows.getObject(3, UUID.class));
                assertEquals(1, rows.getLong(4));
                assertEquals("founded", rows.getString(5));
                assertEquals("PERSON", rows.getString(6));
                assertFalse(rows.next(), "one outbox event must exist for one aggregate revision");
            }
        }

        HistoricalRelationship rolledBack = relation(subject, object, "must not escape rollback");
        try (Connection connection = adminDataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertShared(connection, rolledBack);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM historical_relationship_projection_outbox WHERE aggregate_id = ?")) {
                statement.setObject(1, rolledBack.id().value());
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    assertEquals(1, rows.getLong(1), "trigger writes the event in the same open transaction");
                }
            }
            connection.rollback();
        }
        try (Connection connection = adminDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM historical_relationship_projection_outbox WHERE aggregate_id = ?")) {
            statement.setObject(1, rolledBack.id().value());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getLong(1), "rollback must remove both revision and outbox event");
            }
        }
    }

    private static HistoricalRelationshipRepository repository() {
        return new JdbcHistoricalRelationshipRepository(appDataSource, VOCABULARY);
    }

    private static HistoricalRelationship relation(
            HistoricalEntityReference subject, HistoricalEntityReference object, String description) {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        var range = new HistoricalDateRange(
                HistoricalDate.year(-43, CalendarSystemId.of("julian")).asApproximate("c. 44 BCE"),
                HistoricalDate.year(-42, CalendarSystemId.of("julian")));
        return HistoricalRelationship.create(subject, object, HistoricalRelationshipKindCode.of("founded"),
                range, description, now, VOCABULARY);
    }

    private static DataSource dataSource(String user, String password) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(user);
        source.setPassword(password);
        return source;
    }

    private static long visibleRowsFor(UUID tenantId) throws Exception {
        try (Connection connection = appDataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement setTenant = connection.prepareStatement(
                    "SELECT set_config('numou.tenant_id', ?, true)")) {
                setTenant.setString(1, tenantId.toString());
                setTenant.execute();
            }
            try (Statement statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT count(*) FROM historical_relationship_revision WHERE tenant_id IS NOT NULL")) {
                rows.next();
                long count = rows.getLong(1);
                connection.commit();
                return count;
            }
        }
    }

    private static void insertSkippedRevision(UUID relationshipId) throws Exception {
        String sql = """
                INSERT INTO historical_relationship_revision (
                    relationship_id, revision, tenant_id, subject_kind, subject_id, object_kind, object_id,
                    relationship_kind, validity_calendar, valid_from_year, valid_from_month, valid_from_day,
                    valid_from_precision, valid_from_approximate, valid_from_expression,
                    valid_through_year, valid_through_month, valid_through_day, valid_through_precision,
                    valid_through_approximate, valid_through_expression, description, recorded_at, updated_at
                )
                SELECT relationship_id, 99, tenant_id, subject_kind, subject_id, object_kind, object_id,
                       relationship_kind, validity_calendar, valid_from_year, valid_from_month, valid_from_day,
                       valid_from_precision, valid_from_approximate, valid_from_expression,
                       valid_through_year, valid_through_month, valid_through_day, valid_through_precision,
                       valid_through_approximate, valid_through_expression, description, recorded_at, updated_at
                  FROM historical_relationship_revision
                 WHERE relationship_id = ? AND revision = 2
                """;
        try (Connection connection = adminDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, relationshipId);
            statement.executeUpdate();
        }
    }

    private static void insertShared(HistoricalRelationship relationship, DataSource source) throws Exception {
        try (Connection connection = source.getConnection()) {
            insertShared(connection, relationship);
        }
    }

    private static void insertShared(Connection connection, HistoricalRelationship relationship) throws Exception {
        String sql = """
                INSERT INTO historical_relationship_revision (
                    relationship_id, revision, tenant_id, subject_kind, subject_id, object_kind, object_id,
                    relationship_kind, validity_calendar, valid_from_year, valid_from_month, valid_from_day,
                    valid_from_precision, valid_from_approximate, valid_from_expression,
                    valid_through_year, valid_through_month, valid_through_day, valid_through_precision,
                    valid_through_approximate, valid_through_expression, description, recorded_at, updated_at
                ) VALUES (?, 1, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            var from = relationship.validDuring().from();
            var through = relationship.validDuring().through();
            statement.setObject(1, relationship.id().value());
            statement.setString(2, relationship.subject().kind().name());
            statement.setObject(3, relationship.subject().id());
            statement.setString(4, relationship.object().kind().name());
            statement.setObject(5, relationship.object().id());
            statement.setString(6, relationship.kind().value());
            statement.setString(7, relationship.validDuring().calendarSystem().value());
            bindDate(statement, 8, from);
            bindDate(statement, 14, through);
            statement.setString(20, relationship.description());
            statement.setTimestamp(21, java.sql.Timestamp.from(relationship.recordedAt()));
            statement.setTimestamp(22, java.sql.Timestamp.from(relationship.updatedAt()));
            statement.executeUpdate();
        }
    }

    private static void bindDate(PreparedStatement statement, int start, HistoricalDate date) throws Exception {
        statement.setLong(start, date.astronomicalYear());
        statement.setInt(start + 1, date.month());
        statement.setInt(start + 2, date.day());
        statement.setString(start + 3, date.precision().name());
        statement.setBoolean(start + 4, date.approximate());
        statement.setString(start + 5, date.originalExpression());
    }

}
