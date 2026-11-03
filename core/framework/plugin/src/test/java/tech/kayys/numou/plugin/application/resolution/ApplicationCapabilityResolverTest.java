package tech.kayys.numou.plugin.application.resolution;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.*;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ApplicationCapabilityResolverTest {
    @Test void requiredCapabilityIsResolvedAndOptionalAbsenceIsReported() {
        var manifest = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
            List.of(new CapabilityRequirement("numou.sales", "^1.0.0")),
            List.of(new CapabilityRequirement("numou.crm", "^1.0.0")), Set.of(), List.of());
        var provider = new CapabilityProvider("sales-provider", "numou.sales", "1.2.0", List.of());
        var result = new ApplicationCapabilityResolver().resolve(manifest, List.of(provider));
        assertThat(result.providersByCapability()).containsEntry("numou.sales", provider);
        assertThat(result.unavailableOptionalCapabilities()).containsExactly("numou.crm");
    }
    @Test void rejectsAmbiguousProvidersAndDependencyCycles() {
        var app = new ApplicationManifest("com.example.app", "1.0.0", "com.example", "Example",
                List.of(new CapabilityRequirement("numou.sales", "^1.0.0")), List.of(), Set.of(), List.of());
        var resolver = new ApplicationCapabilityResolver();
        var ambiguous = List.of(
                new CapabilityProvider("a", "numou.sales", "1.0.0", List.of()),
                new CapabilityProvider("b", "numou.sales", "1.1.0", List.of()));
        assertThatThrownBy(() -> resolver.resolve(app, ambiguous))
                .isInstanceOf(CapabilityResolutionException.class).hasMessageContaining("exactly one");
        var cycle = List.of(
                new CapabilityProvider("sales", "numou.sales", "1.0.0",
                        List.of(new CapabilityRequirement("numou.party", "^1.0.0"))),
                new CapabilityProvider("party", "numou.party", "1.0.0",
                        List.of(new CapabilityRequirement("numou.sales", "^1.0.0"))));
        assertThatThrownBy(() -> resolver.resolve(app, cycle))
                .isInstanceOf(CapabilityResolutionException.class).hasMessageContaining("cycle");
    }
}
