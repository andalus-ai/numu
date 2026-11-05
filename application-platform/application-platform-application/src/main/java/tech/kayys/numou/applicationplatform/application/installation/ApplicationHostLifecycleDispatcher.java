package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Applies committed installation lifecycle transitions to the host composition.
 * Outbox delivery is at-least-once; the event ID is therefore the idempotency key
 * passed to the host composition adapter.
 */
public final class ApplicationHostLifecycleDispatcher {
    private static final String ENABLED = "ENABLED";
    private static final String FAILED = "FAILED";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String UNINSTALLING = "UNINSTALLING";
    private static final String UNINSTALLED = "UNINSTALLED";

    private final ApplicationPackageCatalogPort catalog;
    private final ApplicationHostCompositionPort host;

    public ApplicationHostLifecycleDispatcher(ApplicationPackageCatalogPort catalog,
            ApplicationHostCompositionPort host) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.host = Objects.requireNonNull(host, "host");
    }

    public CompletionStage<Outcome> dispatch(TenantId tenantId, ClaimedEvent event) {
        if (tenantId == null || event == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenant and event are required"));
        if (ENABLED.equals(event.toStatus())) return activate(tenantId, event);
        if (SUSPENDED.equals(event.toStatus()) || UNINSTALLING.equals(event.toStatus())
                || UNINSTALLED.equals(event.toStatus()) || FAILED.equals(event.toStatus()))
            return deactivate(tenantId, event);
        return CompletableFuture.completedFuture(Outcome.IGNORED);
    }

    private CompletionStage<Outcome> activate(TenantId tenantId, ClaimedEvent event) {
        return catalog.findValidatedPackage(
                new tech.kayys.numou.applicationplatform.domain.installation.ApplicationId(event.applicationId()),
                new tech.kayys.numou.applicationplatform.domain.installation.PackageVersion(event.packageVersion()))
                .thenCompose(found -> {
                    ValidatedApplicationPackage pkg = found.orElseThrow(() ->
                            new IllegalStateException("Enabled installation has no verified package"));
                    if (!pkg.sha256Digest().equals(event.packageDigest()))
                        return CompletableFuture.failedFuture(new SecurityException("Activation package digest mismatch"));
                    var command = new ApplicationHostCompositionPort.ActivationCommand(
                            tenantId, event.installationId(), event.applicationId(), event.packageVersion(),
                            event.packageDigest(), event.eventId(), event.actorId(), event.correlationId(), pkg);
                    return host.activate(command).thenApply(ignored -> Outcome.ACTIVATED);
                });
    }

    private CompletionStage<Outcome> deactivate(TenantId tenantId, ClaimedEvent event) {
        var command = new ApplicationHostCompositionPort.DeactivationCommand(
                tenantId, event.installationId(), event.applicationId(), event.packageVersion(),
                event.packageDigest(), event.eventId(), event.actorId(), event.correlationId(), event.toStatus());
        return host.deactivate(command).thenApply(ignored -> Outcome.DEACTIVATED);
    }

    public enum Outcome { ACTIVATED, DEACTIVATED, IGNORED }
}
