package tech.kayys.numou.content.domain.media;
import java.util.Objects;
import java.util.UUID;
public record MediaAssetId(UUID value) {
 public MediaAssetId { Objects.requireNonNull(value); }
 public static MediaAssetId generate(){return new MediaAssetId(UUID.randomUUID());}
 public static MediaAssetId of(String value){return new MediaAssetId(UUID.fromString(value));}
 @Override public String toString(){return value.toString();}
}
