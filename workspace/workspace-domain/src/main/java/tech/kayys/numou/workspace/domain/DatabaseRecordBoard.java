package tech.kayys.numou.workspace.domain;

import java.util.List;
import java.util.Objects;

/** Bounded board projection. Groups contain the current page, while totalRecords is the full filtered count. */
public record DatabaseRecordBoard(String groupByProperty, List<DatabaseRecordGroup> groups,
                                 long totalRecords, int limit, int offset) {
    public DatabaseRecordBoard {
        if (groupByProperty == null || !groupByProperty.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid group-by property");
        }
        groups = List.copyOf(groups);
        if (totalRecords < 0) throw new IllegalArgumentException("totalRecords cannot be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
    }
}
