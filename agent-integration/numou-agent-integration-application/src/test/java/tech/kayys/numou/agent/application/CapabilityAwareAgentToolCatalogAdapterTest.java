package tech.kayys.numou.agent.application;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityAwareAgentToolCatalogAdapterTest {
    @Test
    void publishesOnlyToolsFromTenantApplicationSelectedCapabilities() {
        AgentToolProvider finance = provider("finance", "numou.finance.transactions.list");
        AgentToolProvider workforce = provider("workforce", "numou.workforce.profile.get");
        ApplicationCapabilitySelectionPort selection = (applicationId, tenantId) -> {
            assertThat(applicationId).isEqualTo("accounting-only");
            assertThat(tenantId).isEqualTo("tenant-4");
            return Set.of("finance");
        };
        AgentToolCatalogPort catalog = new CapabilityAwareAgentToolCatalogAdapter(selection, List.of(finance, workforce));
        assertThat(catalog.findEnabledTools("accounting-only", "tenant-4"))
                .extracting(AgentToolDescriptor::toolId)
                .containsExactly("numou.finance.transactions.list");
    }

    @Test
    void rejectsMissingApplicationOrTenantId() {
        AgentToolCatalogPort catalog = new CapabilityAwareAgentToolCatalogAdapter(
                (applicationId, tenantId) -> Set.of(), List.of());
        assertThatThrownBy(() -> catalog.findEnabledTools(" ", "tenant-4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.findEnabledTools("accounting-only", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AgentToolProvider provider(String capabilityId, String toolId) {
        return new AgentToolProvider() {
            @Override public String capabilityId() { return capabilityId; }
            @Override public List<AgentToolDescriptor> tools() {
                return List.of(new AgentToolDescriptor(toolId, "1.0.0", capabilityId,
                        "Read capability information", capabilityId + ".read",
                        AgentToolOperation.READ, false, Map.of("type", "object")));
            }
        };
    }
}
