package tech.kayys.numou.developer.apicatalog.application.port;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.domain.ApiProduct;
import tech.kayys.numou.developer.apicatalog.domain.ApiProductId;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
/** Aggregate repository. Every lookup and write is explicitly scoped to the provider tenant. */
public interface ApiProductRepository {
 Uni<Optional<ApiProduct>> findById(TenantId tenantId, ApiProductId id);
 Uni<Optional<ApiProduct>> findBySlug(TenantId tenantId, String slug);
 Uni<Page<ApiProduct>> findAll(TenantId tenantId, PageRequest pageRequest);
 Uni<Void> save(TenantId tenantId, ApiProduct product, long expectedRevision);
}