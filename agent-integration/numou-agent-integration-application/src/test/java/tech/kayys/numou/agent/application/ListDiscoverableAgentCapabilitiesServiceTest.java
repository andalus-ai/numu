package tech.kayys.numou.agent.application;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ListDiscoverableAgentCapabilitiesServiceTest {
    @Test
    void listsOnlyAuthorizedCapabilitiesInStableOrderWithoutDuplicates() {
        AgentExecutionContext context = new AgentExecutionContext(
                "erp-app", "tenant-9", "user-3", Set.of("finance.read", "workforce.read"), "corr-9");
        List<AgentToolDescriptor> tools = List.of(
                tool("numou.finance.summary", "finance", "finance.read"),
                tool("numou.finance.transactions.list", "finance", "finance.read"),
                tool("numou.workforce.profile.get", "workforce", "workforce.read"),
                tool("numou.payroll.run", "payroll", "payroll.write"));
        DiscoverAgentToolsService discovery = new DiscoverAgentToolsService(
                (applicationId, tenantId) -> {
                    assertThat(applicationId).isEqualTo("erp-app");
                    assertThat(tenantId).isEqualTo("tenant-9");
                    return tools;
                },
                (caller, descriptor) -> caller.permissions().contains(descriptor.requiredPermission()));
        assertThat(new ListDiscoverableAgentCapabilitiesService(discovery).list(context))
                .containsExactly("finance", "workforce");
    }

    private static AgentToolDescriptor tool(String id, String capability, String permission) {
        return new AgentToolDescriptor(id, "1.0.0", capability, "Capability operation",
                permission, AgentToolOperation.READ, false, Map.of("type", "object"));
    }
}
