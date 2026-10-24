package tech.kayys.numou.warehouse.application;

import java.util.List;

/** One bounded page of tenant-scoped recovery audit history. The cursor is opaque to callers. */
public record WarehouseRecoveryAuditPage(List<WarehouseRecoveryOperatorAuditEvent> events, String nextCursor) {
    public WarehouseRecoveryAuditPage {
        events = List.copyOf(events);
        if (nextCursor != null && nextCursor.isBlank()) {
            throw new IllegalArgumentException("nextCursor must be null or non-blank");
        }
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
