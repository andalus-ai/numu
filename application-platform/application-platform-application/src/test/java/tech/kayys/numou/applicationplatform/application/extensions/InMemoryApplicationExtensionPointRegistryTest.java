package tech.kayys.numou.applicationplatform.application.extensions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InMemoryApplicationExtensionPointRegistryTest {
    @Test
    void registrationIsIdempotentAndEnumerationIsDeterministic() {
        var registry = new InMemoryApplicationExtensionPointRegistry();
        var workspace = new ApplicationExtensionPoint("numou.workspace.sidebar", "1.2.0",
                "urn:numou:schema:workspace-sidebar:v1");
        var commerce = new ApplicationExtensionPoint("numou.commerce.checkout", "2.0.0",
                "urn:numou:schema:checkout:v2");

        registry.register(workspace);
        registry.register(workspace);
        registry.register(commerce);

        assertThat(registry.all()).containsExactly(commerce, workspace);
        assertThat(registry.find(workspace.id())).contains(workspace);
    }

    @Test
    void rejectsConflictingContractAndRequiresExpectedVersionForRemoval() {
        var registry = new InMemoryApplicationExtensionPointRegistry();
        registry.register(new ApplicationExtensionPoint("numou.workspace.sidebar", "1.0.0",
                "urn:numou:schema:workspace-sidebar:v1"));

        assertThatThrownBy(() -> registry.register(new ApplicationExtensionPoint(
                "numou.workspace.sidebar", "2.0.0", "urn:numou:schema:workspace-sidebar:v2")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.unregister("numou.workspace.sidebar", "2.0.0")).isFalse();
        assertThat(registry.find("numou.workspace.sidebar")).isPresent();
        assertThat(registry.unregister("numou.workspace.sidebar", "1.0.0")).isTrue();
        assertThat(registry.find("numou.workspace.sidebar")).isEmpty();
        assertThat(registry.unregister("numou.workspace.sidebar", "1.0.0")).isFalse();
    }

    @Test
    void validatesVersionAndIdentifierAtRegistrationBoundary() {
        assertThatThrownBy(() -> new ApplicationExtensionPoint(
                "not-an-id", "1.0.0", "urn:test:schema")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationExtensionPoint(
                "numou.workspace.sidebar", "1.x", "urn:test:schema")).isInstanceOf(IllegalArgumentException.class);
    }
}
