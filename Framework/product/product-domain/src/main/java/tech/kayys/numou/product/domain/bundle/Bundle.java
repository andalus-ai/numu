package tech.kayys.syirkah.product.domain.bundle;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.BundleActivated;
import tech.kayys.syirkah.product.domain.event.BundleArchived;
import tech.kayys.syirkah.product.domain.event.BundleComponentAdded;
import tech.kayys.syirkah.product.domain.event.BundleComponentRemoved;
import tech.kayys.syirkah.product.domain.event.BundleCreated;
import tech.kayys.syirkah.product.domain.event.BundleDiscontinued;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commercial composition aggregate: sell several products together
 * as one offering (product01 / product02).
 *
 * Bundle is composition only — not promotion pricing, not BOM, not
 * warehouse kit. Nested bundles are out of scope initially.
 */
public final class Bundle extends AbstractAggregateRoot<BundleId> {

    private final String code;

    private final String name;

    private BundleStatus status;

    private final List<BundleComponent> components = new ArrayList<>();

    private Bundle(
            BundleId id,
            String code,
            String name
    ) {
        super(id);

        this.code = requireText(code, "Bundle code");
        this.name = requireText(name, "Bundle name");
        this.status = BundleStatus.DRAFT;
    }

    public static Bundle create(
            BundleId id,
            String code,
            String name
    ) {
        Objects.requireNonNull(id, "Bundle id cannot be null");

        Bundle bundle = new Bundle(id, code, name);

        bundle.raise(
                new BundleCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        bundle.code,
                        bundle.name
                )
        );

        return bundle;
    }

    public void activate() {
        requireStatus(BundleStatus.DRAFT);

        if (components.isEmpty()) {
            throw new BusinessRuleViolation(
                    "Bundle must contain at least one component"
            );
        }

        status = BundleStatus.ACTIVE;

        raise(
                new BundleActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void discontinue() {
        requireStatus(BundleStatus.ACTIVE);

        status = BundleStatus.DISCONTINUED;

        raise(
                new BundleDiscontinued(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void archive() {
        requireStatus(BundleStatus.DISCONTINUED);

        status = BundleStatus.ARCHIVED;

        raise(
                new BundleArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void addComponent(ProductId productId, BigDecimal quantity) {
        ensureMutable();

        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");

        if (components.stream()
                .anyMatch(c -> c.productId().equals(productId))) {
            throw new BusinessRuleViolation(
                    "Product is already a bundle component: "
                            + productId
            );
        }

        var component = new BundleComponent(productId, quantity);
        components.add(component);

        raise(
                new BundleComponentAdded(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId,
                        quantity
                )
        );
    }

    public void addComponent(BundleComponent component) {
        Objects.requireNonNull(component, "component cannot be null");
        addComponent(component.productId(), component.quantity());
    }

    public void removeComponent(ProductId productId) {
        ensureMutable();

        Objects.requireNonNull(productId, "productId cannot be null");

        boolean removed = components.removeIf(
                component -> component.productId().equals(productId)
        );

        if (!removed) {
            throw new BusinessRuleViolation(
                    "Bundle component not found: " + productId
            );
        }

        raise(
                new BundleComponentRemoved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        productId
                )
        );
    }

    private void ensureMutable() {
        if (status != BundleStatus.DRAFT) {
            throw new InvalidStateException(
                    "Bundle can only be modified while in DRAFT"
            );
        }
    }

    private void requireStatus(BundleStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Bundle must be in " + expected
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

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public BundleStatus status() {
        return status;
    }

    public List<BundleComponent> components() {
        return List.copyOf(components);
    }
}
