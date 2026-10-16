package tech.kayys.syirkah.product.domain.product;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ProductActivated;
import tech.kayys.syirkah.product.domain.event.ProductArchived;
import tech.kayys.syirkah.product.domain.event.ProductCreated;
import tech.kayys.syirkah.product.domain.event.ProductDiscontinued;
import tech.kayys.syirkah.product.domain.event.ProductIdentifierAdded;
import tech.kayys.syirkah.product.domain.event.ProductIdentifierRemoved;
import tech.kayys.syirkah.product.domain.event.ProductRenamed;
import tech.kayys.syirkah.product.domain.event.ProductUpdated;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The core Product aggregate: identity, type, lifecycle and
 * external identifiers (P1.0, product01.md).
 *
 * Deliberately absent: price, tax, inventory, promotion,
 * subscription, customer, channel, GL account. Those belong to
 * separate capabilities that consume this aggregate's IDs and
 * events (product00.md).
 *
 * Variants, specification and SKU are separate consistency
 * boundaries that reference this product by {@link ProductId}.
 */
public final class Product extends AbstractAggregateRoot<ProductId> {

    /**
     * Maximum length of the product name.
     *
     * <p>Carried over from the retired {@code product-core} value object
     * {@code ProductName} during the Product 1.0 consolidation, so removing
     * that module does not silently drop the invariant.</p>
     */
    public static final int MAX_NAME_LENGTH = 512;

    /**
     * Maximum length of the product description.
     *
     * <p>Carried over from the retired {@code product-core} value object
     * {@code ProductDescription} during the Product 1.0 consolidation.</p>
     */
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;

    private String code;

    private String name;

    private String description;

    private final ProductType type;

    private ProductStatus status;

    private final List<ProductIdentifier> identifiers = new ArrayList<>();

    private Product(
            ProductId id,
            String code,
            String name,
            String description,
            ProductType type
    ) {
        super(id);

        this.code = requireText(code, "Product code");
        this.name = requireName(name);
        this.description = normalizeDescription(description);
        this.type = Objects.requireNonNull(
                type,
                "Product type cannot be null"
        );
        this.status = ProductStatus.DRAFT;
    }

    public static Product create(
            ProductId id,
            String code,
            String name,
            String description,
            ProductType type
    ) {
        Product product = new Product(
                id,
                code,
                name,
                description,
                type
        );

        product.raise(
                new ProductCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        product.code,
                        product.name,
                        product.type
                )
        );

        return product;
    }

    public void activate() {
        requireStatus(ProductStatus.DRAFT);

        status = ProductStatus.ACTIVE;

        raise(
                new ProductActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void discontinue() {
        requireStatus(ProductStatus.ACTIVE);

        status = ProductStatus.DISCONTINUED;

        raise(
                new ProductDiscontinued(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void archive() {
        requireStatus(ProductStatus.DISCONTINUED);

        status = ProductStatus.ARCHIVED;

        raise(
                new ProductArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void rename(String newName) {
        ensureMutable();

        String normalized = requireName(newName);

        if (normalized.equals(name)) {
            return;
        }

        String oldName = name;
        name = normalized;

        raise(
                new ProductRenamed(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        oldName,
                        normalized
                )
        );
    }

    public void changeDescription(String newDescription) {
        ensureMutable();

        String normalized = normalizeDescription(newDescription);

        if (Objects.equals(normalized, description)) {
            return;
        }

        description = normalized;

        raise(
                new ProductUpdated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void addIdentifier(ProductIdentifier identifier) {
        ensureMutable();

        Objects.requireNonNull(
                identifier,
                "Identifier cannot be null"
        );

        boolean duplicate = identifiers.contains(identifier);

        if (duplicate) {
            throw new BusinessRuleViolation(
                    "Product identifier already exists: "
                            + identifier.type()
                            + "=" + identifier.value()
            );
        }

        identifiers.add(identifier);

        raise(
                new ProductIdentifierAdded(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        identifier.type(),
                        identifier.value()
                )
        );
    }

    public void removeIdentifier(ProductIdentifier identifier) {
        ensureMutable();

        Objects.requireNonNull(
                identifier,
                "Identifier cannot be null"
        );

        if (!identifiers.remove(identifier)) {
            throw new BusinessRuleViolation(
                    "Product identifier not found: "
                            + identifier.type()
                            + "=" + identifier.value()
            );
        }

        raise(
                new ProductIdentifierRemoved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        identifier.type(),
                        identifier.value()
                )
        );
    }

    private void ensureMutable() {
        if (status == ProductStatus.ARCHIVED) {
            throw new InvalidStateException(
                    "Archived product cannot be modified"
            );
        }
    }

    private void requireStatus(ProductStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Product must be in " + expected
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

    /**
     * Product name invariant: 1-{@value #MAX_NAME_LENGTH} characters.
     */
    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name cannot be blank"
            );
        }

        String normalized = value.trim();

        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Product name must not exceed " + MAX_NAME_LENGTH + " characters"
            );
        }

        return normalized;
    }

    /**
     * Product description invariant: optional, but at most
     * {@value #MAX_DESCRIPTION_LENGTH} characters when present.
     */
    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Product description must not exceed "
                            + MAX_DESCRIPTION_LENGTH + " characters"
            );
        }

        return normalized;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ProductType type() {
        return type;
    }

    public ProductStatus status() {
        return status;
    }

    public List<ProductIdentifier> identifiers() {
        return List.copyOf(identifiers);
    }
}