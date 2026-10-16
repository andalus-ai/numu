package tech.kayys.numou.foundation.application.runtime;

import java.util.Optional;
import java.util.Set;

/**
 * Registry holding discovered and configured application modules.
 */
public interface ModuleRegistry {

    void register(ApplicationModule module);

    Optional<ApplicationModule> find(String id);

    Set<ApplicationModule> modules();
}
