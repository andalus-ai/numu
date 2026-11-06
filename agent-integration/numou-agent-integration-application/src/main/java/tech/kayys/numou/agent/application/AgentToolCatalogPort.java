package tech.kayys.numou.agent.application;

import java.util.List;

/** Supplies tools enabled by the selected application composition. */
public interface AgentToolCatalogPort {
    List<AgentToolDescriptor> findEnabledTools(String applicationId);
}