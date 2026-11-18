package tech.kayys.numou.advertising.domain;
import java.util.Objects;
import java.util.UUID;
public record CreativeId(UUID value) {
  public CreativeId { Objects.requireNonNull(value); }
  public static CreativeId generate(){ return new CreativeId(UUID.randomUUID()); }
}
