package tech.kayys.syirkah.commerce.configuration.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.configuration.application.command.CompleteProductConfigurationCommand;
import tech.kayys.syirkah.commerce.configuration.application.command.CreateProductConfigurationCommand;
import tech.kayys.syirkah.commerce.configuration.application.command.SelectOptionCommand;
import tech.kayys.syirkah.commerce.configuration.application.handler.CompleteProductConfigurationHandler;
import tech.kayys.syirkah.commerce.configuration.application.handler.CreateProductConfigurationHandler;
import tech.kayys.syirkah.commerce.configuration.application.handler.SelectOptionHandler;
import tech.kayys.syirkah.commerce.configuration.application.support.StubSpecificationLookup;
import tech.kayys.syirkah.commerce.configuration.domain.ConfigurationStatus;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductType;
import tech.kayys.syirkah.product.domain.specification.OptionDefinition;
import tech.kayys.syirkah.product.domain.specification.OptionGroup;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Configuration application commands (product02)")
class ConfigurationCommandHandlerTest {

    private final InMemoryProductStore products = new InMemoryProductStore();
    private final InMemorySpecStore specifications = new InMemorySpecStore();
    private final InMemoryConfigStore configurations = new InMemoryConfigStore();
    private final NoOpEvents events = new NoOpEvents();

    @Test
    void createSelectAndComplete() {
        ProductId productId = ProductId.generate();
        ProductSpecificationId specId = ProductSpecificationId.generate();
        products.save(Product.create(
                productId, "COFFEE", "Coffee", null, ProductType.PHYSICAL));
        var spec = ProductSpecification.create(
                specId, productId, "COFFEE-SPEC", "Coffee");
        spec.addOptionGroup(new OptionGroup(
                "SIZE", "Size", true,
                List.of(
                        new OptionDefinition("LARGE", "Large"),
                        new OptionDefinition("SMALL", "Small"))));
        specifications.save(spec);

        var stubs = new StubSpecificationLookup();
        stubs.add(spec);

        var create = new CreateProductConfigurationHandler(
                products, specifications, configurations, events);
        var select = new SelectOptionHandler(configurations, events);
        var complete = new CompleteProductConfigurationHandler(
                configurations, stubs, events);

        var configurationId = create.handle(
                new CreateProductConfigurationCommand(productId, specId)
        ).await().indefinitely().orElseThrow();

        select.handle(new SelectOptionCommand(configurationId, "SIZE", "LARGE"))
                .await().indefinitely().orElseThrow();

        complete.handle(new CompleteProductConfigurationCommand(configurationId))
                .await().indefinitely().orElseThrow();

        var saved = configurations.findById(configurationId)
                .toCompletableFuture().join().orElseThrow();
        assertEquals(ConfigurationStatus.COMPLETED, saved.status());
        assertEquals(1, saved.selections().size());
    }

    private static final class NoOpEvents implements EventPublisher {
        @Override
        public io.smallrye.mutiny.Uni<Void> publish(List<DomainEvent> domainEvents) {
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        }
    }

    private static final class InMemoryConfigStore
            implements ProductConfigurationRepository {
        private final Map<ProductConfigurationId, ProductConfiguration> store =
                new ConcurrentHashMap<>();

        @Override
        public CompletionStage<ProductConfiguration> save(
                ProductConfiguration configuration
        ) {
            store.put(configuration.id(), configuration);
            return CompletableFuture.completedFuture(configuration);
        }

        @Override
        public CompletionStage<Optional<ProductConfiguration>> findById(
                ProductConfigurationId id
        ) {
            return CompletableFuture.completedFuture(
                    Optional.ofNullable(store.get(id)));
        }
    }

    private static final class InMemoryProductStore implements ProductRepository {
        private final Map<ProductId, Product> store = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Product> save(Product aggregate) {
            store.put(aggregate.id(), aggregate);
            return CompletableFuture.completedFuture(aggregate);
        }

        @Override
        public CompletionStage<Optional<Product>> findById(ProductId id) {
            return CompletableFuture.completedFuture(
                    Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletionStage<Boolean> existsById(ProductId id) {
            return CompletableFuture.completedFuture(store.containsKey(id));
        }

        @Override
        public CompletionStage<Void> delete(Product aggregate) {
            store.remove(aggregate.id());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteById(ProductId id) {
            store.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Optional<Product>> findByCode(String code) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<Boolean> existsByCode(String code) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private static final class InMemorySpecStore
            implements ProductSpecificationRepository {
        private final Map<ProductSpecificationId, ProductSpecification> store =
                new ConcurrentHashMap<>();

        @Override
        public CompletionStage<ProductSpecification> save(
                ProductSpecification aggregate
        ) {
            store.put(aggregate.id(), aggregate);
            return CompletableFuture.completedFuture(aggregate);
        }

        @Override
        public CompletionStage<Optional<ProductSpecification>> findById(
                ProductSpecificationId id
        ) {
            return CompletableFuture.completedFuture(
                    Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletionStage<Boolean> existsById(ProductSpecificationId id) {
            return CompletableFuture.completedFuture(store.containsKey(id));
        }

        @Override
        public CompletionStage<Void> delete(ProductSpecification aggregate) {
            store.remove(aggregate.id());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteById(ProductSpecificationId id) {
            store.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<ProductSpecification>> findByProductId(
                ProductId productId
        ) {
            return CompletableFuture.completedFuture(store.values().stream()
                    .filter(s -> s.productId().equals(productId))
                    .toList());
        }
    }
}
