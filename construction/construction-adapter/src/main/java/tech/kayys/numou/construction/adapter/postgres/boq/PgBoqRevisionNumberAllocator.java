package tech.kayys.numou.construction.adapter.postgres.boq;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;

import java.util.Objects;
import java.util.UUID;

/**
 * Allocates monotonically increasing revision numbers using a row lock scoped to
 * one tenant + BOQ pair. The caller must supply a connection already participating in the
 * command transaction and must insert the snapshot before committing that same
 * transaction.
 *
 * <p>The minimumNextRevision argument is deliberately supplied by the BOQ adapter:
 * this class does not assume whether the initial BOQ state is revision 0 or 1.
 * Pass the authoritative next revision derived from the aggregate/schema.</p>
 */
public final class PgBoqRevisionNumberAllocator {
    static final String ENSURE_COUNTER = """
            INSERT INTO construction_boq_revision_counter (tenant_id, boq_id, last_allocated_revision)
            VALUES ($1, $2, $3 - 1)
            ON CONFLICT (tenant_id, boq_id) DO NOTHING
            """;
    static final String LOCK_COUNTER = """
            SELECT last_allocated_revision
              FROM construction_boq_revision_counter
             WHERE tenant_id = $1 AND boq_id = $2
             FOR UPDATE
            """;
    static final String UPDATE_COUNTER = """
            UPDATE construction_boq_revision_counter
               SET last_allocated_revision = $3, updated_at = now()
             WHERE tenant_id = $1 AND boq_id = $2
            """;

    private final SqlConnection connection;

    public PgBoqRevisionNumberAllocator(SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /**
     * Reserves one revision number inside the caller's transaction.
     * A rollback also rolls back this allocation, avoiding gaps caused solely by
     * failed commands. Gaps may still exist if historical data was imported.
     */
    public Uni<Integer> allocateNext(String tenantId, UUID boqId, int minimumNextRevision) {
        if (tenantId == null || tenantId.isBlank()) return Uni.createFrom().failure(new IllegalArgumentException("tenantId is required"));
        Objects.requireNonNull(boqId, "boqId");
        if (minimumNextRevision < 1) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("minimumNextRevision must be >= 1"));
        }
        return connection.preparedQuery(ENSURE_COUNTER)
                .execute(Tuple.of(tenantId, boqId, minimumNextRevision))
                .flatMap(ignored -> connection.preparedQuery(LOCK_COUNTER)
                        .execute(Tuple.of(tenantId, boqId)))
                .flatMap(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Revision counter missing after insert/lock for BOQ " + boqId));
                    }
                    int lastAllocated = rows.iterator().next().getInteger("last_allocated_revision");
                    int next = Math.max(lastAllocated + 1, minimumNextRevision);
                    return connection.preparedQuery(UPDATE_COUNTER)
                            .execute(Tuple.of(tenantId, boqId, next))
                            .flatMap(updated -> updated.rowCount() == 1
                                    ? Uni.createFrom().item(next)
                                    : Uni.createFrom().failure(new IllegalStateException(
                                            "Revision counter update did not affect exactly one row")));
                });
    }
}
