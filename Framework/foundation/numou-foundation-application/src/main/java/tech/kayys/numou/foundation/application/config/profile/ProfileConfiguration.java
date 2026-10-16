package tech.kayys.numou.foundation.application.config.profile;

import java.util.Optional;
import java.util.Set;

/**
 * Registry of available configuration profiles (config01.md §P4-03 #12).
 */
public interface ProfileConfiguration {

    void register(ConfigurationProfile profile);

    Optional<ConfigurationProfile> find(ConfigurationProfileId id);

    default ConfigurationProfile require(ConfigurationProfileId id) {
        return find(id).orElseThrow(() ->
                new ProfileValidationException("Profile not registered: " + id.value()));
    }

    Set<ConfigurationProfileId> profiles();
}
