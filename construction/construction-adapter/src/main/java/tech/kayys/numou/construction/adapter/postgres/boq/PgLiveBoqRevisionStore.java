package tech.kayys.numou.construction.adapter.postgres.boq;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.domain.boq.BoqItemType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Transaction-bound access to the live BOQ aggregate and items. */
public interface PgLiveBoqRevisionStore {
    /** Loads and locks the BOQ row (SELECT ... FOR UPDATE) for this transaction. */
    Uni<LiveBoq> loadForUpdate(String tenantId, UUID boqId);

    /** Returns the current live item values in a deterministic order. */
    Uni<List<LiveBoqItem>> loadItems(String tenantId, UUID boqId);

    /**
     * Compare-and-set mutable aggregate metadata. Must return false on stale expected
     * revision/status. This write must use the same connection/transaction.
     */
    Uni<Boolean> compareAndSetState(String tenantId, UUID boqId, int expectedRevision, String expectedStatus,
                                    int newRevision, String newStatus, Instant changedAt);

    record LiveBoq(UUID id, UUID projectId, String tenantId, String name,
                   int currentRevision, String status) {
        public LiveBoq {
            if (id == null || projectId == null) throw new IllegalArgumentException("BOQ and project IDs are required");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Tenant ID is required");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("BOQ name is required");
            if (currentRevision < 1) throw new IllegalArgumentException("Current revision must be >= 1");
            if (status == null || status.isBlank()) throw new IllegalArgumentException("BOQ status is required");
        }
    }

    record LiveBoqItem(UUID itemId, UUID wbsNodeId, String itemCode, String description,
                       BoqItemType itemType, BigDecimal quantity, String quantityUnit,
                       BigDecimal rate, String rateUnit, String currencyCode) {
        public LiveBoqItem {
            if (itemId == null || wbsNodeId == null) throw new IllegalArgumentException("Item and WBS IDs are required");
            if (itemCode == null || itemCode.isBlank() || description == null || description.isBlank())
                throw new IllegalArgumentException("Item code and description are required");
            if (itemType == null || quantity == null || rate == null) throw new IllegalArgumentException("Item type, quantity and rate are required");
            if (quantity.signum() < 0 || rate.signum() < 0) throw new IllegalArgumentException("Quantity and rate must be non-negative");
            if (quantityUnit == null || quantityUnit.isBlank() || rateUnit == null || rateUnit.isBlank())
                throw new IllegalArgumentException("Quantity and rate units are required");
            if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency code must be ISO-like uppercase 3 letters");
        }
    }
}
