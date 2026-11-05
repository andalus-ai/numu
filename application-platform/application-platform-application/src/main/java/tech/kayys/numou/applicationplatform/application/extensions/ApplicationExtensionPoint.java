package tech.kayys.numou.applicationplatform.application.extensions;

import tech.kayys.numou.plugin.application.manifest.SemanticVersion;

import java.util.Objects;

/** Host-owned, versioned contract that applications may contribute to. */
public record ApplicationExtensionPoint(String id, String contractVersion, String schemaRef) {
    public ApplicationExtensionPoint {
        Objects.requireNonNull(id, "id");
        if (!id.matches("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$"))
            throw new IllegalArgumentException("Extension point ID must be reverse-DNS style");
        SemanticVersion.parse(contractVersion);
        Objects.requireNonNull(schemaRef, "schemaRef");
        if (schemaRef.isBlank() || schemaRef.length() > 500)
            throw new IllegalArgumentException("schemaRef must contain 1 to 500 characters");
    }
}
