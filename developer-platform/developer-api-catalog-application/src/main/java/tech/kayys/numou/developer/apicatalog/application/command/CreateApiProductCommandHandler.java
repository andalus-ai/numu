package tech.kayys.numou.developer.apicatalog.application.command;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.*;
import tech.kayys.numou.developer.apicatalog.domain.*;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import java.util.Objects;
public final class CreateApiProductCommandHandler implements CommandHandler<CreateApiProductCommand,ApiProductId> {
 private final ApiProductRepository repository; private final ApiCatalogAuthorizationPort authorization; private final ExecutionContextProvider contextProvider; private final UnitOfWork unitOfWork; private final EventPublisher events;
 public CreateApiProductCommandHandler(ApiProductRepository repository,ApiCatalogAuthorizationPort authorization,ExecutionContextProvider contextProvider,UnitOfWork unitOfWork,EventPublisher events){this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);this.contextProvider=Objects.requireNonNull(contextProvider);this.unitOfWork=Objects.requireNonNull(unitOfWork);this.events=Objects.requireNonNull(events);}
 @Override public Uni<ApiProductId> handle(CreateApiProductCommand command){var context=contextProvider.current();var tenant=context.requireTenant();return authorization.requireCanManage(context,tenant).chain(()->unitOfWork.execute(()->repository.findBySlug(tenant,command.slug().trim().toLowerCase(java.util.Locale.ROOT)).chain(existing->{if(existing.isPresent())return Uni.createFrom().failure(ApplicationError.of("API_PRODUCT_SLUG_CONFLICT","An API product with this slug already exists in the provider tenant").toException());var product=ApiProduct.create(ApiProductId.newId(),tenant.value(),command.name(),command.slug(),command.description(),context.now());return repository.save(tenant,product,0).chain(ignored->events.publish(product.pullDomainEvents())).replaceWith(product.id());}))); }
}