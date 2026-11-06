package tech.kayys.numou.agent.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoverAgentToolsServiceTest {

    private static final AgentExecutionContext CONTEXT = new AgentExecutionContext(
            "accounting-app", "tenant-1", "user-7", Set.of("finance.read"), "corr-1");

    @Test
    void exposesOnlyEnabledToolsAuthorizedForVerifiedCallerAndSortsDeterministically() {
        AgentToolDescriptor transactions = tool("numou.finance.transactions.list", "finance.read");
        AgentToolDescriptor ledger = tool("numou.finance.ledger.summary", "finance.read");
        AgentToolDescriptor admin = tool("numou.application.configure", "application.admin");

        AgentToolCatalogPort catalog = applicationId -> {
            assertThat(applicationId).isEqualTo("accounting-app");
            return List.of(ledger, admin, transactions);
        };
        AgentToolAuthorizationPort authorization =
                (context, descriptor) -> context.permissions().contains(descriptor.requiredPermission());

        List<AgentToolDescriptor> result =
                new DiscoverAgentToolsService(catalog, authorization).discover(CONTEXT);

        assertThat(result).extracting(AgentToolDescriptor::toolId)
                .containsExactly("numou.finance.ledger.summary", "numou.finance.transactions.list");
    }

    @Test
    void rejectsMissingTenantOrActorContext() {
        assertThatThrownBy(() -> new AgentExecutionContext(
                "accounting-app", " ", "user-7", Set.of(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AgentExecutionContext(
                "accounting-app", "tenant-1", null, Set.of(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresApprovalForMutatingAndAdministrativeTools() {
        assertThatThrownBy(() -> new AgentToolDescriptor(
                "numou.finance.payment.post", "1.0.0", "finance.payment",
                "Post a payment", "finance.payment.post", AgentToolOperation.WRITE,
                false, Map.of("type", "object")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyInputSchema() {
        assertThatThrownBy(() -> toolWithSchema(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AgentToolDescriptor tool(String id, String permission) {
        return new AgentToolDescriptor(id, "1.0.0", "finance",
                "Read finance information", permission, AgentToolOperation.READ,
                false, Map.of("type", "object"));
    }

    private static AgentToolDescriptor toolWithSchema(Map<String, Object> schema) {
        return new AgentToolDescriptor("numou.test.tool", "1.0.0", "test",
                "Test tool", "test.read", AgentToolOperation.READ, false, schema);
    }
}