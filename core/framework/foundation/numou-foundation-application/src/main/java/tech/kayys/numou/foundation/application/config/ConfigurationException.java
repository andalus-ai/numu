package tech.kayys.numou.foundation.application.config;

/**
 * Base exception for configuration failures (config01.md §P4-02 #24).
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
