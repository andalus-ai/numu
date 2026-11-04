package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Objects;
/** Supplied only by a host adapter backed by a verified security context. */
public record DeveloperApplicationActor(TenantId tenantId,String actorId,String correlationId){
 public DeveloperApplicationActor{Objects.requireNonNull(tenantId,"verified tenant is required");if(actorId==null||actorId.isBlank()||actorId.trim().length()>200)throw new IllegalArgumentException("actorId is required");actorId=actorId.trim();correlationId=correlationId==null?"":correlationId.trim();if(correlationId.length()>200)throw new IllegalArgumentException("correlationId too long");}
}