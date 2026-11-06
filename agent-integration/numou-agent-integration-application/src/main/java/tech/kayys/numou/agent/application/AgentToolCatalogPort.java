package tech.kayys.numou.agent.application;

import java.util.List;

/** Supplies tools enabled for one tenant's selected application composition. */
public interface AgentToolCatalogPort {
    List<AgentToolDescriptor> findEnabledTools(String applicationId, String tenantId);
}
