package tech.kayys.numou.socialgraph.persistence.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** PostgreSQL-authoritative policy adapter; never reads a potentially stale graph projection. */
@ApplicationScoped
public class JdbcSocialGraphRelationshipPolicyAdapter implements SocialGraphRelationshipPolicyPort {
    private static final String BLOCK_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM sg_relationship
                 WHERE tenant_id = ?
                   AND relationship_type = 'BLOCK'
                   AND status = 'ACTIVE'
                   AND valid_from <= ?
                   AND (valid_until IS NULL OR valid_until > ?)
                   AND ((source_party_id = ? AND target_party_id = ?)
                     OR (source_party_id = ? AND target_party_id = ?))
            )
            """;
    private static final String MUTE_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM sg_relationship
                 WHERE tenant_id = ?
                   AND relationship_type = 'MUTE'
                   AND source_party_id = ?
                   AND target_party_id = ?
                   AND status = 'ACTIVE'
                   AND valid_from <= ?
                   AND (valid_until IS NULL OR valid_until > ?)
            )
            """;

    private final DataSource dataSource;

    @Inject
    public JdbcSocialGraphRelationshipPolicyAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public Uni<Boolean> isBlocked(TenantId tenantId, PartyId first, PartyId second, Instant at) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(first, "first cannot be null");
        Objects.requireNonNull(second, "second cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (first.equals(second)) return Uni.createFrom().item(false);
        return Uni.createFrom().item(() -> exists(BLOCK_SQL, ps -> {
            ps.setObject(1, tenantId.value());
            ps.setTimestamp(2, Timestamp.from(at));
            ps.setTimestamp(3, Timestamp.from(at));
            ps.setObject(4, first.value());
            ps.setObject(5, second.value());
            ps.setObject(6, second.value());
            ps.setObject(7, first.value());
        })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Boolean> isMuted(TenantId tenantId, PartyId viewer, PartyId author, Instant at) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(viewer, "viewer cannot be null");
        Objects.requireNonNull(author, "author cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (viewer.equals(author)) return Uni.createFrom().item(false);
        return Uni.createFrom().item(() -> exists(MUTE_SQL, ps -> {
            ps.setObject(1, tenantId.value());
            ps.setObject(2, viewer.value());
            ps.setObject(3, author.value());
            ps.setTimestamp(4, Timestamp.from(at));
            ps.setTimestamp(5, Timestamp.from(at));
        })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private boolean exists(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Policy query returned no result row");
                return rs.getBoolean(1);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to evaluate Social Graph relationship policy", failure);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
