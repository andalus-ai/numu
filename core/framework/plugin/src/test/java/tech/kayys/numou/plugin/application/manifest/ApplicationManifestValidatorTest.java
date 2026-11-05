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

    @Test void reportsInvalidSemVerCapabilityRangesAsManifestViolations() {
        String invalid = valid()
                .replace("\"contractRange\":\"^1.0.0\"", "\"contractRange\":\"^1.0.0-01\"")
                .replace("\"requires\":{", "\"requires\":{\"optionalCapabilities\":[{\"id\":\"numou.crm\",\"contractRange\":\"~1.0.0-02\"}],");
        assertThatThrownBy(() -> validator().validate(invalid, Set.of("commerce.pos.operate")))
                .isInstanceOfSatisfying(ManifestValidationException.class, error ->
                        assertThat(error.violations())
                                .anySatisfy(v -> assertThat(v).contains(
                                        "requires.capabilities[0].contractRange",
                                        "Invalid SemVer prerelease identifier: 01"))
                                .anySatisfy(v -> assertThat(v).contains(
                                        "requires.optionalCapabilities[0].contractRange",
                                        "Invalid SemVer prerelease identifier: 02")));
    }
    @Test void distinguishesDuplicateAndConflictingCapabilityDeclarations() {
        String duplicate = valid().replace(
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}]",
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"},"
                        + "{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}]");
        assertThatThrownBy(() -> validator().validate(duplicate, Set.of("commerce.pos.operate")))
                .isInstanceOfSatisfying(ManifestValidationException.class, error ->
                        assertThat(error.violations()).contains(
                                "Duplicate required capability requirement: numou.party"));

        String conflict = valid().replace(
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}]",
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"},"
                        + "{\"id\":\"numou.party\",\"contractRange\":\"^2.0.0\"}]");
        assertThatThrownBy(() -> validator().validate(conflict, Set.of("commerce.pos.operate")))
                .isInstanceOfSatisfying(ManifestValidationException.class, error ->
                        assertThat(error.violations()).contains(
                                "Conflicting required capability contract ranges for numou.party: ^1.0.0 and ^2.0.0"));
    }

    @Test void rejectsCapabilityDeclaredAsBothRequiredAndOptional() {
        String invalid = valid().replace("\"requires\":{",
                "\"requires\":{\"optionalCapabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}],");
        assertThatThrownBy(() -> validator().validate(invalid, Set.of("commerce.pos.operate")))
                .isInstanceOfSatisfying(ManifestValidationException.class, error ->
                        assertThat(error.violations()).contains(
                                "Capability cannot be both required and optional: numou.party"));
    }

    @Test void sortsSemanticManifestViolationsDeterministically() {
        String forward = valid()
                .replace("\"permissions\":[\"commerce.pos.operate\"]",
                        "\"permissions\":[\"unknown.z\",\"unknown.a\"]")
                .replace("\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}]",
                        "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"},"
                                + "{\"id\":\"numou.party\",\"contractRange\":\"^2.0.0\"}]");
        String reverse = forward.replace(
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"},"
                        + "{\"id\":\"numou.party\",\"contractRange\":\"^2.0.0\"}]",
                "\"capabilities\":[{\"id\":\"numou.party\",\"contractRange\":\"^2.0.0\"},"
                        + "{\"id\":\"numou.party\",\"contractRange\":\"^1.0.0\"}]");

        assertThat(violations(forward)).containsExactlyElementsOf(violations(reverse))
                .isSorted();
    }

    private java.util.List<String> violations(String manifest) {
        try {
            validator().validate(manifest, Set.of());
            fail("Expected manifest validation to fail");
            return java.util.List.of();
        } catch (ManifestValidationException error) {
            return error.violations();
        }
    }

}
