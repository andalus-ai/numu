package tech.kayys.numou.billing.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Universal Billing Context.
 * Decouples the billing engine from specific domain models (customer vs tenant vs table).
 */
public final class BillingContext implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String partyId;
    private final PartyType partyType;
    private final DomainTag domainTag;
    private final String tenantId;
    private final String currencyCode;
    private final String locale;
    private final Map<String, String> attributes;

    public BillingContext(
            String partyId,
            PartyType partyType,
            DomainTag domainTag,
            String tenantId,
            String currencyCode,
            String locale,
            Map<String, String> attributes) {
        if (partyId == null || partyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Party ID cannot be empty");
        }
        this.partyId = partyId;
        this.partyType = partyType != null ? partyType : PartyType.CUSTOMER;
        this.domainTag = domainTag != null ? domainTag : DomainTag.GENERIC;
        this.tenantId = tenantId != null ? tenantId : "default";
        this.currencyCode = currencyCode != null ? currencyCode : "USD";
        this.locale = locale != null ? locale : "en_US";
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        validate();
    }

    public static BillingContext ofCustomer(String customerId, String currencyCode) {
        return new BillingContext(customerId, PartyType.CUSTOMER, DomainTag.GENERIC, "default", currencyCode, "en_US", Map.of());
    }

    public static BillingContext ofTenant(String tenantId, String currencyCode) {
        return new BillingContext(tenantId, PartyType.TENANT, DomainTag.SAAS, tenantId, currencyCode, "en_US", Map.of());
    }

    public static BillingContext ofRetail(String customerOrCashierId, String tenantId, String currencyCode) {
        return new BillingContext(customerOrCashierId, PartyType.CUSTOMER, DomainTag.RETAIL, tenantId, currencyCode, "en_US", Map.of());
    }

    public static BillingContext ofFnB(String tableOrGuestId, String tenantId, String currencyCode) {
        return new BillingContext(tableOrGuestId, PartyType.GUEST, DomainTag.FNB, tenantId, currencyCode, "en_US", Map.of());
    }

    public static BillingContext ofGrocery(String customerId, String tenantId, String currencyCode) {
        return new BillingContext(customerId, PartyType.CUSTOMER, DomainTag.GROCERY, tenantId, currencyCode, "en_US", Map.of());
    }

    @Override
    public void validate() {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("BillingContext partyId is mandatory");
        }
    }

    public String getPartyId() { return partyId; }
    public PartyType getPartyType() { return partyType; }
    public DomainTag getDomainTag() { return domainTag; }
    public String getTenantId() { return tenantId; }
    public String getCurrencyCode() { return currencyCode; }
    public String getLocale() { return locale; }
    public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public BillingContext withAttribute(String key, String value) {
        Map<String, String> newAttrs = new HashMap<>(this.attributes);
        newAttrs.put(key, value);
        return new BillingContext(partyId, partyType, domainTag, tenantId, currencyCode, locale, newAttrs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BillingContext that)) return false;
        return Objects.equals(partyId, that.partyId) &&
               partyType == that.partyType &&
               domainTag == that.domainTag &&
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partyId, partyType, domainTag, tenantId);
    }

    @Override
    public String toString() {
        return "BillingContext{" +
                "partyId='" + partyId + '\'' +
                ", partyType=" + partyType +
                ", domainTag=" + domainTag +
                ", tenantId='" + tenantId + '\'' +
                ", currencyCode='" + currencyCode + '\'' +
                '}';
    }
}
