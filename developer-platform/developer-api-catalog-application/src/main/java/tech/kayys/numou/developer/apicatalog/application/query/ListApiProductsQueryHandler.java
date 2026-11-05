package tech.kayys.numou.developer.apicatalog.application.query;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import java.util.Objects;
public final class ListApiProductsQueryHandler implements QueryHandler<ListApiProductsQuery,Page<ApiProductView>> {
 private final ApiProductRepository repository;private final ApiCatalogAuthorizationPort authorization;private final ExecutionContextProvider contextProvider;
 public ListApiProductsQueryHandler(ApiProductRepository repository,ApiCatalogAuthorizationPort authorization,ExecutionContextProvider contextProvider){this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);this.contextProvider=Objects.requireNonNull(contextProvider);}
 @Override public Uni<Page<ApiProductView>> handle(ListApiProductsQuery query){var context=contextProvider.current();var tenant=context.requireTenant();return authorization.requireCanRead(context,tenant).chain(()->repository.findAll(tenant,query.pageRequest()).map(page->new Page<>(page.content().stream().map(ApiProductView::from).toList(),page.totalElements(),page.page(),page.size())));}
}