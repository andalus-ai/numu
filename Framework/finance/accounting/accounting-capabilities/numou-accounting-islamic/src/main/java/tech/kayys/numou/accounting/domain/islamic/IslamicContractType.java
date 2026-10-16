package tech.kayys.syirkah.accounting.domain.islamic;

/**
 * AAOIFI-compliant Islamic financing contracts.
 */
public enum IslamicContractType {
    MURABAHA,    // Cost-plus sales contract
    MUDARABA,    // Trustee partnership (capital + labour)
    MUSHARAKA,   // Joint venture equity partnership
    IJARA,       // Leasing
    ISTISNA,     // Manufacturing / construction contract
    SALAM,       // Advance payment for deferred delivery
    WAKALA,      // Agency investment contract
    QARD_HASAN   // Benevolent interest-free loan
}
