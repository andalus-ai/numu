package tech.kayys.numou.transport.spi;

import tech.kayys.numou.transport.domain.DepartureInventory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for one tenant's scheduled-departure inventory.
 *
 * <p>{@link #save(DepartureInventory, int)} is a compare-and-set operation. The caller must
 * provide the version it loaded. A concurrent writer causes {@link InventoryConcurrencyException}
 * and must reload/re-evaluate the domain command; it must not blindly retry the stale snapshot.</p>
 */
public interface DepartureInventoryRepository {
    Optional<DepartureInventory> find(String tenantId, UUID departureId);

    /**
     * Returns tenant/departure keys whose holds are due for expiry, ordered for stable batching.
     * Implementations must bound the result by {@code limit} and must not return other tenants' data
     * through a caller-supplied tenant filter (this query is an internal maintenance scan).
     */
    List<InventoryKey> findDueInventories(Instant now, int limit);

    record InventoryKey(String tenantId, UUID departureId) {
        public InventoryKey {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (departureId == null) throw new IllegalArgumentException("departureId must not be null");
        }
    }

    /** Creates a new inventory at version zero. Existing tenant/departure inventory is a conflict. */
    void create(DepartureInventory inventory);

    /**
     * Atomically persists a new snapshot if the stored version equals expectedVersion.
     * The inventory and all hold changes must commit or roll back together.
     */
    void save(DepartureInventory inventory, int expectedVersion);
}
