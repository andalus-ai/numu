package tech.kayys.numou.workspace.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Schema for one generic table inside a tenant-owned workspace database. */
public record DatabaseTable(
        UUID id,
        UUID tenantId,
        UUID databaseId,
        String name,
        long schemaVersion,
        List<DatabaseProperty> properties) {

    public DatabaseTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(databaseId, "databaseId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("table name cannot be blank");
        name = name.trim();
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        properties = properties == null ? List.of() : List.copyOf(properties);

        Set<UUID> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (DatabaseProperty property : properties) {
            Objects.requireNonNull(property, "property");
            if (!ids.add(property.id())) throw new IllegalArgumentException("duplicate property id");
            if (!keys.add(property.key())) throw new IllegalArgumentException("duplicate property key: " + property.key());
        }
        for (DatabaseProperty property : properties) {
            if (property.type() == DatabasePropertyType.RELATION
                    && property.relatedTableId().equals(id)) {
                // Self-relations are supported; this branch documents that they are intentionally allowed.
                continue;
            }
        }
    }

    public DatabaseProperty property(String key) {
        return properties.stream().filter(property -> property.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown property: " + key));
    }
}
