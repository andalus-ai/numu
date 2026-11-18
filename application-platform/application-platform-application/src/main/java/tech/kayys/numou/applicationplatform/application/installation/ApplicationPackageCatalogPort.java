package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.applicationplatform.domain.installation.PackageVersion;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Returns only publisher-verified, integrity-checked, manifest-validated packages. */
@FunctionalInterface
public interface ApplicationPackageCatalogPort {
    CompletionStage<Optional<ValidatedApplicationPackage>> findValidatedPackage(
            ApplicationId applicationId, PackageVersion version);
}
