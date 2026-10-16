package tech.kayys.numou.foundation.application.config;

import java.util.Map;

/**
 * A source of configuration properties with defined precedence priority (config01.md §P4-02 #9, #10).
 */
public interface ConfigurationSource {

    /**
     * Human-readable source identifier (e.g. "default", "environment", "system-properties").
     */
    String name();

    /**
     * Precedence priority where higher numbers take precedence over lower numbers.
     * <pre>
     * DEFAULT (100) < FILE (200) < PROFILE (300) < ENVIRONMENT (400) < SYSTEM_PROPERTY (500) < RUNTIME_OVERRIDE (600)
     * </pre>
     */
    int priority();

    /**
     * Loads raw key-value string configuration entries.
     */
    Map<String, String> load();
}
