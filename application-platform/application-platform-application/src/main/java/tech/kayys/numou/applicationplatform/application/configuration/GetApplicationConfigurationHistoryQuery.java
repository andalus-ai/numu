package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.Objects;

/**
 * Requests a bounded page of configuration revisions, newest first.
 * beforeRevision is an exclusive cursor; null starts at the current head.
 */
public record GetApplicationConfigurationHistoryQuery(
        ApplicationId applicationId,
        Long beforeRevision,
        int pageSize) {
    public GetApplicationConfigurationHistoryQuery {
        Objects.requireNonNull(applicationId, "applicationId");
        if (beforeRevision != null && beforeRevision < 1)
            throw new IllegalArgumentException("beforeRevision must be positive");
        if (pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
    }
}
