package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TrustedCapabilityRuntimeCatalogTest {
    private static CapabilityProvider provider(String id) {
        return new CapabilityProvider(id, "ledger", "1.0.0", List.of());
    }

    private static CapabilityRuntime runtime(String id) {
        return () -> id;
    }

    @Test
    void createsOnlyExplicitlyRegisteredProviderRuntimes() {
        var catalog = TrustedCapabilityRuntimeCatalog.builder()
                .register("ledger-postgres", ignored -> runtime("ledger-postgres"))
                .build();

        assertThat(catalog.supports("ledger-postgres")).isTrue();
        assertThat(catalog.providerIds()).containsExactly("ledger-postgres");
        assertThat(catalog.create(provider("ledger-postgres")).providerId()).isEqualTo("ledger-postgres");
        assertThatThrownBy(() -> catalog.create(provider("ledger-memory")))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("No trusted runtime factory");
    }

    @Test
    void rejectsDuplicateAndBlankProviderRegistrations() {
        var builder = TrustedCapabilityRuntimeCatalog.builder()
                .register("ledger-postgres", ignored -> runtime("ledger-postgres"));

        assertThatThrownBy(() -> builder.register("ledger-postgres", ignored -> runtime("ledger-postgres")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> builder.register(" ", ignored -> runtime(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider ID is required");
    }

    @Test
    void rejectsFactoryIdentityMismatchAndNullRuntime() {
        var mismatch = TrustedCapabilityRuntimeCatalog.builder()
                .register("ledger-postgres", ignored -> runtime("ledger-memory"))
                .build();
        assertThatThrownBy(() -> mismatch.create(provider("ledger-postgres")))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("identity mismatch");

        var nullFactory = TrustedCapabilityRuntimeCatalog.builder()
                .register("ledger-postgres", ignored -> null)
                .build();
        assertThatThrownBy(() -> nullFactory.create(provider("ledger-postgres")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void catalogIsAnImmutableSnapshotOfBuilderRegistrations() {
        var builder = TrustedCapabilityRuntimeCatalog.builder()
                .register("ledger-postgres", ignored -> runtime("ledger-postgres"));
        var catalog = builder.build();
        builder.register("party-postgres", ignored -> runtime("party-postgres"));

        assertThat(catalog.providerIds()).containsExactly("ledger-postgres");
        assertThat(catalog.supports("party-postgres")).isFalse();
    }
}
