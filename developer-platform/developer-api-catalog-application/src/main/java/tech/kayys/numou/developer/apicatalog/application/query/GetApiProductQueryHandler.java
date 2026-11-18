package tech.kayys.numou.developer.apicatalog.application.query;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.domain.ApiProductId;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import java.util.*;
public final class GetApiProductQueryHandler implements QueryHandler<GetApiProductQuery,ApiProductView> {
 private final ApiProductRepository repository;private final ApiCatalogAuthorizationPort authorization;private final ExecutionContextProvider contextProvider;
 public GetApiProductQueryHandler(ApiProductRepository repository,ApiCatalogAuthorizationPort authorization,ExecutionContextProvider contextProvider){this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);this.contextProvider=Objects.requireNonNull(contextProvider);}
 @Override public Uni<ApiProductView> handle(GetApiProductQuery query){var context=contextProvider.current();var tenant=context.requireTenant();var id=ApiProductId.of(UUID.fromString(query.productId()));return authorization.requireCanRead(context,tenant).chain(()->repository.findById(tenant,id).map(found->found.map(ApiProductView::from).orElseThrow(()->ApplicationError.of("API_PRODUCT_NOT_FOUND","API product does not exist in this provider tenant").toException())));}
}