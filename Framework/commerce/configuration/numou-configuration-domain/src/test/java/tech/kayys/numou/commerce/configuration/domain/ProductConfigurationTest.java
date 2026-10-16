package tech.kayys.syirkah.commerce.configuration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.OptionGroupId;
import tech.kayys.syirkah.product.domain.specification.OptionId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProductConfiguration aggregate (product02)")
class ProductConfigurationTest {

    @Test
    void selectDeselectClearAndComplete() {
        var productId = ProductId.generate();
        var specId = ProductSpecificationId.generate();
        var configuration = ProductConfiguration.create(
                ProductConfigurationId.generate(), productId, specId);

        configuration.selectOption(new SelectedOption("SIZE", "LARGE"));
        configuration.selectOption(new SelectedOption("MILK", "OAT"));
        assertEquals(2, configuration.selections().size());
        assertEquals(ConfigurationStatus.DRAFT, configuration.status());

        configuration.deselectOption(OptionGroupId.of("MILK"), OptionId.of("OAT"));
        assertEquals(1, configuration.selections().size());

        configuration.complete();
        assertEquals(ConfigurationStatus.COMPLETED, configuration.status());
        assertThrows(BusinessRuleViolation.class,
                () -> configuration.selectOption(new SelectedOption("SIZE", "REGULAR")));
    }

    @Test
    void allowsMultiSelectWithoutReplacing() {
        var configuration = ProductConfiguration.create(
                ProductConfigurationId.generate(),
                ProductId.generate(),
                ProductSpecificationId.generate());

        configuration.selectOption(new SelectedOption("TOPPING", "CREAM"));
        configuration.selectOption(new SelectedOption("TOPPING", "CARAMEL"));

        assertEquals(2, configuration.selections().size());
        assertTrue(configuration.pullDomainEvents().size() >= 3);
    }
}
