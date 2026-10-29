package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.StaffRecordPort;
import tech.kayys.numou.numoudomain.StaffContract;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PostgreSQL store for staff contracts (V20261030_01). Tenant-scoped through RLS; append-only by trigger. */
public class JdbcStaffRecordPort implements StaffRecordPort {

    private final JdbcUnitOfWork uow;

    public JdbcStaffRecordPort(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public int add(TenantId tenant, SyirkahId syirkah, int contractVersion, StaffContract staff, Instant at) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            int seq;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM syirkah_ledger_staff WHERE syirkah_id = ?"
                            + " AND tenant_id = ?")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    seq = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_staff (syirkah_id, sequence_no, tenant_id, contract_version, staff,"
                            + " recorded_at) VALUES (?,?,?,?,?::jsonb,?)")) {
                ps.setObject(1, syirkah.value());
                ps.setInt(2, seq);
                ps.setObject(3, tenant.value());
                ps.setInt(4, contractVersion);
                ps.setString(5, SyirkahJsonCodec.encodeStaff(staff));
                ps.setObject(6, JdbcSupport.ts(at));
                ps.executeUpdate();
            }
            return seq;
        });
    }

    @Override
    public List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sequence_no, contract_version, staff::text, recorded_at FROM syirkah_ledger_staff"
                            + " WHERE syirkah_id = ? AND tenant_id = ? ORDER BY sequence_no")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                List<Recorded> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Recorded(rs.getInt(1), rs.getInt(2), SyirkahJsonCodec.decodeStaff(rs.getString(3)),
                                JdbcSupport.instant(rs, 4)));
                    }
                }
                return out;
            }
        });
    }
}
