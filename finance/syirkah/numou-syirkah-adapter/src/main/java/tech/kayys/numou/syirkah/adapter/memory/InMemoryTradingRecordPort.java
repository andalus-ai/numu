package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.TradingRecordPort;
import tech.kayys.numou.numoudomain.CloseId;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.TradingResultCalculator;

import java.time.Instant;
import java.util.*;

public class InMemoryTradingRecordPort implements TradingRecordPort {

    private record Key(TenantId tenant, SyirkahId syirkah, CloseId close) {
    }

    private final Map<Key, Recorded> store = new LinkedHashMap<>();

    @Override
    public synchronized void record(TenantId tenant, SyirkahId syirkah, CloseId close,
                                    TradingResultCalculator.Input input, Instant at) {
        if (store.putIfAbsent(new Key(tenant, syirkah, close), new Recorded(close, input, at)) != null) {
            throw new IllegalStateException("trading record already exists for close " + close.value());
        }
    }

    @Override
    public synchronized Optional<Recorded> find(TenantId tenant, SyirkahId syirkah, CloseId close) {
        return Optional.ofNullable(store.get(new Key(tenant, syirkah, close)));
    }

    @Override
    public synchronized List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        List<Recorded> out = new ArrayList<>();
        store.forEach((k, v) -> {
            if (k.tenant().equals(tenant) && k.syirkah().equals(syirkah)) {
                out.add(v);
            }
        });
        return out;
    }
}
