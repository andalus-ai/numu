package tech.kayys.numou.developer.apicatalog.application.command;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.domain.*;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import java.util.*;
public final class PublishApiVersionCommandHandler implements CommandHandler<PublishApiVersionCommand,Void> {
 private final ApiProductRepository repository;private final ApiCatalogAuthorizationPort authorization;private final ExecutionContextProvider contextProvider;private final UnitOfWork unitOfWork;private final EventPublisher events;
 public PublishApiVersionCommandHandler(ApiProductRepository repository,ApiCatalogAuthorizationPort authorization,ExecutionContextProvider contextProvider,UnitOfWork unitOfWork,EventPublisher events){this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);this.contextProvider=Objects.requireNonNull(contextProvider);this.unitOfWork=Objects.requireNonNull(unitOfWork);this.events=Objects.requireNonNull(events);}
 @Override public Uni<Void> handle(PublishApiVersionCommand command){var context=contextProvider.current();var tenant=context.requireTenant();var id=ApiProductId.of(UUID.fromString(command.productId()));return authorization.requireCanManage(context,tenant).chain(()->unitOfWork.execute(()->repository.findById(tenant,id).chain(found->{var product=found.orElseThrow(()->ApplicationError.of("API_PRODUCT_NOT_FOUND","API product does not exist in this provider tenant").toException());long expected=product.revision();product.publishVersion(command.version(),context.now());return repository.save(tenant,product,expected).chain(ignored->events.publish(product.pullDomainEvents()));}))); }
}