package tech.kayys.numou.asset.domain.meter;

import java.util.Objects;
import java.util.UUID;

/**
 * Meter definition: what measurement an asset tracks (ASSET-21 §21.2, §21.6).
 *
 * <p>The meter owns exactly one canonical {@link MeterUnit} (§21.4) and a
 * {@link MeterBehavior} (§21.11-21.12). It never embeds the reading history.
 * Replacement/reset is handled by registering a new meter whose
 * {@code replacementOf} points at the retired meter (§21.13, §21.29).</p>
 */
public final class AssetMeter {

    private final AssetMeterId id;
    private final String tenantId;
    private final UUID assetId;
    private final MeterType type;
    private final MeterUnit unit;
    private final MeterBehavior behavior;
    private final String name;
    private final UUID replacementOf;
    private boolean active;

    private AssetMeter(AssetMeterId id, String tenantId, UUID assetId, MeterType type,
            MeterUnit unit, MeterBehavior behavior, String name, UUID replacementOf, boolean active) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.unit = Objects.requireNonNull(unit, "unit cannot be null");
        this.behavior = Objects.requireNonNull(behavior, "behavior cannot be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.name = name;
        this.replacementOf = replacementOf;
        this.active = active;
    }

    public static AssetMeter register(AssetMeterId id, String tenantId, UUID assetId, MeterType type,
            MeterUnit unit, MeterBehavior behavior, String name, UUID replacementOf) {
        return new AssetMeter(id, tenantId, assetId, type, unit, behavior, name, replacementOf, true);
    }

    public static AssetMeter of(AssetMeterId id, String tenantId, UUID assetId, MeterType type,
            MeterUnit unit, MeterBehavior behavior, String name, UUID replacementOf, boolean active) {
        return new AssetMeter(id, tenantId, assetId, type, unit, behavior, name, replacementOf, active);
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isMonotonic() {
        return behavior == MeterBehavior.MONOTONIC;
    }

    public AssetMeterId id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public UUID assetId() {
        return assetId;
    }

    public MeterType type() {
        return type;
    }

    public MeterUnit unit() {
        return unit;
    }

    public MeterBehavior behavior() {
        return behavior;
    }

    public String name() {
        return name;
    }

    public UUID replacementOf() {
        return replacementOf;
    }

    public boolean active() {
        return active;
    }
}
