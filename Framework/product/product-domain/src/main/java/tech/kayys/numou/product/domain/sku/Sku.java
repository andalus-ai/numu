package tech.kayys.syirkah.product.domain.sku;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.SkuActivated;
import tech.kayys.syirkah.product.domain.event.SkuArchived;
import tech.kayys.syirkah.product.domain.event.SkuCreated;
import tech.kayys.syirkah.product.domain.event.SkuDiscontinued;
import tech.kayys.syirkah.product.domain.event.SkuIdentifierAdded;
import tech.kayys.syirkah.product.domain.event.SkuIdentifierRemoved;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The stock keeping unit: where Product meets inventory,
 * procurement, sales and fulfillment (P1.0).
 *
 * The SKU references its product (and optionally a variant) by ID
 * only, has its own lifecycle, and deliberately owns NO quantity,
 * price, cost, warehouse or tax - downstream capabilities hang
 * those off {@link SkuId}.
 */
public final class Sku extends AbstractAggregateRoot<SkuId> {

    private final ProductId productId;

    private final ProductVariantId variantId;

    private final String code;

    private String name;

    private SkuStatus status;

    private final List<SkuIdentifier> identifiers = new ArrayList<>();

    private Sku(
            SkuId id,
            ProductId productId,
            ProductVariantId variantId,
            String code,
            String name
    ) {
        super(id);

        this.productId = Objects.requireNonNull(
                productId,
                "Product id cannot be null"
        );

        this.variantId = variantId;

        this.code = requireText(code, "SKU code");
        this.name = requireText(name, "SKU name");
        this.status = SkuStatus.DRAFT;
    }

    public static Sku create(
            SkuId id,
            ProductId productId,
            ProductVariantId variantId,
            String code,
            String name
    ) {
        Sku sku = new Sku(id, productId, variantId, code, name);

        sku.raise(
                new SkuCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        productId,
                        variantId,
                        code,
                        name
                )
        );

        return sku;
    }

    public void activate() {
        requireStatus(SkuStatus.DRAFT);

        status = SkuStatus.ACTIVE;

        raise(
                new SkuActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void discontinue() {
        requireStatus(SkuStatus.ACTIVE);

        status = SkuStatus.DISCONTINUED;

        raise(
                new SkuDiscontinued(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void archive() {
        requireStatus(SkuStatus.DISCONTINUED);

        status = SkuStatus.ARCHIVED;

        raise(
                new SkuArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void rename(String newName) {
        ensureMutable();

        this.name = requireText(newName, "SKU name");
    }

    public void addIdentifier(SkuIdentifier identifier) {
        ensureMutable();

        Objects.requireNonNull(
                identifier,
                "SKU identifier cannot be null"
        );

        boolean duplicate = identifiers.contains(identifier);

        if (duplicate) {
            throw new BusinessRuleViolation(
                    "SKU identifier already exists: "
                            + identifier.type()
                            + "=" + identifier.value()
            );
        }

        identifiers.add(identifier);

        raise(
                new SkuIdentifierAdded(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        identifier.type(),
                        identifier.value()
                )
        );
    }

    public void removeIdentifier(SkuIdentifier identifier) {
        ensureMutable();

        Objects.requireNonNull(
                identifier,
                "SKU identifier cannot be null"
        );

        if (!identifiers.remove(identifier)) {
            throw new BusinessRuleViolation(
                    "SKU identifier not found: "
                            + identifier.type()
                            + "=" + identifier.value()
            );
        }

        raise(
                new SkuIdentifierRemoved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        identifier.type(),
                        identifier.value()
                )
        );
    }

    private void ensureMutable() {
        if (status == SkuStatus.ARCHIVED) {
            throw new InvalidStateException(
                    "Archived SKU cannot be modified"
            );
        }
    }

    private void requireStatus(SkuStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "SKU must be in " + expected
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

    public ProductId productId() {
        return productId;
    }

    public ProductVariantId variantId() {
        return variantId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public SkuStatus status() {
        return status;
    }

    public List<SkuIdentifier> identifiers() {
        return List.copyOf(identifiers);
    }
}