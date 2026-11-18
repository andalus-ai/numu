package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationTransition;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped aggregate read and compare-and-set transition persistence boundary. */
public interface ApplicationInstallationTransitionStore {
    CompletionStage<Optional<ApplicationInstallation>> find(TenantId tenantId, ApplicationInstallationId id);
    CompletionStage<Boolean> appendTransition(ApplicationInstallation installation,
            ApplicationInstallationTransition transition, long expectedRevision);
}
