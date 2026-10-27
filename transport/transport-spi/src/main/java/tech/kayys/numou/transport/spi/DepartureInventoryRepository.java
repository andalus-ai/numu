package tech.kayys.numou.transport.spi;

import tech.kayys.numou.transport.domain.DepartureInventory;

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

    /** Creates a new inventory at version zero. Existing tenant/departure inventory is a conflict. */
    void create(DepartureInventory inventory);

    /**
     * Atomically persists a new snapshot if the stored version equals expectedVersion.
     * The inventory and all hold changes must commit or roll back together.
     */
    void save(DepartureInventory inventory, int expectedVersion);
}
