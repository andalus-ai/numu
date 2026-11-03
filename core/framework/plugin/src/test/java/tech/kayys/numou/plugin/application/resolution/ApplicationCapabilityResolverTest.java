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
}
