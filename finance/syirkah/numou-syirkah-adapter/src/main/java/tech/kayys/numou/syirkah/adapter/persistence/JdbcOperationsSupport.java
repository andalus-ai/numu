package tech.kayys.numou.syirkah.adapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.ops.OutboxRecord;
import tech.kayys.numou.syirkah.application.port.OutboxPort;
import tech.kayys.numou.syirkah.application.port.SyirkahCatalogPort;
import tech.kayys.numou.syirkah.application.query.SyirkahSummary;
import tech.kayys.numou.syirkah.domain.SyirkahId;
import tech.kayys.numou.syirkah.domain.SyirkahStatus;
import tech.kayys.numou.syirkah.domain.SyirkahType;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Operations-side PostgreSQL ports: catalog listing (read model) and the outbox relay queries. */
public class JdbcOperationsSupport implements SyirkahCatalogPort, OutboxPort {

    static final String PAGE_SQL =
            "SELECT l.syirkah_id, l.syirkah_type, l.currency, l.status, l.version, l.created_at, l.updated_at,"
                    + " k.version AS contract_version, jsonb_array_length(k.contract -> 'partners') AS partner_count,"
                    + " (SELECT count(*) FROM syirkah_ledger_close c WHERE c.syirkah_id = l.syirkah_id"
                    + "   AND c.status = 'ACTIVE') AS close_count,"
                    + " (SELECT max(c.period_end) FROM syirkah_ledger_close c WHERE c.syirkah_id = l.syirkah_id"
                    + "   AND c.status = 'ACTIVE') AS latest_close_end"
                    + " FROM syirkah_ledger l"
                    + " JOIN syirkah_ledger_contract k ON k.syirkah_id = l.syirkah_id AND k.version ="
                    + "   (SELECT max(version) FROM syirkah_ledger_contract WHERE syirkah_id = l.syirkah_id)"
                    + " WHERE l.tenant_id = ? AND (?::text IS NULL OR l.status = ?)"
                    + " AND (?::uuid IS NULL OR l.syirkah_id > ?::uuid)"
                    + " ORDER BY l.syirkah_id LIMIT ?";

    static final String CLAIM_SQL =
            "SELECT seq, event_id, event_type, syirkah_id, occurred_at, payload::text FROM syirkah_ledger_outbox"
                    + " WHERE tenant_id = ? AND published_at IS NULL ORDER BY seq LIMIT ? FOR UPDATE SKIP LOCKED";

    static final String MARK_SQL =
            "UPDATE syirkah_ledger_outbox SET published_at = ? WHERE tenant_id = ? AND seq = ANY(?)"
                    + " AND published_at IS NULL";

    private final JdbcUnitOfWork uow;

    public JdbcOperationsSupport(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public List<SyirkahSummary> page(TenantId tenant, Optional<SyirkahStatus> status, Optional<SyirkahId> after,
                                     int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be 1..500");
        }
        return JdbcSupport.inConnection(uow, tenant, c -> {
            List<SyirkahSummary> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(PAGE_SQL)) {
                ps.setObject(1, tenant.value());
                String st = status.map(Enum::name).orElse(null);
                ps.setString(2, st);
                ps.setString(3, st);
                String cursor = after.map(a -> a.value().toString()).orElse(null);
                ps.setString(4, cursor);
                ps.setString(5, cursor);
                ps.setInt(6, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.sql.Date end = rs.getDate(11);
                        out.add(new SyirkahSummary(new SyirkahId(rs.getObject(1, UUID.class)),
                                SyirkahType.valueOf(rs.getString(2)), rs.getString(3).trim(),
                                SyirkahStatus.valueOf(rs.getString(4)), rs.getInt(8), rs.getInt(9), rs.getInt(10),
                                end == null ? null : end.toLocalDate(), rs.getInt(5),
                                JdbcSupport.instant(rs, 6), JdbcSupport.instant(rs, 7)));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public List<OutboxRecord> claim(TenantId tenant, int limit) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            List<OutboxRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(CLAIM_SQL)) {
                ps.setObject(1, tenant.value());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID sid = rs.getObject(4, UUID.class);
                        out.add(new OutboxRecord(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                                sid == null ? null : new SyirkahId(sid), JdbcSupport.instant(rs, 5),
                                rs.getString(6)));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public void markPublished(TenantId tenant, List<Long> sequences, Instant at) {
        if (sequences.isEmpty()) {
            return;
        }
        JdbcSupport.inConnection(uow, tenant, c -> {
            Array seqs = c.createArrayOf("bigint", sequences.toArray());
            try (PreparedStatement ps = c.prepareStatement(MARK_SQL)) {
                ps.setObject(1, JdbcSupport.ts(at));
                ps.setObject(2, tenant.value());
                ps.setArray(3, seqs);
                ps.executeUpdate();
            } finally {
                seqs.free();
            }
            return null;
        });
    }
}
