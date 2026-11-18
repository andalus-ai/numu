package tech.kayys.numou.content.domain.model;

import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable schema snapshot. A changed field contract always creates a new version. */
public record ContentTypeSchema(
        TenantId tenantId, ContentTypeId typeId, String key, String displayName, int version,
        SchemaStatus status, List<ContentFieldDefinition> fields, Instant createdAt, Instant publishedAt) {
    public ContentTypeSchema {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(typeId, "typeId");
        if (key == null || !key.matches("[a-z][a-z0-9_]{1,62}"))
            throw new IllegalArgumentException("Content type key must be a lowercase identifier");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName must not be blank");
        if (version < 1) throw new IllegalArgumentException("Schema version must be positive");
        Objects.requireNonNull(status, "status");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == SchemaStatus.DRAFT && publishedAt != null)
            throw new IllegalArgumentException("Draft schema cannot have publishedAt");
        if (status == SchemaStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("Published schema requires publishedAt");
        Set<String> names = new HashSet<>();
        for (ContentFieldDefinition field : fields)
            if (!names.add(field.name())) throw new IllegalArgumentException("Duplicate field: " + field.name());
    }

    public static ContentTypeSchema create(TenantId tenantId, ContentTypeId typeId, String key,
            String displayName, List<ContentFieldDefinition> fields, Instant now) {
        return new ContentTypeSchema(tenantId, typeId, key, displayName, 1,
                SchemaStatus.DRAFT, fields, now, null);
    }

    public ContentTypeSchema nextDraft(List<ContentFieldDefinition> nextFields, Instant now) {
        if (status != SchemaStatus.PUBLISHED)
            throw new IllegalStateException("Only a published schema can be used as the base for a new version");
        return new ContentTypeSchema(tenantId, typeId, key, displayName, version + 1,
                SchemaStatus.DRAFT, nextFields, now, null);
    }

    public ContentTypeSchema publish(Instant now) {
        if (status != SchemaStatus.DRAFT) throw new IllegalStateException("Only a draft schema can be published");
        return new ContentTypeSchema(tenantId, typeId, key, displayName, version,
                SchemaStatus.PUBLISHED, fields, createdAt, now);
    }
}
