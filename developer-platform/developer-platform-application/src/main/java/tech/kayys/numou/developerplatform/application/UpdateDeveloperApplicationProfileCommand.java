package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.developerplatform.domain.DeveloperApplicationId;import java.util.Objects;
public record UpdateDeveloperApplicationProfileCommand(DeveloperApplicationId applicationId,long expectedRevision,String name,String description){
 public UpdateDeveloperApplicationProfileCommand{Objects.requireNonNull(applicationId);if(expectedRevision<1)throw new IllegalArgumentException("expectedRevision must be positive");}
}