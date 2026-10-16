package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import java.util.Objects;

/**
 * Tenant or system-level accounting compliance and feature toggle configuration.
 */
public record ComplianceConfiguration(
        ComplianceStandard standard,
        boolean allowRibaAccounts,
        boolean enforceShariaContracts,
        boolean enableZakatAccounting,
        boolean enableIndonesianTaxWithholding,
        boolean strictPeriodLocking,
        boolean dualLedgerEnabled
) implements ValueObject {

    public ComplianceConfiguration {
        Objects.requireNonNull(standard, "standard cannot be null");
    }

    /** Default IFRS configuration */
    public static ComplianceConfiguration ifrsDefault() {
        return new ComplianceConfiguration(
                ComplianceStandard.IFRS,
                true,
                false,
                false,
                false,
                true,
                false
        );
    }

    /** Indonesian SAK configuration */
    public static ComplianceConfiguration sakIndonesiaDefault() {
        return new ComplianceConfiguration(
                ComplianceStandard.SAK_INDONESIA,
                true,
                false,
                false,
                true,
                true,
                false
        );
    }

    /** Pure Sharia AAOIFI / SAK Syariah configuration */
    public static ComplianceConfiguration shariaDefault() {
        return new ComplianceConfiguration(
                ComplianceStandard.SHARIA_AAOIFI_SAK,
                false, // Strict zero riba
                true,  // Must tag contracts
                true,  // Zakat accounting enabled
                true,  // Indonesian tax enabled
                true,
                false
        );
    }

    /** Hybrid dual-ledger configuration */
    public static ComplianceConfiguration hybridDefault() {
        return new ComplianceConfiguration(
                ComplianceStandard.HYBRID,
                true,  // Conventional allowed in conventional book
                true,  // Sharia tagged in sharia book
                true,  // Zakat enabled
                true,
                true,
                true   // Dual ledger enabled
        );
    }
}
