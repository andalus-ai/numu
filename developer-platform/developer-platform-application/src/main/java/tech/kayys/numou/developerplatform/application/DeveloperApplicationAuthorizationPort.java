package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.developerplatform.domain.DeveloperApplicationId;
import java.util.concurrent.CompletionStage;
/** Host authorization adapter enforces tenant membership and operation-specific permissions. */
public interface DeveloperApplicationAuthorizationPort{
 CompletionStage<Void> requireCanRegister(DeveloperApplicationActor actor);
 CompletionStage<Void> requireCanList(DeveloperApplicationActor actor);
 CompletionStage<Void> requireCanRead(DeveloperApplicationActor actor,DeveloperApplicationId id);
 CompletionStage<Void> requireCanManage(DeveloperApplicationActor actor,DeveloperApplicationId id);
}