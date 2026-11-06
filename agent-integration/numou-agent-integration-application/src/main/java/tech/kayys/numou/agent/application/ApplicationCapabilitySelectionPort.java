package tech.kayys.numou.agent.application;

import java.util.Set;

/** Returns the capability IDs selected by the validated application composition. */
public interface ApplicationCapabilitySelectionPort {
    Set<String> enabledCapabilityIds(String applicationId);
}