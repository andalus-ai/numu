package tech.kayys.numou.socialgraph.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSocialGraphRelationshipQueryAdapterPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void followingUsesStableKeysetPaginationAndNeverCrossesTenant() throws Exception {
        DataSource ds = dataSource();
        migrate(ds);
        var adapter = new JdbcSocialGraphRelationshipQueryAdapter(ds);
        TenantId tenant = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        PartyId owner = PartyId.generate();
        PartyId first = PartyId.generate();
        PartyId second = PartyId.generate();
        PartyId third = PartyId.generate();
        Instant base = Instant.parse("2026-11-10T10:00:00Z");
        insert(ds, tenant, owner, first, base.plusSeconds(3));
        insert(ds, tenant, owner, second, base.plusSeconds(2));
        insert(ds, tenant, owner, third, base.plusSeconds(1));
        insert(ds, otherTenant, owner, PartyId.generate(), base.plusSeconds(4));

        var page1 = adapter.findFollowing(tenant, owner, base.plusSeconds(10), null, 2).await().indefinitely();
        assertThat(page1.items()).extracting(x -> x.partyId()).containsExactly(first, second);
        assertThat(page1.nextCursor()).isNotBlank();

        var page2 = adapter.findFollowing(tenant, owner, base.plusSeconds(10), page1.nextCursor(), 2)
                .await().indefinitely();
        assertThat(page2.items()).extracting(x -> x.partyId()).containsExactly(third);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void cursorCannotBeReusedForAnotherOwnerOrDirection() throws Exception {
        DataSource ds = dataSource();
        migrate(ds);
        var adapter = new JdbcSocialGraphRelationshipQueryAdapter(ds);
        TenantId tenant = TenantId.generate();
        PartyId owner = PartyId.generate();
        Instant base = Instant.parse("2026-11-10T10:00:00Z");
        insert(ds, tenant, owner, PartyId.generate(), base.plusSeconds(3));
        insert(ds, tenant, owner, PartyId.generate(), base.plusSeconds(2));
        insert(ds, tenant, owner, PartyId.generate(), base.plusSeconds(1));
        var page = adapter.findFollowing(tenant, owner, base.plusSeconds(10), null, 2).await().indefinitely();

        assertThatThrownBy(() -> adapter.findFollowing(
                tenant, PartyId.generate(), base.plusSeconds(10), page.nextCursor(), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.findFollowing(
                TenantId.generate(), owner, base.plusSeconds(10), page.nextCursor(), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.findFollowers(
                tenant, owner, base.plusSeconds(10), page.nextCursor(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void insert(DataSource ds, TenantId tenant, PartyId source, PartyId target, Instant created)
            throws Exception {
        String sql = """
                INSERT INTO sg_relationship
                (tenant_id, relationship_id, relationship_type, source_party_id, target_party_id,
                 status, created_at, updated_at, valid_from, valid_until, removed_at, provenance, version)
                VALUES (?, ?, 'FOLLOW', ?, ?, 'ACTIVE', ?, ?, ?, NULL, NULL, 'test', 0)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tenant.value());
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, source.value());
            ps.setObject(4, target.value());
            ps.setTimestamp(5, Timestamp.from(created));
            ps.setTimestamp(6, Timestamp.from(created));
            ps.setTimestamp(7, Timestamp.from(created));
            ps.executeUpdate();
        }
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private static void migrate(DataSource ds) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }
}
