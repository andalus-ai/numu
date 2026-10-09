package tech.kayys.syirkah.reliability.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.security.domain.audit.AuditEntry;
import tech.kayys.syirkah.security.spi.port.AuditPort;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Append-only audit sink. The table rejects UPDATE/DELETE/TRUNCATE by trigger; reads are tenant-filtered by
 * row-level security on {@code app.tenant_id} (see V100 migration; both verified on PG 16).
 * {@code findByResource} relies on the connection's tenant setting - callers must run inside a tenant-scoped
 * transaction (see {@code TenantScopedPool}). NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class PgAuditStore implements AuditPort {

    static final String INSERT = """
            INSERT INTO platform_audit (tenant_id, actor, action, resource, resource_id, occurred_at, correlation_id, changes)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)""";
    static final String BY_RESOURCE = """
            SELECT actor, tenant_id, action, resource, resource_id, occurred_at, correlation_id, changes
            FROM platform_audit WHERE resource = $1 AND resource_id = $2 ORDER BY audit_id""";

    @Inject
    PgPool pool;

    @Override
    public Uni<Void> append(AuditEntry e) {
        return pool.preparedQuery(INSERT).execute(toTuple(e)).replaceWithVoid();
    }

    @Override
    public Uni<Void> appendAll(List<AuditEntry> entries) {
        if (entries.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return pool.preparedQuery(INSERT).executeBatch(entries.stream().map(PgAuditStore::toTuple).toList()).replaceWithVoid();
    }

    @Override
    public Uni<List<AuditEntry>> findByResource(String resource, String resourceId) {
        return pool.preparedQuery(BY_RESOURCE).execute(Tuple.of(resource, resourceId == null ? "" : resourceId))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false).map(PgAuditStore::toEntry).toList());
    }

    private static Tuple toTuple(AuditEntry e) {
        return Tuple.from(java.util.List.of(UUID.fromString(e.tenant()), e.actor(), e.action(), e.resource(), e.resourceId(),
                Instant.parse(e.timestamp()).atOffset(ZoneOffset.UTC), e.correlationId(), e.changes()));
    }

    private static AuditEntry toEntry(Row r) {
        return new AuditEntry(r.getString("actor"), r.getUUID("tenant_id").toString(), r.getString("action"),
                r.getString("resource"), r.getString("resource_id"),
                r.getOffsetDateTime("occurred_at").toInstant().toString(), r.getString("correlation_id"), r.getString("changes"));
    }
}
