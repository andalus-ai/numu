package tech.kayys.numou.billing.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Universal line item for any billing operation across SaaS, Retail, FnB, and Grocery.
 */
public final class BillingItem implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String itemId;
    private final String sku;
    private final String name;
    private final ItemType itemType;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private final Money totalAmount;
    private final Money taxAmount;
    private final Money discountAmount;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final Map<String, String> metadata;

    public BillingItem(
            String itemId,
            String sku,
            String name,
            ItemType itemType,
            BigDecimal quantity,
            Money unitPrice,
            Money taxAmount,
            Money discountAmount,
            Instant periodStart,
            Instant periodEnd,
            Map<String, String> metadata) {
        this.itemId = itemId;
        this.sku = sku;
        this.name = name;
        this.itemType = itemType != null ? itemType : ItemType.ONE_TIME;
        this.quantity = quantity != null ? quantity : BigDecimal.ONE;
        this.unitPrice = unitPrice;
        
        Money base = unitPrice != null ? unitPrice.multiply(this.quantity) : Money.zero(unitPrice != null ? unitPrice.getCurrency().getCurrencyCode() : "USD");
        Money tax = taxAmount != null ? taxAmount : Money.zero(base.getCurrency().getCurrencyCode());
        Money disc = discountAmount != null ? discountAmount : Money.zero(base.getCurrency().getCurrencyCode());
        
        this.taxAmount = tax;
        this.discountAmount = disc;
        this.totalAmount = base.add(tax).subtract(disc);
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        validate();
    }

    public static BillingItem of(String sku, String name, BigDecimal quantity, Money unitPrice) {
        return new BillingItem(sku, sku, name, ItemType.ONE_TIME, quantity, unitPrice, null, null, null, null, Map.of());
    }

    public static BillingItem ofSubscription(String sku, String name, Money periodPrice, Instant start, Instant end) {
        return new BillingItem(sku, sku, name, ItemType.SUBSCRIPTION, BigDecimal.ONE, periodPrice, null, null, start, end, Map.of());
    }

    public static BillingItem ofUsage(String sku, String name, BigDecimal units, Money ratePerUnit) {
        return new BillingItem(sku, sku, name, ItemType.USAGE, units, ratePerUnit, null, null, null, null, Map.of());
    }

    @Override
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("BillingItem name cannot be empty");
        }
    }

    public String getItemId() { return itemId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public ItemType getItemType() { return itemType; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getTaxAmount() { return taxAmount; }
    public Money getDiscountAmount() { return discountAmount; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }
}
