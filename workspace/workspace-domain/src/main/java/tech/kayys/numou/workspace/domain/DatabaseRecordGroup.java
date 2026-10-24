package tech.kayys.numou.workspace.domain;

import java.util.List;

/** One board column; null value represents records with no grouping value. */
public record DatabaseRecordGroup(String value, List<DatabaseRecord> records) {
    public DatabaseRecordGroup {
        if (value != null && value.isBlank()) throw new IllegalArgumentException("group value cannot be blank");
        records = List.copyOf(records);
    }
}
