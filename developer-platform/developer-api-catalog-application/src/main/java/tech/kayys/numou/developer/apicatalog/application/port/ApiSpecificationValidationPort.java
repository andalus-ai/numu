package tech.kayys.numou.developer.apicatalog.application.port;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.domain.ApiSpecification;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
/** Verifies tenant-owned artifact integrity and OpenAPI semantic validity before publication. */
public interface ApiSpecificationValidationPort {
 Uni<Void> validateAndVerify(TenantId providerTenantId, ApiSpecification specification);
}