package tech.kayys.numou.agent.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescribeAgentToolServiceTest {
    private static final AgentExecutionContext CONTEXT = new AgentExecutionContext(
            "workforce-app", "tenant-1", "operator-2", Set.of("workforce.read"), "corr-2");

    @Test
    void describesOnlyToolsVisibleThroughAuthorizedDiscovery() {
        AgentToolDescriptor visible = tool(
                "numou.workforce.profile.get", "workforce", "workforce.read");
        AgentToolDescriptor hidden = tool(
                "numou.workforce.profile.update", "workforce", "workforce.write");
        DiscoverAgentToolsService discovery = new DiscoverAgentToolsService(
                applicationId -> List.of(visible, hidden),
                (context, descriptor) -> context.permissions().contains(descriptor.requiredPermission()));

        DescribeAgentToolService service = new DescribeAgentToolService(discovery);

        assertThat(service.describe(CONTEXT, visible.toolId())).contains(visible);
        assertThat(service.describe(CONTEXT, hidden.toolId())).isEmpty();
    }

    @Test
    void rejectsBlankToolId() {
        DiscoverAgentToolsService discovery = new DiscoverAgentToolsService(
                applicationId -> List.of(), (context, descriptor) -> true);

        assertThatThrownBy(() -> new DescribeAgentToolService(discovery).describe(CONTEXT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AgentToolDescriptor tool(String id, String capability, String permission) {
        return new AgentToolDescriptor(id, "1.0.0", capability, "Read workforce data",
                permission, AgentToolOperation.READ, false, Map.of("type", "object"));
    }
}
