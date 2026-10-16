package tech.kayys.syirkah.commerce.configuration.domain;

import tech.kayys.syirkah.commerce.configuration.domain.event.OptionDeselected;
import tech.kayys.syirkah.commerce.configuration.domain.event.OptionSelected;
import tech.kayys.syirkah.commerce.configuration.domain.event.ProductConfigurationCancelled;
import tech.kayys.syirkah.commerce.configuration.domain.event.ProductConfigurationCompleted;
import tech.kayys.syirkah.commerce.configuration.domain.event.ProductConfigurationCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.OptionGroupId;
import tech.kayys.syirkah.product.domain.specification.OptionId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Concrete option selection for a product (product02.md).
 *
 * Owns selected state only. Specification defines allowed options;
 * validation is performed externally and is not persisted as status.
 */
public final class ProductConfiguration
        extends AbstractAggregateRoot<ProductConfigurationId> {

    private final ProductId productId;
    private final ProductSpecificationId specificationId;
    private ConfigurationStatus status;
    private final List<SelectedOption> selections = new ArrayList<>();

    private ProductConfiguration(
            ProductConfigurationId id,
            ProductId productId,
            ProductSpecificationId specificationId
    ) {
        super(id);
        this.productId = Objects.requireNonNull(productId, "productId cannot be null");
        this.specificationId = specificationId;
        this.status = ConfigurationStatus.DRAFT;
    }

    public static ProductConfiguration create(
            ProductConfigurationId id,
            ProductId productId,
            ProductSpecificationId specificationId
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        var configuration = new ProductConfiguration(id, productId, specificationId);
        configuration.raise(new ProductConfigurationCreated(
                UUID.randomUUID(),
                Instant.now(),
                id,
                productId,
                specificationId));
        return configuration;
    }

    /** Transient factory used by POS/pricing tests (no events). */
    public static ProductConfiguration of(
            ProductId productId,
            List<SelectedOption> selections
    ) {
        return of(ProductConfigurationId.generate(), productId, null, selections);
    }

    public static ProductConfiguration of(
            ProductConfigurationId id,
            ProductId productId,
            ProductSpecificationId specificationId,
            List<SelectedOption> selections
    ) {
        Objects.requireNonNull(selections, "selections cannot be null");
        var configuration = new ProductConfiguration(id, productId, specificationId);
        configuration.selections.addAll(selections);
        return configuration;
    }

    public static ProductConfiguration empty(ProductId productId) {
        return new ProductConfiguration(
                ProductConfigurationId.generate(), productId, null);
    }

    public static ProductConfiguration empty(
            ProductId productId,
            ProductSpecificationId specificationId
    ) {
        return new ProductConfiguration(
                ProductConfigurationId.generate(), productId, specificationId);
    }

    public void selectOption(SelectedOption selection) {
        Objects.requireNonNull(selection, "selection cannot be null");
        ensureMutable();
        if (selections.contains(selection)) {
            throw new BusinessRuleViolation("Option is already selected");
        }
        selections.add(selection);
        raise(new OptionSelected(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                selection.optionGroupId(),
                selection.optionId()));
    }

    public void deselectOption(OptionGroupId optionGroupId, OptionId optionId) {
        ensureMutable();
        var selection = new SelectedOption(optionGroupId, optionId);
        if (!selections.remove(selection)) {
            throw new BusinessRuleViolation("Option is not selected");
        }
        raise(new OptionDeselected(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                optionGroupId,
                optionId));
    }

    public void clearOptionGroup(OptionGroupId optionGroupId) {
        Objects.requireNonNull(optionGroupId, "optionGroupId cannot be null");
        ensureMutable();
        var removed = selections.stream()
                .filter(s -> s.optionGroupId().equals(optionGroupId))
                .toList();
        selections.removeIf(s -> s.optionGroupId().equals(optionGroupId));
        for (var selection : removed) {
            raise(new OptionDeselected(
                    UUID.randomUUID(),
                    Instant.now(),
                    id(),
                    selection.optionGroupId(),
                    selection.optionId()));
        }
    }

    /** Application validates first, then completes DRAFT → COMPLETED. */
    public void complete() {
        if (status != ConfigurationStatus.DRAFT) {
            throw new BusinessRuleViolation(
                    "Only draft configurations can be completed");
        }
        status = ConfigurationStatus.COMPLETED;
        raise(new ProductConfigurationCompleted(
                UUID.randomUUID(), Instant.now(), id()));
    }

    public void cancel() {
        if (status == ConfigurationStatus.COMPLETED) {
            throw new BusinessRuleViolation(
                    "Completed configurations cannot be cancelled");
        }
        if (status == ConfigurationStatus.CANCELLED) {
            return;
        }
        status = ConfigurationStatus.CANCELLED;
        raise(new ProductConfigurationCancelled(
                UUID.randomUUID(), Instant.now(), id()));
    }

    private void ensureMutable() {
        if (status == ConfigurationStatus.COMPLETED) {
            throw new BusinessRuleViolation(
                    "Completed configuration cannot be modified");
        }
        if (status == ConfigurationStatus.CANCELLED) {
            throw new BusinessRuleViolation(
                    "Cancelled configuration cannot be modified");
        }
    }

    public ProductId productId() {
        return productId;
    }

    public ProductSpecificationId specificationId() {
        return specificationId;
    }

    public ConfigurationStatus status() {
        return status;
    }

    public List<SelectedOption> selections() {
        return List.copyOf(selections);
    }

    /**
     * Compatibility view for pricing: last selection per group code.
     * Prefer {@link #selections()} for multi-select.
     */
    public java.util.Map<String, SelectedOption> options() {
        return selections.stream().collect(Collectors.toMap(
                SelectedOption::groupCode,
                s -> s,
                (first, second) -> second,
                java.util.LinkedHashMap::new));
    }
}
