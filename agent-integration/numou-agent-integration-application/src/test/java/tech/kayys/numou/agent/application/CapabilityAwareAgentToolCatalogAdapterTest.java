package tech.kayys.numou.agent.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityAwareAgentToolCatalogAdapterTest {

    @Test
    void publishesOnlyToolsFromCapabilitiesSelectedByValidatedApplicationComposition() {
        AgentToolProvider finance = provider("finance", "numou.finance.transactions.list");
        AgentToolProvider workforce = provider("workforce", "numou.workforce.profile.get");

        ApplicationCapabilitySelectionPort selection = applicationId -> {
            assertThat(applicationId).isEqualTo("accounting-only");
            return Set.of("finance");
        };

        AgentToolCatalogPort catalog =
                new CapabilityAwareAgentToolCatalogAdapter(selection, List.of(finance, workforce));

        assertThat(catalog.findEnabledTools("accounting-only"))
                .extracting(AgentToolDescriptor::toolId)
                .containsExactly("numou.finance.transactions.list");
    }

    @Test
    void rejectsMissingApplicationId() {
        AgentToolCatalogPort catalog = new CapabilityAwareAgentToolCatalogAdapter(
                applicationId -> Set.of(), List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.findEnabledTools(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AgentToolProvider provider(String capabilityId, String toolId) {
        return new AgentToolProvider() {
            @Override
            public String capabilityId() {
                return capabilityId;
            }

            @Override
            public List<AgentToolDescriptor> tools() {
                return List.of(new AgentToolDescriptor(
                        toolId, "1.0.0", capabilityId, "Read capability information",
                        capabilityId + ".read", AgentToolOperation.READ, false,
                        Map.of("type", "object")));
            }
        };
    }
}