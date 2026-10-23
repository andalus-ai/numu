package tech.kayys.numou.inventory.spi;

/**
 * Resolves the inventory persistence boundary for one authenticated tenant.
 *
 * <p>The returned repository must be tenant-bound: every read, write, uniqueness
 * check, reservation lookup, and idempotency lookup must be scoped to the supplied
 * tenant. A provider must never return an unscoped shared repository as a fallback.
 * Implementations may route to a tenant column, schema, database, or isolated
 * service, but must enforce the tenant predicate in persistence—not only in the UI
 * or in this application facade.</p>
 *
 * <p>For a shared-table database, tenant identity must be part of all relevant
 * unique keys and queries. For a schema/database-per-tenant deployment, the
 * resolver must validate the tenant against the authenticated request context
 * before selecting the storage boundary.</p>
 */
@FunctionalInterface
public interface TenantInventoryRepositoryProvider {
    InventoryRepository forTenant(String tenantId);
}
