package tech.kayys.numou.agent.application;

import java.util.List;
import java.util.Objects;

/**
 * Lists capability identifiers represented by tools visible to this caller.
 * It does not disclose capabilities that have no enabled, authorized tools.
 */
public final class ListDiscoverableAgentCapabilitiesService {
    private final DiscoverAgentToolsService discovery;

    public ListDiscoverableAgentCapabilitiesService(DiscoverAgentToolsService discovery) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
    }

    public List<String> list(AgentExecutionContext context) {
        return discovery.discover(context).stream()
                .map(AgentToolDescriptor::capabilityId)
                .distinct()
                .sorted()
                .toList();
    }
}
