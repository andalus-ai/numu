package tech.kayys.numou.asset.domain.warranty;

/** How date + meter limits combine (ASSET-23; mirrors ASSET-22 OR/AND). */
public enum WarrantyExpiryRule {
    DATE,
    METER,
    DATE_OR_METER,
    DATE_AND_METER
}
