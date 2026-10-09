package tech.kayys.syirkah.foundation.kernel.deployment;

/**
 * Defines the deployment topology of the Syirkah platform instance.
 * Allows the same domain codebase to be deployed in different architectures.
 */
public enum DeploymentTopology {

    /**
     * Standalone / edge single application (e.g. offline POS terminal, desktop, single-store app).
     * Bypasses heavy distributed infrastructure (uses in-memory event bus and local database).
     */
    STANDALONE,

    /**
     * Modular monolith: all or selected modules running in a single JVM process.
     * High performance in-process communication between bounded contexts.
     */
    MODULAR_MONOLITH,

    /**
     * Independent microservice: single bounded context deployed as an autonomous service,
     * communicating over-the-wire via gRPC / REST / Kafka.
     */
    MICROSERVICE,

    /**
     * Cloud-native multi-tenant SaaS: shared or dedicated database with strict tenant isolation.
     */
    SAAS_MULTI_TENANT;

    public boolean isStandalone() {
        return this == STANDALONE;
    }

    public boolean isDistributed() {
        return this == MICROSERVICE || this == SAAS_MULTI_TENANT;
    }
}
