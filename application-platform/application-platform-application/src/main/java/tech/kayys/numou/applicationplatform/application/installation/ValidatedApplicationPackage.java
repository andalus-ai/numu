package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.applicationplatform.domain.installation.PackageVersion;
import java.util.Objects;

/** Immutable catalog result; only trusted catalog adapters may return packages after signature/integrity checks. */
public record ValidatedApplicationPackage(ApplicationId applicationId, PackageVersion version, String sha256Digest) {
    public ValidatedApplicationPackage {
        Objects.requireNonNull(applicationId); Objects.requireNonNull(version);
        if (sha256Digest == null || !sha256Digest.matches("^[a-f0-9]{64}$"))
            throw new IllegalArgumentException("Package digest must be lowercase SHA-256 hex");
    }
}
