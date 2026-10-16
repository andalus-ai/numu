package tech.kayys.numou.foundation.application.runtime;

/**
 * Extension point for modules to participate in the application runtime (config01.md §P4-01 #10).
 */
public interface ApplicationModule {

    String id();

    default String description() {
        return id();
    }
}
