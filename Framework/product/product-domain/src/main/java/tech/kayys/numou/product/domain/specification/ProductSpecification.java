package tech.kayys.syirkah.product.domain.specification;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.event.ProductSpecificationChanged;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A reusable specification for a product (P1.0).
 *
 * Kept mutable on purpose: specifications evolve (v1 Size+Sugar,
 * v2 +Ice, v3 +Milk) and every evolution raises
 * {@code ProductSpecificationChanged} so downstream capabilities
 * can react.
 */
public final class ProductSpecification
        extends AbstractAggregateRoot<ProductSpecificationId> {

    private final ProductId productId;

    private final String code;

    private String name;

    private final List<AttributeDefinition> attributes =
            new ArrayList<>();

    private final List<OptionGroup> optionGroups = new ArrayList<>();

    private ProductSpecification(
            ProductSpecificationId id,
            ProductId productId,
            String code,
            String name
    ) {
        super(id);

        this.productId = Objects.requireNonNull(
                productId,
                "Product id cannot be null"
        );

        this.code = requireText(code, "Specification code");
        this.name = requireText(name, "Specification name");
    }

    public static ProductSpecification create(
            ProductSpecificationId id,
            ProductId productId,
            String code,
            String name
    ) {
        return new ProductSpecification(id, productId, code, name);
    }

    public void rename(String newName) {
        this.name = requireText(newName, "Specification name");

        raiseChanged();
    }

    public void addAttribute(AttributeDefinition definition) {
        Objects.requireNonNull(
                definition,
                "Attribute definition cannot be null"
        );

        boolean exists = attributes.stream()
                .anyMatch(existing ->
                        existing.code().equals(definition.code()));

        if (exists) {
            throw new BusinessRuleViolation(
                    "Attribute already exists: " + definition.code()
            );
        }

        attributes.add(definition);

        raiseChanged();
    }

    public void removeAttribute(String code) {
        boolean removed = attributes.removeIf(existing ->
                existing.code().equals(code));

        if (removed) {
            raiseChanged();
        }
    }

    public void addOptionGroup(OptionGroup optionGroup) {
        Objects.requireNonNull(
                optionGroup,
                "Option group cannot be null"
        );

        boolean exists = optionGroups.stream()
                .anyMatch(existing ->
                        existing.code().equals(optionGroup.code()));

        if (exists) {
            throw new BusinessRuleViolation(
                    "Option group already exists: "
                            + optionGroup.code()
            );
        }

        optionGroups.add(optionGroup);

        raiseChanged();
    }

    public void removeOptionGroup(String code) {
        boolean removed = optionGroups.removeIf(existing ->
                existing.code().equals(code));

        if (removed) {
            raiseChanged();
        }
    }

    public void addOption(String groupCode, OptionDefinition option) {
        Objects.requireNonNull(option, "Option cannot be null");
        String normalizedGroup = requireText(groupCode, "Option group code");

        for (int i = 0; i < optionGroups.size(); i++) {
            OptionGroup group = optionGroups.get(i);
            if (group.code().equals(normalizedGroup)) {
                try {
                    optionGroups.set(i, group.withOption(option));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessRuleViolation(ex.getMessage());
                }
                raiseChanged();
                return;
            }
        }

        throw new BusinessRuleViolation(
                "Option group not found: " + normalizedGroup
        );
    }

    public void removeOption(String groupCode, String optionCode) {
        String normalizedGroup = requireText(groupCode, "Option group code");

        for (int i = 0; i < optionGroups.size(); i++) {
            OptionGroup group = optionGroups.get(i);
            if (group.code().equals(normalizedGroup)) {
                try {
                    optionGroups.set(i, group.withoutOption(optionCode));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessRuleViolation(ex.getMessage());
                }
                raiseChanged();
                return;
            }
        }

        throw new BusinessRuleViolation(
                "Option group not found: " + normalizedGroup
        );
    }

    private void raiseChanged() {
        raise(
                new ProductSpecificationChanged(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId
                )
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }

    public ProductId productId() {
        return productId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public List<AttributeDefinition> attributes() {
        return List.copyOf(attributes);
    }

    public List<OptionGroup> optionGroups() {
        return List.copyOf(optionGroups);
    }
}