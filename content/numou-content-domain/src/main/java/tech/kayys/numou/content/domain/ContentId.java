package tech.kayys.numou.content.domain;
import java.util.Objects;
import java.util.UUID;
public record ContentId(UUID value) {
 public ContentId { Objects.requireNonNull(value, "value"); }
 public static ContentId newId() { return new ContentId(UUID.randomUUID()); }
 public static ContentId of(String value) { return new ContentId(UUID.fromString(value)); }
 @Override public String toString() { return value.toString(); }
}