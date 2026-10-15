package tech.kayys.syirkah.construction.adapter.postgres.boq;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.syirkah.construction.domain.boq.BoqItemType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configurable implementation of the live-BOQ persistence seam. It uses only the
 * supplied transaction-bound connection and performs no transaction management.
 * Configure it only after confirming every mapped column against the real schema.
 */
public final class MappedPgLiveBoqRevisionStoreFactory implements PgLiveBoqRevisionStoreFactory {
    private final PgLiveBoqSqlMapping m;

    public MappedPgLiveBoqRevisionStoreFactory(PgLiveBoqSqlMapping mapping) {
        this.m = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public PgLiveBoqRevisionStore create(SqlConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Store(connection);
    }

    private final class Store implements PgLiveBoqRevisionStore {
        private final SqlConnection connection;
        private Store(SqlConnection connection) { this.connection = connection; }

        @Override
        public Uni<LiveBoq> loadForUpdate(String tenantId, UUID boqId) {
            requireTenant(tenantId); Objects.requireNonNull(boqId, "boqId");
            String sql = "SELECT " + c(m.boqId()) + " AS boq_id, " + c(m.projectId()) + " AS project_id, "
                    + c(m.tenantId()) + " AS tenant_id, " + c(m.boqName()) + " AS boq_name, "
                    + c(m.currentRevision()) + " AS current_revision, " + c(m.status()) + " AS boq_status FROM "
                    + m.boqTableSql() + " WHERE " + c(m.boqId()) + " = $1 AND " + c(m.tenantId()) + " = $2 FOR UPDATE";
            return connection.preparedQuery(sql).execute(Tuple.of(boqId, tenantId)).flatMap(rows -> {
                if (!rows.iterator().hasNext()) return Uni.createFrom().failure(
                        new LiveBoqNotFoundException("BOQ not found: " + boqId));
                Row row = rows.iterator().next();
                return Uni.createFrom().item(new LiveBoq(row.getUUID("boq_id"), row.getUUID("project_id"),
                        row.getString("tenant_id"), row.getString("boq_name"), row.getInteger("current_revision"),
                        row.getString("boq_status")));
            });
        }

        @Override
        public Uni<List<LiveBoqItem>> loadItems(String tenantId, UUID boqId) {
            requireTenant(tenantId); Objects.requireNonNull(boqId, "boqId");
            String sql = "SELECT item_row." + c(m.itemId()) + " AS item_id, item_row." + c(m.wbsNodeId()) + " AS wbs_node_id, "
                    + "item_row." + c(m.itemCode()) + " AS item_code, item_row." + c(m.description()) + " AS item_description, "
                    + "item_row." + c(m.itemType()) + " AS item_type, item_row." + c(m.quantity()) + " AS item_quantity, "
                    + "item_row." + c(m.quantityUnit()) + " AS quantity_unit, item_row." + c(m.rateAmount()) + " AS rate_amount, "
                    + "item_row." + c(m.rateUnit()) + " AS rate_unit, item_row." + c(m.currencyCode()) + " AS currency_code FROM "
                    + m.itemTableSql() + " AS item_row WHERE item_row." + c(m.itemBoqId()) + " = $1 AND EXISTS (SELECT 1 FROM "
                    + m.boqTableSql() + " AS boq_row WHERE " + "boq_row." + c(m.boqId()) + " = item_row." + c(m.itemBoqId())
                    + " AND " + "boq_row." + c(m.tenantId()) + " = $2) ORDER BY "
                    + "item_row." + c(m.itemCode()) + ", " + "item_row." + c(m.itemId());
            return connection.preparedQuery(sql).execute(Tuple.of(boqId, tenantId)).map(rows -> {
                List<LiveBoqItem> items = new ArrayList<>();
                for (Row row : rows) items.add(new LiveBoqItem(row.getUUID("item_id"), row.getUUID("wbs_node_id"),
                        row.getString("item_code"), row.getString("item_description"),
                        BoqItemType.valueOf(row.getString("item_type")), row.getBigDecimal("item_quantity"),
                        row.getString("quantity_unit"), row.getBigDecimal("rate_amount"),
                        row.getString("rate_unit"), row.getString("currency_code").trim()));
                return List.copyOf(items);
            });
        }

        @Override
        public Uni<Boolean> compareAndSetState(String tenantId, UUID boqId, int expectedRevision, String expectedStatus,
                                                int newRevision, String newStatus, Instant changedAt) {
            requireTenant(tenantId); Objects.requireNonNull(boqId, "boqId"); Objects.requireNonNull(expectedStatus, "expectedStatus");
            Objects.requireNonNull(newStatus, "newStatus"); Objects.requireNonNull(changedAt, "changedAt");
            String sql = "UPDATE " + m.boqTableSql() + " SET " + c(m.currentRevision()) + " = $1, "
                    + c(m.status()) + " = $2, " + c(m.changedAt()) + " = $3 WHERE " + c(m.boqId())
                    + " = $4 AND " + c(m.tenantId()) + " = $5 AND " + c(m.currentRevision()) + " = $6 AND " + c(m.status()) + " = $7";
            return connection.preparedQuery(sql).execute(Tuple.of(newRevision, newStatus,
                    changedAt.atOffset(ZoneOffset.UTC), boqId, tenantId, expectedRevision, expectedStatus))
                    .map(rows -> rows.rowCount() == 1);
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    private String c(String column) { return m.col(column); }

    public static final class LiveBoqNotFoundException extends RuntimeException {
        public LiveBoqNotFoundException(String message) { super(message); }
    }
}
