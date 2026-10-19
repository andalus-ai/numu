package tech.kayys.numou.construction.adapter.postgres.boq;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.construction.domain.boq.BoqItemType;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.boq.BoqRevisionSnapshotRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Transaction-bound PostgreSQL adapter for the BOQ revision snapshot tables.
 *
 * <p>The supplied connection is owned by the caller. This adapter never acquires,
 * commits, or rolls back a transaction. Call insert/replaceIfStatus inside the same
 * transaction as the corresponding BOQ aggregate write and outbox event.</p>
 *
 * <p>Every operation is tenant-scoped. Snapshot content (name, items, rates, quantities and
 * calculated amounts) is insert-only. Lifecycle metadata is compare-and-set by expected status.</p>
 */
public final class PgBoqRevisionSnapshotRepository implements BoqRevisionSnapshotRepository {
    private static final String SELECT_HEADER = """
            SELECT boq_id, project_id, revision_number, boq_name, status,
                   calculation_policy_version, created_by, created_at,
                   submitted_by, submitted_at, approved_by, approved_at
              FROM construction_boq_revision_snapshot
             WHERE tenant_id = $1 AND boq_id = $2 AND revision_number = $3
            """;
    private static final String SELECT_ITEMS = """
            SELECT item_id, wbs_node_id, item_code, description, item_type,
                   quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount
              FROM construction_boq_revision_item
             WHERE tenant_id = $1 AND boq_id = $2 AND revision_number = $3
             ORDER BY item_code, item_id
            """;
    private static final String SELECT_ALL_HEADERS = """
            SELECT boq_id, project_id, revision_number, boq_name, status,
                   calculation_policy_version, created_by, created_at,
                   submitted_by, submitted_at, approved_by, approved_at
              FROM construction_boq_revision_snapshot
             WHERE tenant_id = $1 AND boq_id = $2
             ORDER BY revision_number
            """;
    private static final String INSERT_HEADER = """
            INSERT INTO construction_boq_revision_snapshot
                (tenant_id, boq_id, project_id, revision_number, boq_name, status,
                 calculation_policy_version, created_by, created_at,
                 submitted_by, submitted_at, approved_by, approved_at, total_amount)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
            """;
    private static final String INSERT_ITEM = """
            INSERT INTO construction_boq_revision_item
                (tenant_id, boq_id, revision_number, item_id, wbs_node_id, item_code, description,
                 item_type, quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
            """;
    private static final String REPLACE_STATUS = """
            UPDATE construction_boq_revision_snapshot
               SET status = $5, submitted_by = $6, submitted_at = $7,
                   approved_by = $8, approved_at = $9
             WHERE tenant_id = $1 AND boq_id = $2 AND revision_number = $3 AND status = $4
            """;

    private final SqlConnection connection;

