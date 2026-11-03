package tech.kayys.numou.socialgraph.persistence.jdbc;

import org.junit.jupiter.api.Test;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            return null;
        });

        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id()))).isPresent();
        assertThat(uow.execute(tx -> tx.findIdempotency(tenant, "key-1"))).isPresent();
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
            throw new IllegalStateException("simulated command failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id()))).isEmpty();
        assertThat(uow.execute(tx -> tx.findIdempotency(tenant, "rollback-key"))).isEmpty();
        assertThat(count(dataSource, "sg_outbox")).isZero();
        assertThat(count(dataSource, "sg_connection_audit")).isZero();
    }

    @Test
    void connectionLookupIsTenantScoped() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcSocialGraphUnitOfWork uow = new JdbcSocialGraphUnitOfWork(dataSource);
        TenantId tenant = TenantId.generate();
        SocialConnection connection = SocialConnection.request(SocialConnectionId.generate(), tenant,
                PartyId.generate(), PartyId.generate(), Instant.parse("2026-11-04T00:00:00Z"));
        uow.execute(tx -> { tx.insertConnection(connection); return null; });

        assertThat(uow.execute(tx -> tx.findConnection(TenantId.generate(), connection.id()))).isEmpty();
        assertThat(uow.execute(tx -> tx.findConnection(tenant, connection.id()))).isPresent();
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private void migrate(DataSource dataSource) throws Exception {
        String resource = "db/migration/V1__social_graph_connections.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
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
