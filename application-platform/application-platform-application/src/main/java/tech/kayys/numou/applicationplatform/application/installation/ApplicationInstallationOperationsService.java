package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOperationsPort.*;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Application-layer facade for operator actions. The host supplies authorization
 * from its authenticated principal; callers cannot grant themselves tenant scope.
 */
public final class ApplicationInstallationOperationsService {
    private final ApplicationInstallationOperationsPort operations;
    private final ApplicationInstallationOperatorAuthorizationPort authorization;

    public ApplicationInstallationOperationsService(ApplicationInstallationOperationsPort operations,
            ApplicationInstallationOperatorAuthorizationPort authorization) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public CompletionStage<List<TransitionEntry>> history(TenantId tenantId, String actorId,
            ApplicationInstallationId installationId, int limit) {
        requireActor(actorId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return authorization.assertCanInspect(tenantId, actorId)
                .thenCompose(ignored -> operations.history(tenantId, installationId, limit));
    }

    public CompletionStage<List<DeadLetterEntry>> deadLetters(TenantId tenantId, String actorId, int limit) {
        requireActor(actorId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return authorization.assertCanInspect(tenantId, actorId)
                .thenCompose(ignored -> operations.deadLetters(tenantId, limit));
    }

    public CompletionStage<List<RedriveAuditEntry>> redriveHistory(TenantId tenantId, String actorId,
            ApplicationInstallationId installationId, int limit) {
        requireActor(actorId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return authorization.assertCanInspect(tenantId, actorId)
                .thenCompose(ignored -> operations.redriveHistory(tenantId, installationId, limit));
    }

    public CompletionStage<RedriveResult> redrive(RedriveCommand command) {
        Objects.requireNonNull(command, "command");
        return authorization.assertCanRedrive(command.tenantId(), command.actorId())
                .thenCompose(ignored -> operations.redrive(command));
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank() || actorId.length() > 200)
            throw new IllegalArgumentException("authenticated actor is required");
    }
}
