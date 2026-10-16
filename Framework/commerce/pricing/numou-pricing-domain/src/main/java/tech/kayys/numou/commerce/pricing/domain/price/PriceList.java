package tech.kayys.syirkah.commerce.pricing.domain.price;

import tech.kayys.syirkah.commerce.pricing.domain.event.PriceEntryAdded;
import tech.kayys.syirkah.commerce.pricing.domain.event.PriceEntryChanged;
import tech.kayys.syirkah.commerce.pricing.domain.event.PriceListActivated;
import tech.kayys.syirkah.commerce.pricing.domain.event.PriceListArchived;
import tech.kayys.syirkah.commerce.pricing.domain.event.PriceListCreated;
import tech.kayys.syirkah.commerce.pricing.domain.event.PriceListSuspended;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Price list aggregate (product02.md): which set of prices we are using.
 * Codes are tenant-defined; no hard-coded RETAIL/WHOLESALE enum.
 * Owns its {@link PriceEntry} rows; cross-aggregate links are IDs only.
 */
public final class PriceList extends AbstractAggregateRoot<PriceListId> {

    private final String code;
    private String name;
    private PriceListStatus status;
    private final Map<ProductOfferingId, PriceEntry> entries = new LinkedHashMap<>();

    private PriceList(PriceListId id, String code, String name) {
        super(id);
        this.code = requireCode(code);
        this.name = requireName(name);
        this.status = PriceListStatus.DRAFT;
    }

    public static PriceList create(PriceListId id, String code, String name) {
        Objects.requireNonNull(id, "id cannot be null");
        var list = new PriceList(id, code, name);
        list.raise(new PriceListCreated(
                UUID.randomUUID(), Instant.now(), id, list.code, list.name));
        return list;
    }

    public void rename(String name) {
        ensureEditable();
        this.name = requireName(name);
    }

    public void activate() {
        if (status != PriceListStatus.DRAFT && status != PriceListStatus.SUSPENDED) {
            throw new BusinessRuleViolation("Only draft or suspended price lists can be activated");
        }
        status = PriceListStatus.ACTIVE;
        raise(new PriceListActivated(UUID.randomUUID(), Instant.now(), id()));
    }

    public void suspend() {
        if (status != PriceListStatus.ACTIVE) {
            throw new BusinessRuleViolation("Only active price lists can be suspended");
        }
        status = PriceListStatus.SUSPENDED;
        raise(new PriceListSuspended(UUID.randomUUID(), Instant.now(), id()));
    }

    public void archive() {
        if (status == PriceListStatus.ARCHIVED) {
            return;
        }
        if (status != PriceListStatus.SUSPENDED) {
            throw new BusinessRuleViolation("Only suspended price lists can be archived");
        }
        status = PriceListStatus.ARCHIVED;
        raise(new PriceListArchived(UUID.randomUUID(), Instant.now(), id()));
    }

    public PriceEntry addEntry(ProductOfferingId offeringId, Money amount) {
        ensureEditable();
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        if (entries.containsKey(offeringId)) {
            throw new BusinessRuleViolation(
                    "Price entry already exists for offering: " + offeringId.value());
        }
        var entry = new PriceEntry(PriceEntryId.generate(), offeringId, amount);
        entries.put(offeringId, entry);
        raise(new PriceEntryAdded(
                UUID.randomUUID(), Instant.now(), id(), entry.id(), offeringId, amount));
        return entry;
    }

    public PriceEntry changeEntry(ProductOfferingId offeringId, Money amount) {
        ensureEditable();
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        var existing = entries.get(offeringId);
        if (existing == null) {
            throw new BusinessRuleViolation(
                    "No price entry for offering: " + offeringId.value());
        }
        var updated = new PriceEntry(existing.id(), offeringId, amount);
        entries.put(offeringId, updated);
        raise(new PriceEntryChanged(
                UUID.randomUUID(), Instant.now(), id(), updated.id(), offeringId, amount));
        return updated;
    }

    public Optional<PriceEntry> entryFor(ProductOfferingId offeringId) {
        return Optional.ofNullable(entries.get(offeringId));
    }

    public List<PriceEntry> entries() {
        return List.copyOf(entries.values());
    }

    private void ensureEditable() {
        if (status == PriceListStatus.ARCHIVED) {
            throw new BusinessRuleViolation("Archived price lists cannot be modified");
        }
    }

    private static String requireCode(String code) {
        Objects.requireNonNull(code, "code cannot be null");
        String trimmed = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String trimmed = name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return trimmed;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public PriceListStatus status() {
        return status;
    }
}
