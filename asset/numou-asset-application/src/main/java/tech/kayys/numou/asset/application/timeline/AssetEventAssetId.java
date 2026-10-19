package tech.kayys.numou.asset.application.timeline;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.UUID;

/**
 * Resolves the operational asset id a business event refers to (ASSET-27 §2).
 *
 * <p>The Asset domain events intentionally do not share a common
 * {@code assetId()} supertype (some reference a source asset, a parent asset or
 * an operational asset). The read-side projects any of those onto the timeline,
 * so resolution is done here, once, by probing the known accessor names. It
 * lives in the application/read layer — never in the aggregate — preserving the
 * "timeline is a curated read model" boundary (§3).</p>
 */
public final class AssetEventAssetId {

    private static final String[] ACCESSORS = {
            "assetId",
            "operationalAssetId",
            "sourceAssetId",
            "parentAssetId",
            "componentAssetId",
            "resourceAssetId"
    };

    private AssetEventAssetId() {
    }

    /** Returns the referenced asset id, or {@code null} when the event is not asset-scoped. */
    public static UUID resolve(DomainEvent event) {
        if (event == null) {
            return null;
        }
        for (String accessor : ACCESSORS) {
            Object value = invoke(event, accessor);
            if (value instanceof UUID id) {
                return id;
            }
        }
        return null;
    }

    private static Object invoke(DomainEvent event, String accessor) {
        try {
            return event.getClass().getMethod(accessor).invoke(event);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }
}