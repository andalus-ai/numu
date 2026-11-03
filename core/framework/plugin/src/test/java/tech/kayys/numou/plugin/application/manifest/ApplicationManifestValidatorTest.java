package tech.kayys.numou.plugin.application.manifest;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ApplicationManifestValidatorTest {
    private ApplicationManifestValidator validator() {
        InputStream schema = getClass().getResourceAsStream("/META-INF/numou/application-v1alpha1.schema.json");
        return new ApplicationManifestValidator(schema);
    }
    private String valid() {
        return """
          {"apiVersion":"platform.numou/v1alpha1","kind":"Application",
           "metadata":{"id":"com.example.retail","version":"1.0.0","publisher":"com.example","displayName":"Retail"},
           "requires":{"capabilities":[{"id":"numou.party","contractRange":"^1.0.0"}]},
           "permissions":["commerce.pos.operate"],
           "contributions":{"navigation":[{"id":"com.example.retail.sales","label":"Sales","route":"/sales","requiredPermission":"commerce.pos.operate"}]}}
          """;
    }
    @Test void acceptsValidManifest() {
        var manifest = validator().validate(valid(), Set.of("commerce.pos.operate"));
        assertThat(manifest.id()).isEqualTo("com.example.retail");
        assertThat(manifest.requiredCapabilities()).hasSize(1);
    }
    @Test void rejectsUnknownPropertiesAndPermissions() {
        String unknown = valid().replace("\"displayName\":\"Retail\"", "\"displayName\":\"Retail\",\"tenantId\":\"x\"");
        assertThatThrownBy(() -> validator().validate(unknown, Set.of("commerce.pos.operate")))
            .isInstanceOf(ManifestValidationException.class);
        assertThatThrownBy(() -> validator().validate(valid(), Set.of()))
            .isInstanceOf(ManifestValidationException.class).hasMessageContaining("Unknown permission");
    }
    @Test void rejectsUndeclaredNavigationPermission() {
        String invalid = valid().replace("\"requiredPermission\":\"commerce.pos.operate\"",
            "\"requiredPermission\":\"commerce.catalog.read\"");
        assertThatThrownBy(() -> validator().validate(invalid, Set.of("commerce.pos.operate")))
            .isInstanceOf(ManifestValidationException.class).hasMessageContaining("not declared");
    }
}
