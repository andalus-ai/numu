package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Saved presentation/query configuration for a generic structured table. */
public record DatabaseView(
        UUID id,
        UUID tenantId,
        UUID databaseId,
        UUID tableId,
        UUID createdBy,
        String name,
        DatabaseViewType type,
        Map<String, Object> filters,
        List<DatabaseViewSort> sorts,
        List<String> visibleProperties,
        Instant createdAt,
        Instant updatedAt) {
    public DatabaseView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(createdBy, "createdBy");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("view name cannot be blank");
        name = name.trim();
        Objects.requireNonNull(type, "type");
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        visibleProperties = visibleProperties == null ? List.of() : List.copyOf(visibleProperties);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if (visibleProperties.stream().distinct().count() != visibleProperties.size()) {
            throw new IllegalArgumentException("visible properties must be unique");
        }
    }

    public static DatabaseView create(UUID tenantId, UUID databaseId, UUID tableId, UUID actorId,
                                      String name, DatabaseViewType type, Map<String, Object> filters,
                                      List<DatabaseViewSort> sorts, List<String> visibleProperties, Instant now) {
        return new DatabaseView(UUID.randomUUID(), tenantId, databaseId, tableId, actorId, name, type,
                filters, sorts, visibleProperties, now, now);
    }
}
