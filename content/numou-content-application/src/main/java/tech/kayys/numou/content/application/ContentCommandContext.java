package tech.kayys.numou.content.application;
import tech.kayys.numou.content.domain.ActorId;
import tech.kayys.numou.content.domain.TenantId;
import java.util.Objects;
/** Trusted context resolved by the host authentication/tenant boundary, never from content payload fields. */
public record ContentCommandContext(TenantId tenantId, ActorId actorId) {
 public ContentCommandContext { Objects.requireNonNull(tenantId); Objects.requireNonNull(actorId); }
}