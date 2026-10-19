package tech.kayys.numou.syirkah.adapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.governance.PendingApproval;
import tech.kayys.numou.syirkah.application.port.ApprovalPort;
import tech.kayys.numou.syirkah.domain.SyirkahId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL maker-checker store (V301). Tenant-scoped through RLS. A request can be inserted once and decided
 * once: the trigger freezes it after PENDING, and {@link #save} turns an attempt to re-decide into an error.
 */
public class JdbcApprovalPort implements ApprovalPort {

    private static final String COLUMNS = "approval_id, syirkah_id, kind, payload::text, maker, proposed_at, status,"
            + " checker, decided_at, note";

    private final JdbcUnitOfWork uow;

    public JdbcApprovalPort(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public void save(PendingApproval a) {
        JdbcSupport.inConnection(uow, a.tenant(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_approval (approval_id, tenant_id, syirkah_id, kind, payload, maker,"
                            + " proposed_at, status, checker, decided_at, note) VALUES (?,?,?,?,?::jsonb,?,?,?,?,?,?)"
                            + " ON CONFLICT (approval_id) DO UPDATE SET status = EXCLUDED.status,"
                            + " checker = EXCLUDED.checker, decided_at = EXCLUDED.decided_at, note = EXCLUDED.note"
                            + " WHERE syirkah_ledger_approval.status = 'PENDING'")) {
                ps.setObject(1, a.id());
                ps.setObject(2, a.tenant().value());
                ps.setObject(3, a.syirkahId().value());
                ps.setString(4, a.kind().name());
                ps.setString(5, SyirkahJsonCodec.encodePayload(a.kind(), a.payload()));
                ps.setString(6, a.maker());
                ps.setObject(7, JdbcSupport.ts(a.proposedAt()));
                ps.setString(8, a.status().name());
                ps.setString(9, a.checker());
                ps.setObject(10, JdbcSupport.ts(a.decidedAt()));
                ps.setString(11, a.note());
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("approval " + a.id() + " is already decided and cannot change");
                }
            }
            return null;
        });
    }

    @Override
    public Optional<PendingApproval> find(TenantId tenant, UUID id) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM syirkah_ledger_approval WHERE approval_id = ? AND tenant_id = ?")) {
                ps.setObject(1, id);
                ps.setObject(2, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs, tenant)) : Optional.<PendingApproval>empty();
                }
            }
        });
    }

    @Override
    public List<PendingApproval> pending(TenantId tenant) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            List<PendingApproval> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM syirkah_ledger_approval WHERE tenant_id = ? AND status = 'PENDING'"
                    + " ORDER BY proposed_at, approval_id")) {
                ps.setObject(1, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(map(rs, tenant));
                    }
                }
            }
            return out;
        });
    }

    private static PendingApproval map(ResultSet rs, TenantId tenant) throws SQLException {
        PendingApproval.Kind kind = PendingApproval.Kind.valueOf(rs.getString(3));
        SyirkahId sid = new SyirkahId(rs.getObject(2, UUID.class));
        return new PendingApproval(rs.getObject(1, UUID.class), tenant, sid, kind,
                SyirkahJsonCodec.decodePayload(kind, tenant, sid, rs.getString(4)), rs.getString(5),
                JdbcSupport.instant(rs, 6), PendingApproval.Status.valueOf(rs.getString(7)), rs.getString(8),
                JdbcSupport.instant(rs, 9), rs.getString(10));
    }
}
