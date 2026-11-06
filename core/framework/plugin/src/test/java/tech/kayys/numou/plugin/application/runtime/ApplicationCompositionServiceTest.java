package tech.kayys.numou.plugin.application.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolutionEdge;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationCompositionServiceTest {

    @Test
    void createsRuntimePlanInDependencyInitializationOrder() {
        var manifest = new ApplicationManifest("com.example.accounting", "2.1.0",
                "com.example", "Accounting", List.of(
                new CapabilityRequirement("numou.accounting", "^2.0.0")),
                List.of(new CapabilityRequirement("numou.documents", "^1.0.0")),
                Set.of(), List.of());
        var accounting = new CapabilityProvider("accounting-provider", "numou.accounting", "2.2.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0")));
        var party = new CapabilityProvider("party-provider", "numou.party", "1.4.0", List.of());

        var composition = new ApplicationCompositionService()
                .compose(manifest, List.of(accounting, party));

        assertThat(composition.applicationId()).isEqualTo("com.example.accounting");
        assertThat(composition.applicationVersion()).isEqualTo("2.1.0");
        assertThat(composition.initializationOrder())
                .extracting(CapabilityProvider::providerId)
                .containsExactly("party-provider", "accounting-provider");
        assertThat(composition.resolutionReport().dependencyEdges())
                .extracting(CapabilityResolutionEdge::targetCapability)
                .containsExactly("numou.accounting", "numou.documents", "numou.party");
        assertThat(composition.resolutionReport().unavailableOptionalCapabilities())
                .containsExactly("numou.documents");
    }

    @Test
    void sameCompositionBoundarySupportsSingleAndMultiCapabilityApplications() {
        var service = new ApplicationCompositionService();
        var provider = new CapabilityProvider("ledger-provider", "numou.ledger", "1.0.0", List.of());
        var standaloneManifest = new ApplicationManifest("com.example.ledger", "1.0.0",
                "com.example", "Ledger", List.of(
                new CapabilityRequirement("numou.ledger", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var modularManifest = new ApplicationManifest("com.example.erp", "1.0.0",
                "com.example", "ERP", List.of(
                new CapabilityRequirement("numou.ledger", "^1.0.0"),
                new CapabilityRequirement("numou.party", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var party = new CapabilityProvider("party-provider", "numou.party", "1.2.0", List.of());

        var standalone = service.compose(standaloneManifest, List.of(provider, party));
        var modular = service.compose(modularManifest, List.of(provider, party));

        assertThat(standalone.initializationOrder()).containsExactly(provider);
        assertThat(modular.initializationOrder()).containsExactly(provider, party);
        assertThat(standalone.applicationId()).isNotEqualTo(modular.applicationId());
    }

    @Test
    void rejectsInvalidCompositionInsteadOfReturningPartialRuntimePlan() {
        var manifest = new ApplicationManifest("com.example.app", "1.0.0",
                "com.example", "Example", List.of(
                new CapabilityRequirement("numou.missing", "^1.0.0")),
                List.of(), Set.of(), List.of());

        assertThatThrownBy(() -> new ApplicationCompositionService().compose(manifest, List.of()))
                .hasMessage("Missing required capability provider for numou.missing matching contract range ^1.0.0");
    }
}
