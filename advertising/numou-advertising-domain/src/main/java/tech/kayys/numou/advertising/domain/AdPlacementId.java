package tech.kayys.numou.advertising.domain;
import java.util.Objects;
import java.util.UUID;
public record AdPlacementId(UUID value) {
  public AdPlacementId { Objects.requireNonNull(value); }
  public static AdPlacementId generate(){return new AdPlacementId(UUID.randomUUID());}
}
