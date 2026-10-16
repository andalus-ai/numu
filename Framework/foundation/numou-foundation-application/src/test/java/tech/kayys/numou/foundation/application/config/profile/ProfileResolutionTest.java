package tech.kayys.numou.foundation.application.config.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Profile Resolution & Inheritance Tests")
class ProfileResolutionTest {

    @Test
    @DisplayName("Should resolve single profile with default parent")
    void shouldResolveSingleProfile() {
        ProfileConfiguration registry = new DefaultProfileConfiguration();
        registry.register(ConfigurationProfile.builder("production")
                .parent(ConfigurationProfileId.DEFAULT)
                .property("numou.runtime.startup-timeout", "2m")
                .build());

        ProfileResolver resolver = new DefaultProfileResolver(registry);
        ResolvedProfile resolved = resolver.resolve(ConfigurationProfileId.of("production"));

        assertEquals(ConfigurationProfileId.of("production"), resolved.activeProfile());
        assertEquals(
                List.of(ConfigurationProfileId.DEFAULT, ConfigurationProfileId.of("production")),
                resolved.inheritanceChain()
        );
        assertEquals("2m", resolved.properties().get("numou.runtime.startup-timeout"));
    }

    @Test
    @DisplayName("Should resolve multi-level inheritance chain with overriding")
    void shouldResolveMultiLevelChain() {
        ProfileConfiguration registry = new DefaultProfileConfiguration();
        registry.register(ConfigurationProfile.builder("staging")
                .parent(ConfigurationProfileId.DEFAULT)
                .property("db.pool", "10")
                .property("log.level", "INFO")
                .build());

        registry.register(ConfigurationProfile.builder("staging-perf")
                .parent("staging")
                .property("db.pool", "50")
                .build());

        ProfileResolver resolver = new DefaultProfileResolver(registry);
        ResolvedProfile resolved = resolver.resolve(ConfigurationProfileId.of("staging-perf"));

        assertEquals(
                List.of(
                        ConfigurationProfileId.DEFAULT,
                        ConfigurationProfileId.of("staging"),
                        ConfigurationProfileId.of("staging-perf")
                ),
                resolved.inheritanceChain()
        );
        assertEquals("50", resolved.properties().get("db.pool"));
        assertEquals("INFO", resolved.properties().get("log.level"));
    }

    @Test
    @DisplayName("Should detect and reject circular profile inheritance")
    void shouldRejectCircularInheritance() {
        ProfileConfiguration registry = new DefaultProfileConfiguration();
        registry.register(ConfigurationProfile.builder("a")
                .parent("b")
                .build());
        registry.register(ConfigurationProfile.builder("b")
                .parent("a")
                .build());

        ProfileResolver resolver = new DefaultProfileResolver(registry);
        ProfileValidationException ex = assertThrows(ProfileValidationException.class, () ->
                resolver.resolve(ConfigurationProfileId.of("a")));
        assertTrue(ex.getMessage().contains("Circular profile inheritance"));
    }
}
