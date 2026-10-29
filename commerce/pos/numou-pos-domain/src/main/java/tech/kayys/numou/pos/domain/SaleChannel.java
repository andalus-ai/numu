package tech.kayys.numou.pos.domain;

/** Origin channel for a sale; channel-specific fulfillment remains in its owning capability. */
public enum SaleChannel {
    POS,
    WEB,
    MOBILE,
    MARKETPLACE,
    SOCIAL_COMMERCE,
    B2B_PORTAL,
    CALL_CENTER,
    KIOSK,
    PARTNER_API
}
