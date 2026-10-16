package tech.kayys.syirkah.product.domain.specification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.event.ProductSpecificationChanged;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Product specification aggregate")
class ProductSpecificationTest {

    private static ProductSpecification newSpecification() {
        return ProductSpecification.create(
                ProductSpecificationId.generate(),
                ProductId.generate(),
                "COFFEE-CONFIG",
                "Coffee configuration"
        );
    }

    private static AttributeDefinition sugar() {
        return new AttributeDefinition(
                "sugar",
                "Sugar level",
                AttributeType.ENUM,
                true
        );
    }

    private static OptionGroup size() {
        return new OptionGroup(
                "SIZE",
                "Cup size",
                true,
                List.of(
                        new OptionDefinition("SMALL", "Small"),
                        new OptionDefinition("MEDIUM", "Medium"),
                        new OptionDefinition("LARGE", "Large")
                )
        );
    }

    @Test
    void createsSpecificationWithoutEvents() {
        var specification = newSpecification();

        assertEquals("COFFEE-CONFIG", specification.code());
        assertEquals("Coffee configuration", specification.name());
        assertTrue(specification.attributes().isEmpty());
        assertTrue(specification.optionGroups().isEmpty());
        assertTrue(specification.pullDomainEvents().isEmpty());
    }

    @Test
    void addsAttributeAndRaisesChanged() {
        var specification = newSpecification();

        specification.addAttribute(sugar());

        assertEquals(1, specification.attributes().size());

        var changed = assertInstanceOf(
                ProductSpecificationChanged.class,
                specification.pullDomainEvents().getFirst()
        );

        assertEquals(
                "product.product-specification-changed",
                changed.eventType()
        );
    }

    @Test
    void rejectsDuplicateAttribute() {
        var specification = newSpecification();

        specification.addAttribute(sugar());

        assertThrows(
                BusinessRuleViolation.class,
                () -> specification.addAttribute(sugar())
        );
    }

    @Test
    void removesAttributeOnlyWhenPresent() {
        var specification = newSpecification();

        specification.addAttribute(sugar());
        specification.pullDomainEvents();

        specification.removeAttribute("sugar");

        assertTrue(specification.attributes().isEmpty());
        assertEquals(1, specification.pullDomainEvents().size());

        // Removing an unknown attribute is a silent no-op.
        specification.removeAttribute("sugar");
        assertTrue(specification.pullDomainEvents().isEmpty());
    }

    @Test
    void addsOptionGroupAndRejectsDuplicate() {
        var specification = newSpecification();

        specification.addOptionGroup(size());

        assertEquals(1, specification.optionGroups().size());
        assertEquals(1, specification.pullDomainEvents().size());

        assertThrows(
                BusinessRuleViolation.class,
                () -> specification.addOptionGroup(size())
        );
    }

    @Test
    void removesOptionGroup() {
        var specification = newSpecification();

        specification.addOptionGroup(size());
        specification.pullDomainEvents();

        specification.removeOptionGroup("SIZE");

        assertTrue(specification.optionGroups().isEmpty());
        assertEquals(1, specification.pullDomainEvents().size());
    }

    @Test
    void optionGroupMayBeCreatedEmptyThenFilled() {
        var group = OptionGroup.create("SIZE", "Cup size", true, false);

        assertTrue(group.options().isEmpty());
        assertFalse(group.multiSelect());

        var withOption = group.withOption(
                new OptionDefinition("SMALL", "Small"));

        assertEquals(1, withOption.options().size());
    }

    @Test
    void specificationIsLinkedToItsProduct() {
        ProductId productId = ProductId.generate();

        var specification = ProductSpecification.create(
                ProductSpecificationId.generate(),
                productId,
                "COFFEE-CONFIG",
                "Coffee configuration"
        );

        specification.addAttribute(sugar());

        var changed = assertInstanceOf(
                ProductSpecificationChanged.class,
                specification.pullDomainEvents().getFirst()
        );

        assertEquals(productId, changed.productId());
    }
}