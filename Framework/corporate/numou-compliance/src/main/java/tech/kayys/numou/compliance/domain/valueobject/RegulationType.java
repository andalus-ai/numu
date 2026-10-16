package tech.kayys.syirkah.compliance.domain.valueobject;

/**
 * Types of regulations.
 */
public enum RegulationType {
    GDPR("GDPR - General Data Protection Regulation"),
    CCPA("CCPA - California Consumer Privacy Act"),
    HIPAA("HIPAA - Health Insurance Portability and Accountability Act"),
    SOX("SOX - Sarbanes-Oxley Act"),
    PCI_DSS("PCI-DSS - Payment Card Industry Data Security Standard"),
    ISO_27001("ISO 27001 - Information Security Management"),
    SOC2("SOC 2 - Service Organization Control 2"),
    FISMA("FISMA - Federal Information Security Management Act"),
    ITAR("ITAR - International Traffic in Arms Regulations"),
    CFR("CFR - Code of Federal Regulations"),
    GDPR_DPA("GDPR-DPA - Data Processing Agreement"),
    EU_US_PRIVACY("EU-US Privacy Shield");

    private final String description;

    RegulationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPrivacyRelated() {
        return this == GDPR || this == CCPA || this == HIPAA;
    }

    public boolean isSecurityRelated() {
        return this == PCI_DSS || this == ISO_27001 || this == SOC2;
    }
}
