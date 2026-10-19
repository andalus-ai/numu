package tech.kayys.numou.cache;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped cache key.
 *
 * <p>A cross-tenant cache hit is a data breach, not a bug, so the tenant is part
 * of the key's identity and cannot be omitted by accident: callers either pass a
 * tenant or explicitly ask for {@link #platform(String, String)}.
 *
 * @param namespace logical cache name, e.g. {@code menu.by-venue}
 * @param tenantId  owning tenant; {@code null} only for platform-wide data
 * @param key       caller-defined key within the namespace
 */
public record CacheKey(String namespace, UUID tenantId, String key) {

    public static final String PLATFORM = "platform";

    public CacheKey {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        if (namespace.isBlank() || namespace.indexOf('|') >= 0) {
            throw new IllegalArgumentException("namespace must be non-blank and must not contain '|'");
        }
    }

    public static CacheKey of(String namespace, UUID tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null (use platform() for global data)");
        return new CacheKey(namespace, tenantId, key);
    }

    public static CacheKey platform(String namespace, String key) {
        return new CacheKey(namespace, null, key);
    }

    /** Stable storage key: {@code namespace|tenant|key}. */
    public String asString() {
        return prefix(namespace, tenantId) + key;
    }

    /** Prefix shared by every key of one tenant inside one namespace. */
    public static String prefix(String namespace, UUID tenantId) {
        return namespace + "|" + (tenantId == null ? PLATFORM : tenantId.toString()) + "|";
    }
}
