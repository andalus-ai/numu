package tech.kayys.numou.foundation.application.runtime;

import java.util.Optional;
import java.util.Set;

/**
 * Registry holding pluggable runtime components (config01.md §P4-01 #3).
 */
public interface ApplicationComponentRegistry {

    void register(RuntimeComponent component);

    Optional<RuntimeComponent> find(String id);

    Set<RuntimeComponent> components();
}
