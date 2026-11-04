package tech.kayys.numou.developer.apicatalog.application.port;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
/** Authorization boundary. Implementations authorize the actor in trusted execution context. */
public interface ApiCatalogAuthorizationPort {
 Uni<Void> requireCanManage(ExecutionContext context, TenantId providerTenantId);
 Uni<Void> requireCanRead(ExecutionContext context, TenantId providerTenantId);
}