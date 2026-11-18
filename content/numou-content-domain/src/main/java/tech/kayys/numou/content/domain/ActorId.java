package tech.kayys.numou.content.domain;
import java.util.Objects;
import java.util.UUID;
public record ActorId(UUID value) {
 public ActorId { Objects.requireNonNull(value, "value"); }
 public static ActorId of(String value) { return new ActorId(UUID.fromString(value)); }
 @Override public String toString() { return value.toString(); }
}