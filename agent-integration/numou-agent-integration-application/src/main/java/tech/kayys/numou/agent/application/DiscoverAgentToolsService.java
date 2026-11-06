package tech.kayys.numou.agent.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Returns only tools enabled for the selected application and discoverable by
 * the verified caller. Invocation-time authorization remains mandatory.
 */
public final class DiscoverAgentToolsService {
    private final AgentToolCatalogPort catalog;
    private final AgentToolAuthorizationPort authorization;

    public DiscoverAgentToolsService(
            AgentToolCatalogPort catalog,
            AgentToolAuthorizationPort authorization) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<AgentToolDescriptor> discover(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return catalog.findEnabledTools(context.applicationId()).stream()
                .filter(Objects::nonNull)
                .filter(tool -> authorization.mayDiscover(context, tool))
                .sorted(Comparator.comparing(AgentToolDescriptor::toolId))
                .toList();
    }
}