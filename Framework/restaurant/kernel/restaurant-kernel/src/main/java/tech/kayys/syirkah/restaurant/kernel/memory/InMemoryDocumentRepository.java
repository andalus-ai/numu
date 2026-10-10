package tech.kayys.syirkah.restaurant.kernel.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.entity.OptimisticLockable;
import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.restaurant.kernel.domain.ConcurrencyConflictException;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory repository for tests and local development.
 *
 * <p>It stores <em>snapshots</em>, not live objects, and enforces the same optimistic-lock and tenant rules as
 * the Postgres implementation, so tests written against it catch real concurrency bugs (two writers, stale
 * saves) instead of passing by sharing object references.
 *
 * @param <S> snapshot type produced by the aggregate
 */
public class InMemoryDocumentRepository<A extends AbstractAggregateRoot<ID> & OptimisticLockable, ID extends DomainId<UUID>, S>
        implements VersionedRepository<A, ID> {

    private record Key(UUID tenantId, UUID id) { }

    private record Stored<S>(long version, S snapshot) { }

    private final Map<Key, Stored<S>> rows = new ConcurrentHashMap<>();
    private final Function<A, S> toSnapshot;
    private final Function<S, A> fromSnapshot;

    public InMemoryDocumentRepository(Function<A, S> toSnapshot, Function<S, A> fromSnapshot) {
        this.toSnapshot = toSnapshot;
        this.fromSnapshot = fromSnapshot;
    }

    @Override
    public Uni<Optional<A>> find(UUID tenantId, ID id) {
        return Uni.createFrom().item(() -> Optional.ofNullable(load(tenantId, id.value())));
    }

    @Override
    public Uni<Void> save(UUID tenantId, A aggregate) {
        return Uni.createFrom().item(() -> {
            final var key = new Key(tenantId, aggregate.id().value());
            final long expected = aggregate.storedVersion();
            final var next = new Stored<>(expected + 1, toSnapshot.apply(aggregate));
            rows.compute(key, (k, current) -> {
                if (expected == 0 ? current != null : current == null || current.version() != expected) {
                    throw new ConcurrencyConflictException(expected == 0
                            ? "Aggregate already exists: " + key.id()
                            : "Aggregate was modified concurrently: " + key.id());
                }
                return next; // compute() is atomic per key: check-and-swap cannot interleave
            });
            aggregate.markStored(expected + 1);
            return null;
        });
    }

    protected final A load(UUID tenantId, UUID id) {
        final var stored = rows.get(new Key(tenantId, id));
        if (stored == null) {
            return null;
        }
        final A restored = fromSnapshot.apply(stored.snapshot());
        restored.markStored(stored.version());
        return restored;
    }

    /** All aggregates of one tenant, freshly restored; concrete repositories filter/sort for their queries. */
    protected final List<A> allOf(UUID tenantId) {
        return rows.entrySet().stream()
                .filter(e -> e.getKey().tenantId().equals(tenantId))
                .map(e -> load(tenantId, e.getKey().id()))
                .toList();
    }
}
