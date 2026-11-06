package tech.kayys.numou.agent.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Projects capability-owned tool contributions into the selected application's catalog.
 * The selection port must be backed by the validated application composition, not user input.
 */
public final class CapabilityAwareAgentToolCatalogAdapter implements AgentToolCatalogPort {
    private final ApplicationCapabilitySelectionPort selection;
    private final List<AgentToolProvider> providers;

    public CapabilityAwareAgentToolCatalogAdapter(
            ApplicationCapabilitySelectionPort selection,
            List<AgentToolProvider> providers) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public List<AgentToolDescriptor> findEnabledTools(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId is required");
        }
        Set<String> enabled = Set.copyOf(
                Objects.requireNonNull(selection.enabledCapabilityIds(applicationId),
                        "enabled capability IDs"));
        return providers.stream()
                .filter(Objects::nonNull)
                .filter(provider -> enabled.contains(provider.capabilityId()))
                .flatMap(provider -> Objects.requireNonNull(provider.tools(), "provider tools").stream())
                .filter(Objects::nonNull)
                .toList();
    }
}