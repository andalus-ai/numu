package tech.kayys.numou.socialgraph.persistence.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipListEntry;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPage;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPort;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL keyset query adapter; tenant scope is mandatory in every statement. */
@ApplicationScoped
public class JdbcSocialGraphRelationshipQueryAdapter implements SocialGraphRelationshipQueryPort {
    private static final int MAX_PAGE_SIZE = 100;
    private final DataSource dataSource;

    @Inject
    public JdbcSocialGraphRelationshipQueryAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public Uni<SocialGraphRelationshipQueryPage> findFollowing(
            TenantId tenantId, PartyId follower, Instant at, String cursor, int limit) {
        return query(tenantId, follower, at, cursor, limit, true);
    }

    @Override
    public Uni<SocialGraphRelationshipQueryPage> findFollowers(
            TenantId tenantId, PartyId followed, Instant at, String cursor, int limit) {
        return query(tenantId, followed, at, cursor, limit, false);
    }

    private Uni<SocialGraphRelationshipQueryPage> query(
            TenantId tenantId, PartyId party, Instant at, String cursor, int limit, boolean outgoing) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(party, "party cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("limit must be between 1 and 100");
        Cursor decoded = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor, party.value(), outgoing);
        return Uni.createFrom().item(() -> queryBlocking(tenantId, party, at, decoded, limit, outgoing))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private SocialGraphRelationshipQueryPage queryBlocking(
            TenantId tenantId, PartyId party, Instant at, Cursor cursor, int limit, boolean outgoing) {
        String endpoint = outgoing ? "source_party_id" : "target_party_id";
        String counterpart = outgoing ? "target_party_id" : "source_party_id";
        String sql = """
                SELECT relationship_id, %s AS party_id, created_at
                  FROM sg_relationship
                 WHERE tenant_id = ?
                   AND %s = ?
                   AND relationship_type = 'FOLLOW'
                   AND status = 'ACTIVE'
                   AND valid_from <= ?
                   AND (valid_until IS NULL OR valid_until > ?)
                   AND (?::timestamptz IS NULL OR (created_at, relationship_id) < (?, ?))
                 ORDER BY created_at DESC, relationship_id DESC
                 LIMIT ?
                """.formatted(counterpart, endpoint);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId.value());
            ps.setObject(2, party.value());
            ps.setTimestamp(3, Timestamp.from(at));
            ps.setTimestamp(4, Timestamp.from(at));
            ps.setObject(5, cursor == null ? null : cursor.createdAt());
            ps.setTimestamp(6, cursor == null ? null : Timestamp.from(cursor.createdAt()));
            ps.setObject(7, cursor == null ? null : cursor.relationshipId());
            ps.setInt(8, limit + 1);
            List<SocialGraphRelationshipListEntry> rows = new ArrayList<>(limit + 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new SocialGraphRelationshipListEntry(
                        rs.getObject("relationship_id", UUID.class),
                        new PartyId(rs.getObject("party_id", UUID.class)),
                        rs.getTimestamp("created_at").toInstant()));
            }
            boolean hasMore = rows.size() > limit;
            List<SocialGraphRelationshipListEntry> page = hasMore
                    ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
            String next = hasMore ? Cursor.encode(page.get(page.size() - 1), party.value(), outgoing) : null;
            return new SocialGraphRelationshipQueryPage(page, next);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to query Social Graph relationships", failure);
        }
    }

    private record Cursor(Instant createdAt, UUID relationshipId) {
        private static String encode(SocialGraphRelationshipListEntry entry, UUID owner, boolean outgoing) {
            String raw = owner + "|" + (outgoing ? "OUT" : "IN") + "|" + entry.createdAt() + "|" + entry.relationshipId();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        private static Cursor decode(String encoded, UUID expectedOwner, boolean expectedOutgoing) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 4
                        || !UUID.fromString(parts[0]).equals(expectedOwner)
                        || !parts[1].equals(expectedOutgoing ? "OUT" : "IN")) {
                    throw new IllegalArgumentException("Cursor does not belong to this relationship list");
                }
                return new Cursor(Instant.parse(parts[2]), UUID.fromString(parts[3]));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid relationship cursor", invalid);
            }
        }
    }
}
