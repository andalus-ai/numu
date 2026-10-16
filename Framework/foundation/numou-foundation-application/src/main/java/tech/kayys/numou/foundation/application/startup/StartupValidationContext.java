package tech.kayys.numou.foundation.application.startup;

import tech.kayys.numou.foundation.application.config.ConfigurationSnapshot;
import tech.kayys.numou.foundation.application.config.profile.ResolvedProfile;
import tech.kayys.numou.foundation.application.runtime.ApplicationComponentRegistry;
import tech.kayys.numou.foundation.application.runtime.ApplicationRuntimeState;
import tech.kayys.numou.foundation.application.runtime.ModuleRegistry;
import tech.kayys.numou.foundation.application.runtime.RuntimeRegistry;

/**
 * Read-only context provided to startup checks (config01.md §P4-04 #6).
 */
public interface StartupValidationContext {

    ConfigurationSnapshot configuration();

    ResolvedProfile profile();

    RuntimeRegistry runtime();

    ModuleRegistry modules();

    ApplicationComponentRegistry components();

    ApplicationRuntimeState runtimeState();
}
