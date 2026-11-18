package tech.kayys.numou.developer.apicatalog.application.command;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.domain.ApiProductId;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import java.util.*;
public final class DeprecateApiVersionCommandHandler implements CommandHandler<DeprecateApiVersionCommand,Void> {
 private final ApiProductRepository repository;private final ApiCatalogAuthorizationPort authorization;private final ExecutionContextProvider contextProvider;private final UnitOfWork unitOfWork;
 public DeprecateApiVersionCommandHandler(ApiProductRepository repository,ApiCatalogAuthorizationPort authorization,ExecutionContextProvider contextProvider,UnitOfWork unitOfWork){this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);this.contextProvider=Objects.requireNonNull(contextProvider);this.unitOfWork=Objects.requireNonNull(unitOfWork);}
 @Override public Uni<Void> handle(DeprecateApiVersionCommand command){var context=contextProvider.current();var tenant=context.requireTenant();var id=ApiProductId.of(UUID.fromString(command.productId()));return authorization.requireCanManage(context,tenant).chain(()->unitOfWork.execute(()->repository.findById(tenant,id).chain(found->{var product=found.orElseThrow(()->ApplicationError.of("API_PRODUCT_NOT_FOUND","API product does not exist in this provider tenant").toException());long expected=product.revision();product.deprecateVersion(command.version(),context.now());return repository.save(tenant,product,expected);}))); }
}