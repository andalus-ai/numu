package tech.kayys.syirkah.asset.domain.document;

/**
 * Asset-facing meaning of a referenced document (ASSET-24 §5-§6).
 *
 * <p>Two classifications are kept: the Documents capability owns the generic
 * document classification (PDF, image, …); this enum owns the meaning of the
 * document to Asset (registration, insurance, …).</p>
 */
public enum AssetDocumentType {
    PURCHASE_DOCUMENT,
    OWNERSHIP_DOCUMENT,
    REGISTRATION,
    WARRANTY_CERTIFICATE,
    SERVICE_CONTRACT,
    INSURANCE,
    INSPECTION_REPORT,
    MAINTENANCE_REPORT,
    CALIBRATION_CERTIFICATE,
    MANUAL,
    CERTIFICATE,
    PHOTO,
    CONTRACT,
    INVOICE,
    REPORT,
    OTHER
}
