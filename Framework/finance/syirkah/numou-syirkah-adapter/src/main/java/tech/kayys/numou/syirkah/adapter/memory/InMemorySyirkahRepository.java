package tech.kayys.numou.syirkah.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.domain.Syirkah;
import tech.kayys.numou.syirkah.domain.SyirkahId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference repository for tests and single-node use. Stores immutable snapshots (never the live aggregate),
 * enforces optimistic locking and tenant isolation, and re-verifies hash chains on every load.
 */
public class InMemorySyirkahRepository implements SyirkahRepository {

    private final Map<SyirkahId, Syirkah.Snapshot> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Syirkah> find(TenantId tenant, SyirkahId id) {
        Syirkah.Snapshot snap = store.get(id);
        if (snap == null || !snap.tenantId().equals(tenant)) {
            return Optional.empty();
        }
        return Optional.of(Syirkah.restore(snap));
    }

    @Override
    public synchronized void save(Syirkah syirkah, int expectedVersion) {
        Syirkah.Snapshot current = store.get(syirkah.id());
        int stored = current == null ? -1 : current.version();
        if (stored != expectedVersion) {
            throw new ConcurrentModificationException("syirkah " + syirkah.id().value() + " is at version "
                    + stored + ", expected " + expectedVersion);
        }
        if (current != null && !current.tenantId().equals(syirkah.tenantId())) {
            throw new ConcurrentModificationException("tenant mismatch");
        }
        syirkah.incrementVersion();
        store.put(syirkah.id(), syirkah.snapshot());
    }
}
