package tech.kayys.numou.workspace.domain;

import java.util.List;

/** A bounded page of tenant-scoped structured records. */
public record DatabaseRecordPage(List<DatabaseRecord> records, long total, int limit, int offset) {
    public DatabaseRecordPage {
        records = List.copyOf(records);
        if (total < 0) throw new IllegalArgumentException("total cannot be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        if (records.size() > limit) throw new IllegalArgumentException("page exceeds limit");
    }
}
