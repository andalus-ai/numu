package tech.kayys.numou.inventory.adapter.postgres;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.inventory.spi.InventoryRepository;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTenantInventoryRepositoryProviderTest {

    @Test
    void createsTenantBoundRepositoriesWithoutRetainingAnUnboundedTenantCache() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        PostgresTenantInventoryRepositoryProvider provider =
                new PostgresTenantInventoryRepositoryProvider(dataSource);

        InventoryRepository first = provider.forTenant(" tenant-a ");
        InventoryRepository second = provider.forTenant("tenant-a");

        assertInstanceOf(PostgresInventoryRepository.class, first);
        assertInstanceOf(PostgresInventoryRepository.class, second);
        assertNotSame(first, second,
                "repository instances are lightweight and must not be retained in a per-tenant cache");
    }

    @Test
    void rejectsMissingOrInvalidTenantIdentifiers() {
        PostgresTenantInventoryRepositoryProvider provider =
                new PostgresTenantInventoryRepositoryProvider(new PGSimpleDataSource());

        assertThrows(NullPointerException.class, () -> provider.forTenant(null));
        assertThrows(IllegalArgumentException.class, () -> provider.forTenant("  "));
        assertThrows(IllegalArgumentException.class, () -> provider.forTenant("x".repeat(81)));
    }

    @Test
    void requiresADataSource() {
        assertThrows(NullPointerException.class,
                () -> new PostgresTenantInventoryRepositoryProvider(null));
    }
}
