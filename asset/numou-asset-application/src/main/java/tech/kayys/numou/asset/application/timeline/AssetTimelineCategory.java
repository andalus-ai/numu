package tech.kayys.numou.asset.application.timeline;

/**
 * Coarse classification of an {@link AssetTimelineEntry} (ASSET-27 §4).
 *
 * <p>Deliberately generic so every sector of the Asset ecosystem (lifecycle,
 * maintenance, finance, ...) can be filtered independently by the timeline
 * query API and, later, by a visibility policy (§28).</p>
 */
public enum AssetTimelineCategory {

    LIFECYCLE,

    LOCATION,

    CUSTODY,

    MOVEMENT,

    CLASSIFICATION,

    MAINTENANCE,

    INSPECTION,

    METER,

    WARRANTY,

    DOCUMENT,

    FINANCIAL,

    AVAILABILITY,

    UTILIZATION,

    RELATIONSHIP,

    OTHER
}