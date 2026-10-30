package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.CloseId;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.TradingResultCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Keeps the trading figures (Bab XXII) a close was worked out from, so the cost of goods sold can be audited. */
public interface TradingRecordPort {

    /** One record per close; a second one for the same close is an error. */
    void record(TenantId tenant, SyirkahId syirkah, CloseId close, TradingResultCalculator.Input input, Instant at);

    Optional<Recorded> find(TenantId tenant, SyirkahId syirkah, CloseId close);

    List<Recorded> list(TenantId tenant, SyirkahId syirkah);

    record Recorded(CloseId close, TradingResultCalculator.Input input, Instant recordedAt) {
    }
}
