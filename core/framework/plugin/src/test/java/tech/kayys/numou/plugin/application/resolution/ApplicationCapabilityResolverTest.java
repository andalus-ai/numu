package tech.kayys.numou.plugin.application.resolution;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.*;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ApplicationCapabilityResolverTest {
    @Test void requiredCapabilityIsResolvedAndOptionalAbsenceIsReported() {
        var manifest = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
            List.of(new CapabilityRequirement("numou.a-sales", "^1.0.0")),
            List.of(new CapabilityRequirement("numou.crm", "^1.0.0")), Set.of(), List.of());
        var provider = new CapabilityProvider("sales-provider", "numou.a-sales", "1.2.0", List.of());
        var result = new ApplicationCapabilityResolver().resolve(manifest, List.of(provider));
        assertThat(result.providersByCapability()).containsEntry("numou.a-sales", provider);
        assertThat(result.unavailableOptionalCapabilities()).containsExactly("numou.crm");
    }
    @Test void rejectsConflictingTransitiveRangesForSharedCapability() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(
                        new CapabilityRequirement("numou.a-sales", "^1.0.0"),
                        new CapabilityRequirement("numou.z-inventory", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var providers = List.of(
                new CapabilityProvider("sales", "numou.a-sales", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^1.0.0"))),
                new CapabilityProvider("inventory", "numou.z-inventory", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^2.0.0"))),
                new CapabilityProvider("party-v1", "numou.party", "1.2.0", List.of()));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Selected provider party-v1 supplies numou.party@1.2.0 which does not satisfy additional contract range ^2.0.0");
    }

    @Test void rejectsMissingTransitiveRequiredCapability() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.a-sales", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var providers = List.of(new CapabilityProvider("sales", "numou.a-sales", "1.0.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0"))));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessageContaining("Missing required capability provider for numou.party matching contract range ^1.0.0");
    }

    @Test void rejectsAmbiguousProvidersAndDependencyCycles() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.a-sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var resolver = new ApplicationCapabilityResolver();
        var ambiguous = List.of(
                new CapabilityProvider("a", "numou.a-sales", "1.0.0", List.of()),
                new CapabilityProvider("b", "numou.a-sales", "1.1.0", List.of()));
        assertThatThrownBy(() -> resolver.resolve(app, ambiguous))
                .isInstanceOf(CapabilityResolutionException.class).hasMessageContaining("exactly one");
        var cycle = List.of(
                new CapabilityProvider("sales", "numou.a-sales", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^1.0.0"))),
                new CapabilityProvider("party", "numou.party", "1.0.0",
                        List.of(new CapabilityRequirement("numou.a-sales", "^1.0.0"))));
        assertThatThrownBy(() -> resolver.resolve(app, cycle))
                .isInstanceOf(CapabilityResolutionException.class).hasMessageContaining("cycle");
    }

    @Test void initializationOrderIsStableAcrossProviderInputPermutations() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.zeta", "^1.0.0"),
                        new CapabilityRequirement("numou.alpha", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var alpha = new CapabilityProvider("alpha-provider", "numou.alpha", "1.0.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0")));
        var zeta = new CapabilityProvider("zeta-provider", "numou.zeta", "1.0.0", List.of());
        var party = new CapabilityProvider("party-provider", "numou.party", "1.0.0", List.of());
        var resolver = new ApplicationCapabilityResolver();

        var forward = resolver.resolve(app, List.of(alpha, zeta, party));
        var reverse = resolver.resolve(app, List.of(party, zeta, alpha));

        assertThat(forward.initializationOrder()).extracting(CapabilityProvider::providerId)
                .containsExactly("party-provider", "alpha-provider", "zeta-provider");
        assertThat(reverse.initializationOrder()).extracting(CapabilityProvider::providerId)
                .containsExactlyElementsOf(forward.initializationOrder().stream()
                        .map(CapabilityProvider::providerId).toList());
    }

    @Test void cycleDiagnosticsAreStableAcrossProviderInputPermutations() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var sales = new CapabilityProvider("sales-provider", "numou.sales", "1.0.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0")));
        var party = new CapabilityProvider("party-provider", "numou.party", "1.0.0",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")));
        var resolver = new ApplicationCapabilityResolver();

        var forwardFailure = catchThrowable(() -> resolver.resolve(app, List.of(sales, party)));
        var reverseFailure = catchThrowable(() -> resolver.resolve(app, List.of(party, sales)));

        assertThat(forwardFailure).isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Capability dependency cycle: numou.sales -> numou.party -> numou.sales");
        assertThat(reverseFailure).isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Capability dependency cycle: numou.sales -> numou.party -> numou.sales");
    }

    @Test void rejectsDuplicateProviderIdsAcrossCapabilities() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var providers = List.of(
                new CapabilityProvider("shared-provider-id", "numou.sales", "1.0.0", List.of()),
                new CapabilityProvider("shared-provider-id", "numou.party", "1.0.0", List.of()));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Duplicate provider ID: shared-provider-id");
    }

    @Test void rejectsProviderWithInvalidContractVersionAndIdentifiesProvider() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var provider = new CapabilityProvider("sales-provider", "numou.sales", "1.0", List.of());

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, List.of(provider)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid contract version for provider sales-provider: 1.0")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void providerDiagnosticsIncludeRequestedRangeAndStableCandidateDetails() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var resolver = new ApplicationCapabilityResolver();
        var candidates = List.of(
                new CapabilityProvider("z-provider", "numou.sales", "1.2.0", List.of()),
                new CapabilityProvider("a-provider", "numou.sales", "1.1.0", List.of()));

        var failure = catchThrowable(() -> resolver.resolve(app, candidates));
        assertThat(failure).isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Expected exactly one compatible provider for numou.sales matching ^1.0.0 "
                        + "but found 2: a-provider@1.1.0, z-provider@1.2.0");

        var missingApp = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.inventory", "~3.2.0")), List.of(), Set.of(), List.of());
        assertThatThrownBy(() -> resolver.resolve(missingApp, List.of()))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Missing required capability provider for numou.inventory matching contract range ~3.2.0");
    }

    @Test void conflictingTransitiveRequirementExplainsSelectedProviderVersion() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0"),
                        new CapabilityRequirement("numou.inventory", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var providers = List.of(
                new CapabilityProvider("sales-provider", "numou.sales", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^1.0.0"))),
                new CapabilityProvider("inventory-provider", "numou.inventory", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^2.0.0"))),
                new CapabilityProvider("party-v1-provider", "numou.party", "1.4.0", List.of()));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessage("Selected provider party-v1-provider supplies numou.party@1.4.0 "
                        + "which does not satisfy additional contract range ^2.0.0");
    }

    @Test void preflightsDuplicateAndConflictingDependenciesAcrossProviders() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var providers = List.of(
                new CapabilityProvider("z-sales-provider", "numou.sales", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^1.0.0"),
                                new CapabilityRequirement("numou.party", "^2.0.0"))),
                new CapabilityProvider("a-inventory-provider", "numou.inventory", "1.0.0",
                        List.of(new CapabilityRequirement("numou.stock", "~1.4.0"),
                                new CapabilityRequirement("numou.stock", "~1.4.0"))));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOfSatisfying(CapabilityResolutionException.class, error -> {
                    assertThat(error.diagnostics()).containsExactly(
                            "Provider a-inventory-provider declares duplicate dependency numou.stock with range ~1.4.0",
                            "Provider z-sales-provider declares conflicting dependency ranges for numou.party: ^1.0.0, ^2.0.0");
                    assertThat(error.getMessage()).isEqualTo(String.join("; ", error.diagnostics()));
                });
    }

    @Test void dependencyPreflightDiagnosticsAreIndependentOfProviderInputOrder() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var first = new CapabilityProvider("first", "numou.sales", "1.0.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0"),
                        new CapabilityRequirement("numou.party", "^2.0.0")));
        var second = new CapabilityProvider("second", "numou.inventory", "1.0.0",
                List.of(new CapabilityRequirement("numou.stock", "~1.4.0"),
                        new CapabilityRequirement("numou.stock", "~1.4.0")));
        var resolver = new ApplicationCapabilityResolver();

        var forward = catchThrowable(() -> resolver.resolve(app, List.of(first, second)));
        var reverse = catchThrowable(() -> resolver.resolve(app, List.of(second, first)));

        assertThat(forward).isInstanceOf(CapabilityResolutionException.class);
        assertThat(reverse).isInstanceOf(CapabilityResolutionException.class);
        assertThat(((CapabilityResolutionException) reverse).diagnostics())
                .containsExactlyElementsOf(((CapabilityResolutionException) forward).diagnostics());
    }

}
