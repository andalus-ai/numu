package tech.kayys.numou.omnichannel.core;

/**
 * Provider-neutral port implemented by a connector module. Implementations
 * must authenticate/verify provider input before dispatch and must not persist
 * authoritative commerce or inventory state themselves.
 */
public interface ChannelAdapter {
    String providerId();

    ChannelCapabilities capabilities();
}
