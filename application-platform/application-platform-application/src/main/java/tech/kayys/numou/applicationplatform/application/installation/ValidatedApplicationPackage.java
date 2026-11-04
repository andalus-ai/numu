package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.applicationplatform.domain.installation.PackageVersion;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import java.util.Objects;

/**
 * Immutable catalog result. The manifest has already passed M3 schema and semantic
 * validation; catalog adapters must additionally verify publisher trust and package integrity.
 */
public record ValidatedApplicationPackage(ApplicationManifest manifest, String sha256Digest) {
    public ValidatedApplicationPackage {
        Objects.requireNonNull(manifest, "validated manifest");
        if (sha256Digest == null || !sha256Digest.matches("^[a-f0-9]{64}$"))
            throw new IllegalArgumentException("Package digest must be lowercase SHA-256 hex");
    }
    public ApplicationId applicationId() { return new ApplicationId(manifest.id()); }
    public PackageVersion version() { return new PackageVersion(manifest.version()); }
}
