package tech.kayys.syirkah.accounting.domain.ledger;

import tech.kayys.syirkah.accounting.domain.multitenancy.TenantAware;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;

import java.util.Objects;

/**
 * Ledger aggregate root representing a distinct set of books within a tenant.
 */
public final class Ledger extends AbstractAggregateRoot<LedgerId> implements TenantAware {

    private final LedgerId id;
    private final TenantRef tenantId;
    private String name;
    private LedgerType type;
    private Currency baseCurrency;
    private ComplianceStandard complianceStandard;
    private boolean active;

    public Ledger(
            LedgerId id,
            TenantRef tenantId,
            String name,
            LedgerType type,
            Currency baseCurrency,
            ComplianceStandard complianceStandard
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency cannot be null");
        this.complianceStandard = Objects.requireNonNull(complianceStandard, "complianceStandard cannot be null");
        this.active = true;
    }

    @Override
    public LedgerId id() { return id; }

    @Override
    public TenantRef tenantId() { return tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    public LedgerType getType() { return type; }
    public Currency getBaseCurrency() { return baseCurrency; }
    public ComplianceStandard getComplianceStandard() { return complianceStandard; }
    public boolean isActive() { return active; }

    public void deactivate() { this.active = false; }
}