    public PgBoqRevisionSnapshotRepository(SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public CompletionStage<Optional<BoqRevisionSnapshot>> find(String tenantId, UUID boqId, int revisionNumber) {
        requireTenant(tenantId); requireRevision(boqId, revisionNumber);
        return connection.preparedQuery(SELECT_HEADER).execute(Tuple.of(tenantId, boqId, revisionNumber))
                .flatMap(headers -> {
                    if (!headers.iterator().hasNext()) return Uni.createFrom().item(Optional.<BoqRevisionSnapshot>empty());
                    Row header = headers.iterator().next();
                    return loadItems(tenantId, boqId, revisionNumber).map(items -> Optional.of(toSnapshot(header, items)));
                }).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<List<BoqRevisionSnapshot>> findAllByBoqId(String tenantId, UUID boqId) {
        requireTenant(tenantId); Objects.requireNonNull(boqId, "boqId");
        return connection.preparedQuery(SELECT_ALL_HEADERS).execute(Tuple.of(tenantId, boqId))
                .flatMap(headers -> {
                    List<Row> headerRows = new ArrayList<>();
                    headers.forEach(headerRows::add);
                    Uni<List<BoqRevisionSnapshot>> result = Uni.createFrom().item(new ArrayList<>());
                    for (Row header : headerRows) {
                        int revision = header.getInteger("revision_number");
                        result = result.flatMap(snapshots -> loadItems(tenantId, boqId, revision)
                                .map(items -> { snapshots.add(toSnapshot(header, items)); return snapshots; }));
                    }
                    return result.map(List::copyOf);
                }).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> insert(String tenantId, BoqRevisionSnapshot snapshot) {
        requireTenant(tenantId); Objects.requireNonNull(snapshot, "snapshot");
        Uni<RowSet<Row>> insertHeader = connection.preparedQuery(INSERT_HEADER).execute(Tuple.tuple()
                .addValue(tenantId)
                .addValue(snapshot.boqId())
                .addValue(snapshot.projectId())
                .addValue(snapshot.revisionNumber())
                .addValue(snapshot.boqName())
                .addValue(snapshot.status().name())
                .addValue(snapshot.calculationPolicyVersion())
                .addValue(snapshot.createdBy())
                .addValue(toOffsetDateTime(snapshot.createdAt()))
                .addValue(snapshot.submittedBy())
                .addValue(toOffsetDateTime(snapshot.submittedAt()))
                .addValue(snapshot.approvedBy())
                .addValue(toOffsetDateTime(snapshot.approvedAt()))
                .addValue(snapshot.totalAmount()));
        return insertHeader.flatMap(ignored -> {
            Uni<Void> writes = Uni.createFrom().voidItem();
            for (BoqRevisionSnapshot.ItemSnapshot item : snapshot.items()) {
                writes = writes.flatMap(v -> connection.preparedQuery(INSERT_ITEM).execute(Tuple.tuple()
                .addValue(tenantId)
                .addValue(snapshot.boqId())
                .addValue(snapshot.revisionNumber())
                .addValue(item.itemId())
                .addValue(item.wbsNodeId())
                .addValue(item.itemCode())
                .addValue(item.description())
                .addValue(item.itemType().name())
                .addValue(item.quantity())
                .addValue(item.quantityUnit())
                .addValue(item.rate())
                .addValue(item.rateUnit())
                .addValue(item.currencyCode())
                .addValue(item.amount()))
                        .replaceWithVoid());
            }
            return writes;
        }).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> replaceIfStatus(String tenantId, BoqRevisionSnapshot snapshot,
                                                   BoqRevisionSnapshot.RevisionStatus expectedStatus) {
        requireTenant(tenantId); Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        return connection.preparedQuery(REPLACE_STATUS).execute(Tuple.tuple()
                .addValue(tenantId)
                .addValue(snapshot.boqId())
                .addValue(snapshot.revisionNumber())
                .addValue(expectedStatus.name())
                .addValue(snapshot.status().name())
                .addValue(snapshot.submittedBy())
                .addValue(toOffsetDateTime(snapshot.submittedAt()))
                .addValue(snapshot.approvedBy())
                .addValue(toOffsetDateTime(snapshot.approvedAt())))
                .flatMap(updated -> updated.rowCount() == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new RevisionConflictException(
                                "BOQ revision missing or status changed; expected " + expectedStatus)))
                .subscribeAsCompletionStage();
    }

    private Uni<List<BoqRevisionSnapshot.ItemSnapshot>> loadItems(String tenantId, UUID boqId, int revisionNumber) {
        return connection.preparedQuery(SELECT_ITEMS).execute(Tuple.of(tenantId, boqId, revisionNumber)).map(rows -> {
            List<BoqRevisionSnapshot.ItemSnapshot> items = new ArrayList<>();
            for (Row row : rows) {
                items.add(new BoqRevisionSnapshot.ItemSnapshot(
                        row.getUUID("item_id"), row.getUUID("wbs_node_id"), row.getString("item_code"),
                        row.getString("description"), BoqItemType.valueOf(row.getString("item_type")),
                        row.getBigDecimal("quantity"), row.getString("quantity_unit"),
                        row.getBigDecimal("rate_amount"), row.getString("rate_unit"),
                        row.getString("currency_code").trim(), row.getBigDecimal("line_amount")));
            }
            return List.copyOf(items);
        });
    }

    private static BoqRevisionSnapshot toSnapshot(Row row, List<BoqRevisionSnapshot.ItemSnapshot> items) {
        return new BoqRevisionSnapshot(row.getUUID("boq_id"), row.getUUID("project_id"),
                row.getInteger("revision_number"), row.getString("boq_name"),
                BoqRevisionSnapshot.RevisionStatus.valueOf(row.getString("status")), items,
                row.getString("calculation_policy_version"), row.getUUID("created_by"),
                toInstant(row.getOffsetDateTime("created_at")), row.getUUID("submitted_by"),
                toInstant(row.getOffsetDateTime("submitted_at")), row.getUUID("approved_by"),
                toInstant(row.getOffsetDateTime("approved_at")));
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    private static void requireRevision(UUID boqId, int revisionNumber) {
        Objects.requireNonNull(boqId, "boqId");
        if (revisionNumber < 1) throw new IllegalArgumentException("revisionNumber must be >= 1");
    }

    public static final class RevisionConflictException extends RuntimeException {
        public RevisionConflictException(String message) { super(message); }
    }
}
