package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.Objects;

/** Reads the current tenant-scoped configuration for one installed application. */
public record GetApplicationConfigurationQuery(ApplicationId applicationId) {
    public GetApplicationConfigurationQuery {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
