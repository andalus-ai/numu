package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped read port for installations eligible to contribute to the host runtime. */
@FunctionalInterface
public interface ApplicationInstallationQueryPort {
    /** Returns ENABLED installations only, in deterministic application-ID order. */
    CompletionStage<List<ApplicationInstallation>> findEnabled(TenantId tenantId);
}
