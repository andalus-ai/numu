package tech.kayys.numou.inventory.adapter.postgres;

import tech.kayys.numou.inventory.spi.InventoryRepository;
import tech.kayys.numou.inventory.spi.TenantInventoryRepositoryProvider;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Shared-table PostgreSQL provider. Each call creates a lightweight tenant-bound
 * repository over the shared DataSource. Repository instances hold no tenant
 * state beyond their immutable tenant identifier, so caching them would create
 * an unbounded per-tenant in-memory registry without providing a connection benefit.
 *
 * <p>Callers must pass the authenticated tenant identifier, not a client-selected
 * storage identifier. Tenant authorization remains the responsibility of the
 * application security boundary.</p>
 */
public final class PostgresTenantInventoryRepositoryProvider implements TenantInventoryRepositoryProvider {
    private final DataSource dataSource;

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
        return new PostgresInventoryRepository(dataSource, normalized);
    }
}
