package tech.kayys.numou.syirkah.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.port.SyirkahCatalogPort;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.application.query.SyirkahSummary;
import tech.kayys.numou.syirkah.domain.SyirkahStatus;
import tech.kayys.numou.syirkah.domain.Syirkah;
import tech.kayys.numou.syirkah.domain.SyirkahId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference repository for tests and single-node use. Stores immutable snapshots (never the live aggregate),
 * enforces optimistic locking and tenant isolation, and re-verifies hash chains on every load.
 */
public class InMemorySyirkahRepository implements SyirkahRepository, SyirkahCatalogPort {

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

    @Override
    public List<SyirkahSummary> page(TenantId tenant, Optional<SyirkahStatus> status, Optional<SyirkahId> after,
                                     int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be 1..500");
        }
        return store.values().stream()
                .filter(s -> s.tenantId().equals(tenant))
                .filter(s -> status.isEmpty() || s.status() == status.get())
                .filter(s -> after.isEmpty()
                        || s.id().value().toString().compareTo(after.get().value().toString()) > 0)
                .sorted(Comparator.comparing(s -> s.id().value().toString()))
                .limit(limit)
                .map(s -> SyirkahSummary.of(Syirkah.restore(s)))
                .toList();
    }
}
