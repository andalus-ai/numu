package tech.kayys.numou.omnichannel.core;

/**
 * Declared connector capabilities. Orchestration must branch on capabilities,
 * not infer features from provider names.
 */
public record ChannelCapabilities(
        boolean inboundOrders,
        boolean outboundCatalog,
        boolean inventoryPublishing,
        boolean orderStatusPublishing,
        boolean refunds,
        boolean webhookEvents) {
}
