package tech.kayys.numou.asset.interfaces.rest;

/**
 * Stable API version constants (ASSET-28 §4).
 *
 * <p>Asset exposes a single stable major version under {@code /api/v1}. Breaking
 * changes require a new major version; additive changes stay within the current
 * one (§45 compatibility rules).</p>
 */
public final class ApiVersion {

    public static final String V1 = "/api/v1";

    public static final String CURRENT = V1;

    private ApiVersion() {
    }
}