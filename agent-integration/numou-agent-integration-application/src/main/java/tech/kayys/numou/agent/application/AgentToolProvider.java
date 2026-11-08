package tech.kayys.numou.agent.application;

import java.util.List;

/**
 * Capability-owned contribution of agent tool descriptors.
 * Implementations must expose only operations backed by public application services.
 */
public interface AgentToolProvider {
    String capabilityId();

    List<AgentToolDescriptor> tools();
}