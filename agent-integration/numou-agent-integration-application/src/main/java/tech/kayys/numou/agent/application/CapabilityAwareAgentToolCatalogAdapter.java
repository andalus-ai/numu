package tech.kayys.numou.agent.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Projects host-registered capability tool providers through the selected,
 * tenant-scoped application composition. This adapter does not own the
 * application composition or provider lifecycle.
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
    public List<AgentToolDescriptor> findEnabledTools(String applicationId, String tenantId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Set<String> enabled = Set.copyOf(Objects.requireNonNull(
                selection.findEnabledCapabilityIds(applicationId, tenantId), "enabled capabilities"));
        List<AgentToolDescriptor> result = new ArrayList<>();
        providers.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentToolProvider::capabilityId))
                .filter(provider -> enabled.contains(required(provider.capabilityId(), "provider capabilityId")))
                .forEach(provider -> {
                    List<AgentToolDescriptor> descriptors = Objects.requireNonNull(
                            provider.tools(), "provider tools");
                    descriptors.stream().filter(Objects::nonNull).forEach(descriptor -> {
                        if (!provider.capabilityId().equals(descriptor.capabilityId())) {
                            throw new IllegalStateException("Tool capability does not match its provider: "
                                    + descriptor.toolId());
                        }
                        result.add(descriptor);
                    });
                });
        return result.stream().sorted(Comparator.comparing(AgentToolDescriptor::toolId)).toList();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
        return value;
    }
}
