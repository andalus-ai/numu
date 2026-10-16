package tech.kayys.numou.commerce.configuration.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.commerce.configuration.application.handler.ValidateConfigurationHandler;
import tech.kayys.numou.commerce.configuration.application.query.ValidateConfigurationQuery;
import tech.kayys.numou.commerce.configuration.application.support.StubSpecificationLookup;
import tech.kayys.numou.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.numou.commerce.configuration.domain.SelectedOption;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.specification.OptionDefinition;
import tech.kayys.numou.product.domain.specification.OptionGroup;
import tech.kayys.numou.product.domain.specification.ProductSpecification;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidateConfigurationHandler")
class ValidateConfigurationHandlerTest {

    @Test
    void succeedsWhenSelectionMatchesSpecification() {
        var productId = ProductId.generate();
        var lookup = new StubSpecificationLookup();
        lookup.add(specification(productId));
        var handler = new ValidateConfigurationHandler(lookup);

        var result = handler.handle(new ValidateConfigurationQuery(
                ProductConfiguration.of(
                        productId,
                        List.of(new SelectedOption("SIZE", "LARGE")))))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.orElseThrow().isValid());
    }

    @Test
    void failsWhenNoSpecificationExists() {
        var handler = new ValidateConfigurationHandler(new StubSpecificationLookup());

        var result = handler.handle(new ValidateConfigurationQuery(
                ProductConfiguration.empty(ProductId.generate())))
                .await().indefinitely();

        assertTrue(result.isFailure());
    }

    private static ProductSpecification specification(ProductId productId) {
        var spec = ProductSpecification.create(
                ProductSpecificationId.generate(),
                productId,
                "COFFEE-SPEC",
                "Coffee specification");
        spec.addOptionGroup(new OptionGroup(
                "SIZE", "Size", true,
                List.of(
                        new OptionDefinition("REGULAR", "Regular"),
                        new OptionDefinition("LARGE", "Large"))));
        return spec;
    }
}
