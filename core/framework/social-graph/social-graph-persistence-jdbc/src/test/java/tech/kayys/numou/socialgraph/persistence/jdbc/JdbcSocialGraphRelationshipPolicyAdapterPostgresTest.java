package tech.kayys.numou.socialgraph.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSocialGraphRelationshipPolicyAdapterPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void blockIsEnforcedInBothDirectionsAndIsTenantScoped() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var adapter = new JdbcSocialGraphRelationshipPolicyAdapter(dataSource);
        TenantId tenant = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        PartyId blocker = PartyId.generate();
        PartyId blocked = PartyId.generate();
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        insert(dataSource, SocialRelationship.create(SocialRelationshipId.generate(), tenant,
                SocialRelationshipType.BLOCK, blocker, blocked, now.minusSeconds(60),
                now.minusSeconds(60), null, "manual", Map.of()));

        assertThat(adapter.isBlocked(tenant, blocker, blocked, now).await().indefinitely()).isTrue();
        assertThat(adapter.isBlocked(tenant, blocked, blocker, now).await().indefinitely()).isTrue();
        assertThat(adapter.isBlocked(otherTenant, blocker, blocked, now).await().indefinitely()).isFalse();
        assertThat(adapter.isBlocked(tenant, blocker, blocker, now).await().indefinitely()).isFalse();
    }

    @Test
    void muteIsViewerOwnedAndHonorsEffectiveWindow() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        var adapter = new JdbcSocialGraphRelationshipPolicyAdapter(dataSource);
        TenantId tenant = TenantId.generate();
        PartyId viewer = PartyId.generate();
        PartyId author = PartyId.generate();
        Instant start = Instant.parse("2026-11-10T00:00:00Z");
        Instant end = start.plusSeconds(300);
        insert(dataSource, SocialRelationship.create(SocialRelationshipId.generate(), tenant,
                SocialRelationshipType.MUTE, viewer, author, start, start, end, "manual", Map.of()));

        assertThat(adapter.isMuted(tenant, viewer, author, start).await().indefinitely()).isTrue();
        assertThat(adapter.isMuted(tenant, author, viewer, start).await().indefinitely()).isFalse();
        assertThat(adapter.isMuted(tenant, viewer, author, end).await().indefinitely()).isFalse();
        assertThat(adapter.isMuted(tenant, viewer, author, start.minusSeconds(1)).await().indefinitely()).isFalse();
    }

    private static void insert(DataSource dataSource, SocialRelationship relationship) throws Exception {
        String sql = """
                INSERT INTO sg_relationship
                (tenant_id, relationship_id, relationship_type, source_party_id, target_party_id,
                 status, created_at, updated_at, valid_from, valid_until, removed_at, provenance, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, NULL, ?, 0)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, relationship.tenantId().value());
            ps.setObject(2, relationship.id().value());
            ps.setString(3, relationship.type().name());
            ps.setObject(4, relationship.sourcePartyId().value());
            ps.setObject(5, relationship.targetPartyId().value());
            ps.setTimestamp(6, Timestamp.from(relationship.createdAt()));
            ps.setTimestamp(7, Timestamp.from(relationship.updatedAt()));
            ps.setTimestamp(8, Timestamp.from(relationship.validFrom()));
            ps.setTimestamp(9, relationship.validUntil() == null ? null : Timestamp.from(relationship.validUntil()));
            ps.setString(10, relationship.provenance());
            ps.executeUpdate();
        }
    }

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
}
