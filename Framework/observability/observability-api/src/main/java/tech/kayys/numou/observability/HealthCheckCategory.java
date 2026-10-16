package tech.kayys.syirkah.observability;

/**
 * Operational category of a health check (config02.md §P4-07 #6).
 */
public enum HealthCheckCategory {
    RUNTIME,
    DATABASE,
    CACHE,
    MESSAGE_BROKER,
    OUTBOX,
    INBOX,
    STORAGE,
    MODULE,
    EXTERNAL_DEPENDENCY,
    SECURITY,
    CONFIGURATION
}
