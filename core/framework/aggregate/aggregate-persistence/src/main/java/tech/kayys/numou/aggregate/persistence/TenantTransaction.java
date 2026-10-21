package tech.kayys.numou.aggregate.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs work inside a database transaction scoped to one tenant: sets {@code app.tenant_id} with
 * {@code set_config(..., true)} (transaction-local) so Postgres row-level security applies to every statement.
 *
 * <p>Joins the caller's transaction if one is active (the unit of work), so aggregate writes and outbox appends
 * stay atomic. Because the setting is transaction-local it cannot leak to the next user of a pooled connection
 * (verified on PostgreSQL 16). NOT COMPILED in sandbox.
 */
public final class TenantTransaction {

    private TenantTransaction() { }

    public static <T> Uni<T> run(UUID tenantId, Supplier<Uni<T>> work) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return Panache.withTransaction(() -> Panache.getSession()
                .flatMap(session -> session.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)", String.class)
                        .setParameter(1, tenantId.toString())
                        .getSingleResult())
                .flatMap(ignored -> work.get()));
    }
}
