package tech.kayys.numou.documents.storage.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque tenant-scoped object key. The provider adapter is responsible for
 * mapping it to a physical key without allowing one tenant to address another.
 */
public final class StorageKey {
    private static final Pattern TENANT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private final String tenantId;
    private final String objectId;

    public StorageKey(String tenantId, String objectId) {
        this.tenantId = validateTenant(tenantId);
        this.objectId = validateObjectId(objectId);
    }

    public String tenantId() {
        return tenantId;
    }

    public String objectId() {
        return objectId;
    }

    private static String validateTenant(String value) {
        Objects.requireNonNull(value, "tenantId");
        if (!TENANT.matcher(value).matches() || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Invalid tenant identifier");
        }
        return value;
    }

    private static String validateObjectId(String value) {
        Objects.requireNonNull(value, "objectId");
        if (value.isBlank() || value.length() > 512 || value.startsWith("/")
                || value.startsWith("\\") || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid object identifier");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Object identifier contains an unsafe path segment");
            }
        }
        return value;
    }
}
