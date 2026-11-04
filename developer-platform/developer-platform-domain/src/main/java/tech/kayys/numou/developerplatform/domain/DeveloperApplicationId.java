package tech.kayys.numou.developerplatform.domain;
import java.util.Objects;
import java.util.UUID;
public record DeveloperApplicationId(UUID value) {
 public DeveloperApplicationId { Objects.requireNonNull(value,"application id is required"); }
 public static DeveloperApplicationId generate(){ return new DeveloperApplicationId(UUID.randomUUID()); }
}