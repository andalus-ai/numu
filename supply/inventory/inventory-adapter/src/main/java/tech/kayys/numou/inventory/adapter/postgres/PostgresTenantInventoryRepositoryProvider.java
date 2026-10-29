package tech.kayys.numou.inventory.adapter.postgres;

import tech.kayys.numou.inventory.spi.InventoryRepository;
import tech.kayys.numou.inventory.spi.TenantInventoryRepositoryProvider;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared-table PostgreSQL provider. Repository instances are tenant-bound and
 * cached so application code cannot accidentally reuse an unscoped repository.
 *
 * <p>Callers must pass the authenticated tenant identifier, not a client-selected
 * storage identifier. Tenant authorization remains the responsibility of the
 * application security boundary.</p>
 */
public final class PostgresTenantInventoryRepositoryProvider implements TenantInventoryRepositoryProvider {
    private final DataSource dataSource;
    private final ConcurrentMap<String, InventoryRepository> repositories = new ConcurrentHashMap<>();

    public PostgresTenantInventoryRepositoryProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource is required");
    }

    @Override
    public InventoryRepository forTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "Tenant is required");
        String normalized = tenantId.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new IllegalArgumentException("Tenant must contain 1..80 characters");
        }
        return repositories.computeIfAbsent(normalized,
                tenant -> new PostgresInventoryRepository(dataSource, tenant));
    }
}
