
package tech.kayys.syirkah.accounting.domain.consolidation;

import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.util.Objects;

public record GroupMember(
        TenantRef entityTenantId,
        String entityName,
        BigDecimal ownershipPercentage, // e.g. 80.00%
        boolean isParent
) {
    public GroupMember {
        Objects.requireNonNull(entityTenantId, "entityTenantId cannot be null");
        Objects.requireNonNull(entityName, "entityName cannot be null");
        Objects.requireNonNull(ownershipPercentage, "ownershipPercentage cannot be null");
        if (ownershipPercentage.compareTo(BigDecimal.ZERO) <= 0 || ownershipPercentage.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Ownership percentage must be between 0 and 100%");
        }
    }

    public BigDecimal nonControllingInterestPercentage() {
        return new BigDecimal("100.00").subtract(ownershipPercentage);
    }
}
