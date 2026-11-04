package tech.kayys.numou.socialgraph.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.spi.SocialGraphIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphOutboxEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSocialGraphUnitOfWorkPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void commitsConnectionIdempotencyAndOutboxAtomically() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId requester = PartyId.generate();
        PartyId recipient = PartyId.generate();
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        SocialConnection connection = SocialConnection.request(
                SocialConnectionId.generate(), tenant, requester, recipient, now);

        uow.execute(tx -> {
            tx.insertConnection(connection);
            tx.saveIdempotency(new SocialGraphIdempotencyRecord(tenant, "key-1",
                    "request:" + requester + ":" + recipient, connection.id(), now));
            tx.appendOutbox(new SocialGraphOutboxEvent(UUID.randomUUID(), tenant, requester, connection.id(),
                    0, "social-graph.connection.requested.v1", 1, now,
                    "{\"connectionId\":\"" + connection.id() + "\"}"));
            tx.appendAudit(new tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord(
                    UUID.randomUUID(), tenant, connection.id(), requester, null,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionStatus.PENDING,
                    "REQUEST", "key-1", now));
            return null;
        }).await().indefinitely();

        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id())).await().indefinitely()).isPresent();
        assertThat(uow.execute(tx -> tx.findIdempotency(tenant, "key-1")).await().indefinitely()).isPresent();
        assertThat(count(dataSource, "sg_outbox")).isEqualTo(1);
        assertThat(count(dataSource, "sg_connection_audit")).isEqualTo(1);
    }

    @Test
    void rollsBackConnectionIdempotencyAndOutboxTogetherWhenCallbackFails() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        SocialConnection connection = SocialConnection.request(SocialConnectionId.generate(), tenant,
                PartyId.generate(), PartyId.generate(), Instant.parse("2026-11-04T00:00:00Z"));

        assertThatThrownBy(() -> uow.execute(tx -> {
            tx.insertConnection(connection);
            tx.saveIdempotency(new SocialGraphIdempotencyRecord(tenant, "rollback-key",
                    "fingerprint", connection.id(), Instant.parse("2026-11-04T00:00:00Z")));
            tx.appendOutbox(new SocialGraphOutboxEvent(UUID.randomUUID(), tenant, connection.requesterPartyId(), connection.id(),
                    0, "social-graph.connection.requested.v1", 1,
                    Instant.parse("2026-11-04T00:00:00Z"), "{\"rollback\":true}"));
            tx.appendAudit(new tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord(
                    UUID.randomUUID(), tenant, connection.id(), connection.requesterPartyId(), null,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionStatus.PENDING,
                    "REQUEST", "rollback-key", Instant.parse("2026-11-04T00:00:00Z")));
            throw new IllegalStateException("simulated command failure");
        }).await().indefinitely()).isInstanceOf(IllegalStateException.class);

        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id())).await().indefinitely()).isEmpty();
        assertThat(uow.execute(tx -> tx.findIdempotency(tenant, "rollback-key")).await().indefinitely()).isEmpty();
        assertThat(count(dataSource, "sg_outbox")).isZero();
        assertThat(count(dataSource, "sg_connection_audit")).isZero();
    }

    @Test
    void uniqueIndexRejectsReverseDuplicateOpenConnection() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId first = PartyId.generate();
        PartyId second = PartyId.generate();
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        SocialConnection original = SocialConnection.request(SocialConnectionId.generate(), tenant, first, second, now);
        SocialConnection reverse = SocialConnection.request(SocialConnectionId.generate(), tenant, second, first, now);
        uow.execute(tx -> { tx.insertConnection(original); return null; }).await().indefinitely();

        assertThatThrownBy(() -> uow.execute(tx -> { tx.insertConnection(reverse); return null; }).await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(dataSource, "sg_connection")).isEqualTo(1);
    }

    @Test
    void optimisticVersionCheckRejectsStaleTransition() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId requester = PartyId.generate();
        PartyId recipient = PartyId.generate();
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        SocialConnection original = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now);
        uow.execute(tx -> { tx.insertConnection(original); return null; }).await().indefinitely();

        SocialConnection stored = uow.execute(tx -> tx.findConnection(tenant, original.id()).orElseThrow())
                .await().indefinitely();
        SocialConnection accepted = stored.accept(recipient, now.plusSeconds(1));
        uow.execute(tx -> { tx.updateConnection(accepted, stored.version()); return null; }).await().indefinitely();

        SocialConnection stale = original.cancel(requester, now.plusSeconds(2));
        assertThatThrownBy(() -> uow.execute(tx -> { tx.updateConnection(stale, original.version()); return null; })
                .await().indefinitely()).isInstanceOf(SocialGraphOptimisticConflictException.class);
    }

    @Test
    void auditRowsRejectUpdateAndDelete() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId actor = PartyId.generate();
        SocialConnection connection = SocialConnection.request(SocialConnectionId.generate(), tenant,
                actor, PartyId.generate(), Instant.parse("2026-11-04T00:00:00Z"));
        uow.execute(tx -> {
            tx.insertConnection(connection);
            tx.appendAudit(new tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord(
                    UUID.randomUUID(), tenant, connection.id(), actor, null,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionStatus.PENDING,
                    "REQUEST", "immutable-audit", Instant.parse("2026-11-04T00:00:00Z")));
            return null;
        }).await().indefinitely();

        assertThatThrownBy(() -> executeSql(dataSource,
                "UPDATE sg_connection_audit SET action = 'TAMPERED' WHERE idempotency_key = 'immutable-audit'"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> executeSql(dataSource,
                "DELETE FROM sg_connection_audit WHERE idempotency_key = 'immutable-audit'"))
                .isInstanceOf(Exception.class);
        assertThat(count(dataSource, "sg_connection_audit")).isEqualTo(1);
    }

    @Test
    void connectionLookupIsTenantScoped() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        SocialConnection connection = SocialConnection.request(SocialConnectionId.generate(), tenant,
                PartyId.generate(), PartyId.generate(), Instant.parse("2026-11-04T00:00:00Z"));
        uow.execute(tx -> { tx.insertConnection(connection); return null; }).await().indefinitely();

        assertThat(uow.execute(tx -> tx.findConnection(TenantId.generate(), connection.id())).await().indefinitely()).isEmpty();
        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id())).await().indefinitely()).isPresent();
    }

    @Test
    void canonicalPairOrderingMatchesPostgresUuidOrderingAndSupportsReverseLookup() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        // Java UUID.compareTo treats the high bit as signed; PostgreSQL UUID
        // ordering and canonical string ordering place 0x00 before 0x80.
        PartyId lower = new PartyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        PartyId higher = new PartyId(UUID.fromString("80000000-0000-0000-0000-000000000001"));
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        SocialConnection connection = SocialConnection.request(
                SocialConnectionId.generate(), tenant, lower, higher, now);

        uow.execute(tx -> { tx.insertConnection(connection); return null; }).await().indefinitely();

        assertThat(uow.execute(tx -> tx.findOpenConnection(tenant, higher, lower))
                .await().indefinitely()).isPresent();
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void migrate(DataSource dataSource) throws Exception {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sg_outbox, sg_command_idempotency, sg_connection_audit, sg_connection CASCADE");
        }
    }

    private void executeSql(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
