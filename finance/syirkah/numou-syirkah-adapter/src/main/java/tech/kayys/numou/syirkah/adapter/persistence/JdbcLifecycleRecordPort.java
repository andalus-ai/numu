package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.LifecycleRecordPort;
import tech.kayys.numou.numoudomain.LifecycleChange;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL store for lifecycle records (V20261029_01). Tenant-scoped through RLS; append-only by trigger. Runs in
 * the unit of work of the revision, so a record exists if and only if the akad version does.
 */
public class JdbcLifecycleRecordPort implements LifecycleRecordPort {

    private final JdbcUnitOfWork uow;

    public JdbcLifecycleRecordPort(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public void record(TenantId tenant, SyirkahId syirkah, int newContractVersion, LifecycleChange change,
                       Instant at) {
        JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_lifecycle (syirkah_id, contract_version, tenant_id, kind, change,"
                            + " recorded_at) VALUES (?,?,?,?,?::jsonb,?)")) {
                ps.setObject(1, syirkah.value());
                ps.setInt(2, newContractVersion);
                ps.setObject(3, tenant.value());
                ps.setString(4, change.kind().name());
                ps.setString(5, SyirkahJsonCodec.encodeLifecycle(change));
                ps.setObject(6, JdbcSupport.ts(at));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT contract_version, change::text, recorded_at FROM syirkah_ledger_lifecycle"
                            + " WHERE syirkah_id = ? AND tenant_id = ? ORDER BY contract_version")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                List<Recorded> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Recorded(rs.getInt(1), SyirkahJsonCodec.decodeLifecycle(rs.getString(2)),
                                JdbcSupport.instant(rs, 3)));
                    }
                }
                return out;
            }
        });
    }
}
