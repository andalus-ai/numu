package tech.kayys.numou.agent.application;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes a tool only when it is both enabled for the application and
 * discoverable by the verified caller. This intentionally reuses discovery
 * policy rather than creating a weaker metadata lookup path.
 */
public final class DescribeAgentToolService {
    private final DiscoverAgentToolsService discovery;

    public DescribeAgentToolService(DiscoverAgentToolsService discovery) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
    }

    public Optional<AgentToolDescriptor> describe(AgentExecutionContext context, String toolId) {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId is required");
        }
        return discovery.discover(context).stream()
                .filter(tool -> tool.toolId().equals(toolId))
                .findFirst();
    }
}
