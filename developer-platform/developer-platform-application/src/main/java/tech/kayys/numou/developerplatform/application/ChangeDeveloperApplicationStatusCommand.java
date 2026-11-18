package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.developerplatform.domain.DeveloperApplicationId;
import tech.kayys.numou.developerplatform.domain.DeveloperApplicationStatus;
import java.util.Objects;
public record ChangeDeveloperApplicationStatusCommand(DeveloperApplicationId applicationId,long expectedRevision,DeveloperApplicationStatus targetStatus){
 public ChangeDeveloperApplicationStatusCommand{Objects.requireNonNull(applicationId);Objects.requireNonNull(targetStatus);if(expectedRevision<1)throw new IllegalArgumentException("expectedRevision must be positive");}
}