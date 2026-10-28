package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.AkadFormationPort;
import tech.kayys.numou.numoudomain.AkadFormation;
import tech.kayys.numou.numoudomain.FormationPolicy;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL store for the akad formation record (V20261028_01). Tenant-scoped through RLS; append-only by trigger.
 * Runs in the unit of work that opens the syirkah, so the record exists if and only if the syirkah does.
 */
public class JdbcAkadFormationPort implements AkadFormationPort {

    private final JdbcUnitOfWork uow;

    public JdbcAkadFormationPort(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public void record(TenantId tenant, SyirkahId syirkah, AkadFormation formation, FormationPolicy policy,
                       Instant at) {
        JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_akad_formation (syirkah_id, tenant_id, formation, policy, recorded_at)"
                            + " VALUES (?,?,?::jsonb,?::jsonb,?)")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                ps.setString(3, SyirkahJsonCodec.encodeFormation(formation));
                ps.setString(4, SyirkahJsonCodec.encodePolicy(policy));
                ps.setObject(5, JdbcSupport.ts(at));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<Recorded> find(TenantId tenant, SyirkahId syirkah) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT formation::text, policy::text, recorded_at FROM syirkah_ledger_akad_formation"
                            + " WHERE syirkah_id = ? AND tenant_id = ?")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<Recorded>empty();
                    }
                    return Optional.of(new Recorded(SyirkahJsonCodec.decodeFormation(rs.getString(1)),
                            SyirkahJsonCodec.decodePolicy(rs.getString(2)), JdbcSupport.instant(rs, 3)));
                }
            }
        });
    }
}
