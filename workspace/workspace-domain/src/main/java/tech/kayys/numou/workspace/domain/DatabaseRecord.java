package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped generic row with schema validation and optimistic-concurrency revision. */
public record DatabaseRecord(
        UUID id,
        UUID tenantId,
        UUID databaseId,
        UUID tableId,
        long revision,
        Map<String, Object> values,
        Instant createdAt,
        Instant updatedAt) {

    public DatabaseRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        values = immutableValues(values);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static DatabaseRecord create(UUID tenantId, UUID databaseId, DatabaseTable table,
                                        Map<String, Object> input, Instant now) {
        Objects.requireNonNull(table, "table");
        if (!table.tenantId().equals(tenantId) || !table.databaseId().equals(databaseId)) {
            throw new IllegalArgumentException("table does not belong to the requested tenant database");
        }
        return new DatabaseRecord(UUID.randomUUID(), tenantId, databaseId, table.id(), 1,
                validate(table, input), now, now);
    }

    public DatabaseRecord update(DatabaseTable table, long expectedRevision,
                                 Map<String, Object> input, Instant now) {
        if (expectedRevision != revision) {
            throw new IllegalStateException("record revision conflict: expected " + expectedRevision + ", actual " + revision);
        }
        if (!table.id().equals(tableId) || !table.tenantId().equals(tenantId)
                || !table.databaseId().equals(databaseId)) {
            throw new IllegalArgumentException("table does not own this record");
        }
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("now cannot precede updatedAt");
        return new DatabaseRecord(id, tenantId, databaseId, tableId, revision + 1,
                validate(table, input), createdAt, now);
    }

    private static Map<String, Object> validate(DatabaseTable table, Map<String, Object> input) {
        Map<String, Object> supplied = input == null ? Map.of() : input;
        Map<String, Object> canonical = new LinkedHashMap<>();
        for (String key : supplied.keySet()) {
            if (table.properties().stream().noneMatch(property -> property.key().equals(key))) {
                throw new IllegalArgumentException("unknown property: " + key);
            }
        }
        for (DatabaseProperty property : table.properties()) {
            Object value = property.validateValue(supplied.get(property.key()));
            if (value != null) canonical.put(property.key(), value);
        }
        return Map.copyOf(canonical);
    }

    private static Map<String, Object> immutableValues(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            Objects.requireNonNull(key, "value key");
            if (value instanceof java.util.List<?> list) copy.put(key, java.util.List.copyOf(list));
            else if (value instanceof String || value instanceof java.math.BigDecimal
                    || value instanceof Boolean || value instanceof java.util.UUID
                    || value instanceof java.time.LocalDate || value instanceof Instant) copy.put(key, value);
            else throw new IllegalArgumentException("unsupported stored value type for key '" + key + "'");
        });
        return Map.copyOf(copy);
    }
}
