package tech.kayys.numou.content.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ContentTypeId(UUID value) {
    public ContentTypeId { Objects.requireNonNull(value, "value"); }
    public static ContentTypeId newId() { return new ContentTypeId(UUID.randomUUID()); }
    public static ContentTypeId of(String value) { return new ContentTypeId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
