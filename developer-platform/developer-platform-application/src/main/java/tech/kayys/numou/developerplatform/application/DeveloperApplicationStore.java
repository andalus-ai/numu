package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.developerplatform.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.List;import java.util.Optional;import java.util.UUID;import java.util.concurrent.CompletionStage;
/** All operations require tenant scope; adapters must additionally enforce PostgreSQL RLS. */
public interface DeveloperApplicationStore{
 CompletionStage<CreateResult> registerIfAbsent(DeveloperApplication app,String idempotencyHash,String fingerprint,DeveloperApplicationEvent event);
 CompletionStage<Optional<DeveloperApplication>> find(TenantId tenant,DeveloperApplicationId id);
 CompletionStage<List<DeveloperApplication>> list(TenantId tenant,UUID afterId,int limit);
 CompletionStage<Boolean> update(DeveloperApplication app,long expectedRevision,DeveloperApplicationEvent event);
 record CreateResult(Outcome outcome,Optional<DeveloperApplication> application){
  public enum Outcome{CREATED,REPLAYED,CONFLICT}
  public CreateResult{if(outcome==null||application==null)throw new IllegalArgumentException("result fields required");if(outcome!=Outcome.CONFLICT&&application.isEmpty())throw new IllegalArgumentException("application required");}
 }
}