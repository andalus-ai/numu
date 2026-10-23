package tech.kayys.numou.omnichannel.core;

/**
 * A channel class, not a provider name. Provider-specific identifiers belong
 * to the configured channel account and connector adapter.
 */
public enum ChannelType {
    POS,
    WEB,
    MOBILE_APP,
    MARKETPLACE,
    MESSAGING,
    SOCIAL_COMMERCE,
    B2B_PORTAL,
    PARTNER_API,
    CALL_CENTER,
    OTHER
}
