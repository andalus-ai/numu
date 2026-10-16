package tech.kayys.syirkah.commerce.configuration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.OptionDefinition;
import tech.kayys.syirkah.product.domain.specification.OptionGroup;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigurationValidator against ProductSpecification option groups")
class ConfigurationValidatorTest {

    private final ProductId productId = ProductId.generate();

    private ProductSpecification specification() {
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
        spec.addOptionGroup(new OptionGroup(
                "MILK", "Milk", false,
                List.of(
                        new OptionDefinition("NONE", "None"),
                        new OptionDefinition("OAT", "Oat"))));
        return spec;
    }

    @Test
    void acceptsCompleteValidSelection() {
        var configuration = ProductConfiguration.of(
                productId,
                List.of(
                        new SelectedOption("SIZE", "LARGE"),
                        new SelectedOption("MILK", "OAT")));

        var result = ConfigurationValidator.validate(specification(), configuration);

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void rejectsMissingRequiredGroup() {
        var configuration = ProductConfiguration.of(
                productId,
                List.of(new SelectedOption("MILK", "OAT")));

        var result = ConfigurationValidator.validate(specification(), configuration);

        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
    }

    @Test
    void rejectsUnknownOption() {
        var configuration = ProductConfiguration.of(
                productId,
                List.of(
                        new SelectedOption("SIZE", "XXL"),
                        new SelectedOption("MILK", "OAT")));

        var result = ConfigurationValidator.validate(specification(), configuration);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsUnknownGroup() {
        var configuration = ProductConfiguration.of(
                productId,
                List.of(
                        new SelectedOption("SIZE", "LARGE"),
                        new SelectedOption("SYRUP", "VANILLA")));

        var result = ConfigurationValidator.validate(specification(), configuration);

        assertFalse(result.isValid());
    }
}
