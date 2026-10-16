package tech.kayys.syirkah.asset.domain.valueobject;

/**
 * Lifecycle status of an Asset. This is deliberately the ONLY lifecycle
 * dimension; availability, custody, maintenance and utilization are
 * modelled as separate state dimensions (see ASSET-12/15).
 */
public enum AssetStatus {

    DRAFT,

    ACTIVE,

    SUSPENDED,

    RETIRED,

    DISPOSED;

    public boolean isTerminal() {
        return this == DISPOSED;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
