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
                .hasMessageContaining("Conflicting contract ranges for capability numou.party");
    }

    @Test void rejectsMissingTransitiveRequiredCapability() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.a-sales", "^1.0.0")),
                List.of(), Set.of(), List.of());
        var providers = List.of(new CapabilityProvider("sales", "numou.a-sales", "1.0.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0"))));

        assertThatThrownBy(() -> new ApplicationCapabilityResolver().resolve(app, providers))
                .isInstanceOf(CapabilityResolutionException.class)
                .hasMessageContaining("Missing required capability provider: numou.party");
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
}
