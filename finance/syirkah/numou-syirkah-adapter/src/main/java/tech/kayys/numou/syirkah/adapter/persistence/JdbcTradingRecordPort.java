package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.TradingRecordPort;
import tech.kayys.numou.numoudomain.CloseId;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.TradingResultCalculator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL store for trading records (V20261030_02). Tenant-scoped through RLS; append-only by trigger. */
public class JdbcTradingRecordPort implements TradingRecordPort {

    private final JdbcUnitOfWork uow;

    public JdbcTradingRecordPort(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    @Override
    public void record(TenantId tenant, SyirkahId syirkah, CloseId close, TradingResultCalculator.Input input,
                       Instant at) {
        JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger_trading (syirkah_id, close_id, tenant_id, figures, recorded_at)"
                            + " VALUES (?,?,?,?::jsonb,?)")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, close.value());
                ps.setObject(3, tenant.value());
                ps.setString(4, SyirkahJsonCodec.encodeTrading(input));
                ps.setObject(5, JdbcSupport.ts(at));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<Recorded> find(TenantId tenant, SyirkahId syirkah, CloseId close) {
        return list(tenant, syirkah).stream().filter(r -> r.close().equals(close)).findFirst();
    }

    @Override
    public List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        return JdbcSupport.inConnection(uow, tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT close_id, figures::text, recorded_at FROM syirkah_ledger_trading"
                            + " WHERE syirkah_id = ? AND tenant_id = ? ORDER BY recorded_at, close_id")) {
                ps.setObject(1, syirkah.value());
                ps.setObject(2, tenant.value());
                List<Recorded> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Recorded(new CloseId(rs.getObject(1, java.util.UUID.class)),
                                SyirkahJsonCodec.decodeTrading(rs.getString(2)), JdbcSupport.instant(rs, 3)));
                    }
                }
                return out;
            }
        });
    }
}
