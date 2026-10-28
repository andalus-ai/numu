package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.AkadFormationPort;
import tech.kayys.numou.numoudomain.AkadFormation;
import tech.kayys.numou.numoudomain.FormationPolicy;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryAkadFormationPort implements AkadFormationPort {

    private record Key(TenantId tenant, SyirkahId syirkah) {
    }

    private final Map<Key, Recorded> store = new HashMap<>();

    @Override
    public synchronized void record(TenantId tenant, SyirkahId syirkah, AkadFormation formation,
                                    FormationPolicy policy, Instant at) {
        if (store.putIfAbsent(new Key(tenant, syirkah), new Recorded(formation, policy, at)) != null) {
            throw new IllegalStateException("akad formation already recorded for " + syirkah.value());
        }
    }

    @Override
    public synchronized Optional<Recorded> find(TenantId tenant, SyirkahId syirkah) {
        return Optional.ofNullable(store.get(new Key(tenant, syirkah)));
    }
}
