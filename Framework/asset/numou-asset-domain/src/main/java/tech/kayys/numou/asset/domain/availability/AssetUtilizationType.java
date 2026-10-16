package tech.kayys.syirkah.asset.domain.availability;

/**
 * Semantic kind of a utilization observation (ASSET-26 §16).
 *
 * <p>Kept intentionally generic: the same enum is reusable for vehicles
 * (trip/operating hours), machines (operating/working time) and buildings
 * (assigned time). The precise business meaning stays configurable at the
 * reporting/read-model level.</p>
 */
public enum AssetUtilizationType {

    OPERATING_TIME,

    ASSIGNED_TIME,

    ACTIVE_TIME,

    TRIP,

    WORKING_TIME,

    OTHER
}
