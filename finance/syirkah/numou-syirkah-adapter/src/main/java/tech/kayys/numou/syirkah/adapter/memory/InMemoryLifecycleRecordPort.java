package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.LifecycleRecordPort;
import tech.kayys.numou.numoudomain.LifecycleChange;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryLifecycleRecordPort implements LifecycleRecordPort {

    private record Key(TenantId tenant, SyirkahId syirkah, int version) {
    }

    private final Map<Key, Recorded> store = new HashMap<>();

    @Override
    public synchronized void record(TenantId tenant, SyirkahId syirkah, int newContractVersion,
                                    LifecycleChange change, Instant at) {
        if (store.putIfAbsent(new Key(tenant, syirkah, newContractVersion),
                new Recorded(newContractVersion, change, at)) != null) {
            throw new IllegalStateException("lifecycle record already exists for akad v" + newContractVersion);
        }
    }

    @Override
    public synchronized List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        List<Recorded> out = new ArrayList<>();
        store.forEach((k, v) -> {
            if (k.tenant().equals(tenant) && k.syirkah().equals(syirkah)) {
                out.add(v);
            }
        });
        out.sort(Comparator.comparingInt(Recorded::contractVersion));
        return out;
    }
}
