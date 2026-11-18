package tech.kayys.numou.content.domain;

import tech.kayys.numou.content.domain.model.ContentTypeId;
import java.time.Instant;
import java.util.Objects;

/** Immutable revision. Schema identity is captured per revision so history remains reproducible. */
public record ContentRevision(int number, String title, String slug, String body,
        ContentTypeId contentTypeId, int schemaVersion, ActorId authorId, Instant createdAt) {
    public ContentRevision {
        if (number < 1) throw new IllegalArgumentException("Revision number must be positive");
        title = required(title, "title");
        slug = required(slug, "slug");
        body = Objects.requireNonNull(body, "body");
        if ((contentTypeId == null) != (schemaVersion == 0))
            throw new IllegalArgumentException("Content type and schema version must both be present or both be absent");
        if (schemaVersion < 0) throw new IllegalArgumentException("Schema version cannot be negative");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    public boolean schemaBound() { return contentTypeId != null; }
}
