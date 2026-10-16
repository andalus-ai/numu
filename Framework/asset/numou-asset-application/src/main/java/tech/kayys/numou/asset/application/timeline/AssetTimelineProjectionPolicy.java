package tech.kayys.numou.asset.application.timeline;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.UUID;

/**
 * Maps a business event onto a timeline entry (ASSET-27 §3, §8).
 *
 * <p>Classification is driven by the event's stable {@code eventType} string
 * rather than its Java type, so the read model does not depend on the concrete
 * event classes and stays tolerant to additive changes (§5, §10). Events that
 * are not asset-scoped return {@code null} and are simply not projected
 * (timeline is a curated view, not a raw event dump — §2).</p>
 */
public final class AssetTimelineProjectionPolicy {

    private AssetTimelineProjectionPolicy() {
    }

    public static AssetTimelineEntry project(String tenantId, UUID assetId, DomainEvent event) {
        if (tenantId == null || assetId == null || event == null) {
            return null;
        }
        String type = event.eventType();
        AssetTimelineCategory category = categoryOf(type);
        if (category == null) {
            return null;
        }
        return new AssetTimelineEntry(
                UUID.randomUUID(),
                tenantId,
                assetId,
                event.occurredAt(),
                type,
                category,
                titleOf(type, category),
                null,
                sourceOf(type),
                null,
                event.eventId());
    }

    static AssetTimelineCategory categoryOf(String type) {
        if (isLifecycle(type)) {
            return AssetTimelineCategory.LIFECYCLE;
        }
        if (type.startsWith("asset.asset-location") || type.startsWith("asset.location")) {
            return AssetTimelineCategory.LOCATION;
        }
        if (type.startsWith("asset.asset-assigned") || type.startsWith("asset.asset-unassigned")
                || type.equals("asset.assigned") || type.equals("asset.unassigned")) {
            return AssetTimelineCategory.CUSTODY;
        }
        if (type.startsWith("asset.asset-classified") || type.startsWith("asset.asset-classification-cleared")) {
            return AssetTimelineCategory.CLASSIFICATION;
        }
        if (type.startsWith("asset.asset-relationship") || type.startsWith("asset.component-")) {
            return AssetTimelineCategory.RELATIONSHIP;
        }
        if (type.startsWith("asset.inspection-")) {
            return AssetTimelineCategory.INSPECTION;
        }
        if (type.equals("asset.meter-reading-recorded")) {
            return AssetTimelineCategory.METER;
        }
        if (type.startsWith("maintenance.")) {
            return AssetTimelineCategory.MAINTENANCE;
        }
        if (type.startsWith("asset.warranty") || type.startsWith("asset.service-contract")) {
            return AssetTimelineCategory.WARRANTY;
        }
        if (type.startsWith("asset.document-")) {
            return AssetTimelineCategory.DOCUMENT;
        }
        if (type.startsWith("accounting.fixed-asset") || type.equals("asset.accounting-linked")) {
            return AssetTimelineCategory.FINANCIAL;
        }
        if (type.equals("asset.asset-available") || type.equals("asset.asset-unavailable")
                || type.equals("asset.availability-marked")) {
            return AssetTimelineCategory.AVAILABILITY;
        }
        if (type.equals("asset.utilization-recorded")) {
            return AssetTimelineCategory.UTILIZATION;
        }
        return null;
    }

    private static boolean isLifecycle(String type) {
        return switch (type) {
            case "asset.asset-created", "asset.asset-activated", "asset.asset-suspended",
                    "asset.asset-retired", "asset.asset-disposed" -> true;
            default -> false;
        };
    }

    private static String titleOf(String type, AssetTimelineCategory category) {
        return switch (type) {
            case "asset.asset-created" -> "Asset created";
            case "asset.asset-activated" -> "Asset activated";
            case "asset.asset-suspended" -> "Asset suspended";
            case "asset.asset-retired" -> "Asset retired";
            case "asset.asset-disposed" -> "Asset disposed";
            case "asset.asset-location-changed" -> "Location changed";
            case "asset.asset-location-cleared" -> "Location cleared";
            case "asset.asset-assigned" -> "Assigned";
            case "asset.asset-unassigned" -> "Unassigned";
            case "asset.asset-classified" -> "Classified";
            case "asset.asset-classification-cleared" -> "Classification cleared";
            case "asset.asset-relationship-created" -> "Relationship added";
            case "asset.asset-relationship-removed" -> "Relationship removed";
            case "asset.component-installed" -> "Component installed";
            case "asset.component-removed" -> "Component removed";
            case "asset.meter-reading-recorded" -> "Meter reading recorded";
            case "asset.document-attached" -> "Document attached";
            case "asset.document-detached" -> "Document detached";
            case "asset.document-primary-set" -> "Primary document set";
            case "asset.accounting-linked" -> "Accounting asset linked";
            case "asset.asset-available" -> "Asset available";
            case "asset.asset-unavailable" -> "Asset unavailable";
            case "asset.utilization-recorded" -> "Utilization recorded";
            default -> humanise(type, category);
        };
    }

    private static String humanise(String type, AssetTimelineCategory category) {
        String tail = type.contains(".") ? type.substring(type.indexOf('.') + 1) : type;
        String words = tail.replace('-', ' ');
        if (words.isEmpty()) {
            words = category.name().toLowerCase();
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String sourceOf(String type) {
        int dot = type.indexOf('.');
        return dot > 0 ? type.substring(0, dot) : null;
    }
}