package tech.kayys.numou.foundation.application.config.profile;

/**
 * Resolves a profile with its complete inheritance chain and cycle detection (config01.md §P4-03 #14, #15).
 */
public interface ProfileResolver {

    ResolvedProfile resolve(ConfigurationProfileId profileId);
}
