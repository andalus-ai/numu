package tech.kayys.numou.developer.apicatalog.application.query;
import tech.kayys.numou.developer.apicatalog.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ApiProductView(UUID id,UUID providerTenantId,String name,String slug,String description,ApiProductStatus status,long revision,Instant createdAt,Instant updatedAt,List<ApiVersionView> versions) {
 public ApiProductView { versions=List.copyOf(versions); }
 public static ApiProductView from(ApiProduct product) {
  var versions=product.versions().values().stream().sorted(java.util.Comparator.comparing(ApiVersion::version)).map(v->new ApiVersionView(v.version(),v.specification().openApiVersion(),v.specification().artifactReference(),v.specification().sha256(),v.status(),v.scopes().values().stream().sorted(java.util.Comparator.comparing(ApiScope::name)).map(s->new ScopeView(s.name(),s.description())).toList())).toList();
  return new ApiProductView(product.id().value(),product.providerTenantId(),product.name(),product.slug(),product.description(),product.status(),product.revision(),product.createdAt(),product.updatedAt(),versions);
 }
 public record ApiVersionView(String version,String openApiVersion,String artifactReference,String sha256,ApiVersionStatus status,List<ScopeView> scopes) { public ApiVersionView { scopes=List.copyOf(scopes); } }
 public record ScopeView(String name,String description) {}
}