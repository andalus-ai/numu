package tech.kayys.syirkah.product.domain.variant;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ProductVariantActivated;
import tech.kayys.syirkah.product.domain.event.ProductVariantArchived;
import tech.kayys.syirkah.product.domain.event.ProductVariantCreated;
import tech.kayys.syirkah.product.domain.event.ProductVariantDiscontinued;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An independently identifiable version of a product (P1.0).
 *
 * The variant references its product by ID only and owns its own
 * lifecycle - a product can be ACTIVE while one variant is already
 * DISCONTINUED.
 */
public final class ProductVariant
        extends AbstractAggregateRoot<ProductVariantId> {

    private final ProductId productId;

    private String code;

    private String name;

    private final List<VariantAttribute> attributes = new ArrayList<>();

    private VariantStatus status;

    private ProductVariant(
            ProductVariantId id,
            ProductId productId,
            String code,
            String name
    ) {
        super(id);

        this.productId = Objects.requireNonNull(
                productId,
                "Product id cannot be null"
        );

        this.code = requireText(code, "Variant code");
        this.name = requireText(name, "Variant name");
        this.status = VariantStatus.DRAFT;
    }

    public static ProductVariant create(
            ProductVariantId id,
            ProductId productId,
            String code,
            String name
    ) {
        ProductVariant variant = new ProductVariant(
                id,
                productId,
                code,
                name
        );

        variant.raise(
                new ProductVariantCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        productId,
                        code,
                        name
                )
        );

        return variant;
    }

    public void activate() {
        requireStatus(VariantStatus.DRAFT);

        status = VariantStatus.ACTIVE;

        raise(
                new ProductVariantActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId
                )
        );
    }

    public void discontinue() {
        requireStatus(VariantStatus.ACTIVE);

        status = VariantStatus.DISCONTINUED;

        raise(
                new ProductVariantDiscontinued(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId
                )
        );
    }

    public void archive() {
        requireStatus(VariantStatus.DISCONTINUED);

        status = VariantStatus.ARCHIVED;

        raise(
                new ProductVariantArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId
                )
        );
    }

    /**
     * Sets or replaces one attribute by code. Attributes describe
     * what the variant IS - they are not a customer's temporary
     * configuration (that belongs to commerce configuration).
     */
    public void changeAttribute(String code, String value) {
        ensureMutable();

        String normalizedCode = requireText(code, "Attribute code");
        String normalizedValue = requireText(
                value,
                "Attribute value"
        );

        VariantAttribute attribute = new VariantAttribute(
                normalizedCode,
                normalizedValue
        );

        attributes.removeIf(existing ->
                existing.code().equals(normalizedCode));

        attributes.add(attribute);
    }

    public void removeAttribute(String code) {
        ensureMutable();

        String normalizedCode = requireText(code, "Attribute code");

        boolean removed = attributes.removeIf(existing ->
                existing.code().equals(normalizedCode));

        if (!removed) {
            throw new BusinessRuleViolation(
                    "Variant attribute not found: " + normalizedCode
            );
        }
    }

    private void ensureMutable() {
        if (status == VariantStatus.ARCHIVED) {
            throw new InvalidStateException(
                    "Archived variant cannot be modified"
            );
        }
    }

    private void requireStatus(VariantStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Variant must be in " + expected
                            + " state but was " + status
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }

    public ProductVariantId variantId() {
        return id();
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

    public List<VariantAttribute> attributes() {
        return List.copyOf(attributes);
    }

    public VariantStatus status() {
        return status;
    }
}