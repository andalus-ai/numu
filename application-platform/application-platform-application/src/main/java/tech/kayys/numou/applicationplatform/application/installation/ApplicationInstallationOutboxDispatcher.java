package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.RetryOutcome;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationStatus;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * One bounded tenant-scoped provisioning pass. A retried event resumes from durable
 * installation state and calls the host provisioner with the same operation ID.
 */
public final class ApplicationInstallationOutboxDispatcher {
    private static final String SYSTEM_ACTOR = "system:application-platform";
    private static final String REQUESTED_EVENT = "APPLICATION_INSTALLATION_REQUESTED";

    private final ApplicationInstallationOutboxStore outbox;
    private final ApplicationInstallationTransitionStore installations;
    private final ApplicationPackageCatalogPort catalog;
    private final ApplicationCapabilityCompatibilityPort compatibility;
    private final ApplicationInstallationProvisioningPort provisioner;
    private final Clock clock;
    private final Duration lease;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int batchSize;
    private final int maxAttempts;

    public ApplicationInstallationOutboxDispatcher(ApplicationInstallationOutboxStore outbox,
            ApplicationInstallationTransitionStore installations, ApplicationPackageCatalogPort catalog,
            ApplicationCapabilityCompatibilityPort compatibility, ApplicationInstallationProvisioningPort provisioner,
            Clock clock, Duration lease, Duration initialBackoff, Duration maxBackoff,
            int batchSize, int maxAttempts) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.installations = Objects.requireNonNull(installations, "installations");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.compatibility = Objects.requireNonNull(compatibility, "compatibility");
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lease = positive(lease, "lease");
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0)
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        if (batchSize < 1 || batchSize > 100 || maxAttempts < 1 || maxAttempts > 100)
            throw new IllegalArgumentException("batchSize and maxAttempts must be between 1 and 100");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    public CompletionStage<DispatchResult> dispatchOnce(TenantId tenantId, String workerId) {
        if (tenantId == null || workerId == null || workerId.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and workerId are required"));
        Instant now = clock.instant();
        return outbox.claimDue(tenantId, workerId, now, lease, batchSize)
                .thenCompose(events -> dispatch(events, 0, tenantId, workerId, new DispatchResult(events.size(), 0, 0, 0)));
    }

    private CompletionStage<DispatchResult> dispatch(List<ClaimedEvent> events, int index,
            TenantId tenantId, String workerId, DispatchResult result) {
        if (index >= events.size()) return CompletableFuture.completedFuture(result);
        ClaimedEvent event = events.get(index);
        return process(event, tenantId, workerId).handle((outcome, failure) -> {
            if (failure != null) return new EventOutcome(false, false, true);
            return outcome;
        }).thenCompose(outcome -> dispatch(events, index + 1, tenantId, workerId, result.add(outcome)));
    }

    private CompletionStage<EventOutcome> process(ClaimedEvent event, TenantId tenantId, String workerId) {
        if (!REQUESTED_EVENT.equals(event.eventType())) return acknowledge(event, tenantId, workerId);
        if (!"REQUESTED".equals(event.toStatus()) || event.revision() != 1)
            return handleFailure(event, tenantId, workerId, new ApplicationInstallationProvisioningException(
                    "INSTALLATION_EVENT_INVALID", false, "Initial installation event has an invalid state or revision"));
        return ensureProvisioned(event, tenantId).thenCompose(ignored -> acknowledge(event, tenantId, workerId))
                .exceptionallyCompose(failure -> handleFailure(event, tenantId, workerId, unwrap(failure)));
    }

    private CompletionStage<Void> ensureProvisioned(ClaimedEvent event, TenantId tenantId) {
        return load(event, tenantId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.failedFuture(
                    new ApplicationInstallationProvisioningException("INSTALLATION_NOT_FOUND", false,
                            "Committed installation could not be found"));
            ApplicationInstallation current = found.get();
            if (!matches(event, current)) return CompletableFuture.failedFuture(
                    new ApplicationInstallationProvisioningException("INSTALLATION_EVENT_MISMATCH", false,
                            "Outbox event does not match the committed installation"));
            if (current.status() == ApplicationInstallationStatus.INSTALLED
                    || current.status() == ApplicationInstallationStatus.ENABLED
                    || current.status() == ApplicationInstallationStatus.SUSPENDED
                    || current.status() == ApplicationInstallationStatus.UNINSTALLING
                    || current.status() == ApplicationInstallationStatus.UNINSTALLED
                    || current.status() == ApplicationInstallationStatus.FAILED)
                return CompletableFuture.completedFuture(null);
            return moveToValidation(current, event).thenCompose(validating ->
                    catalog.findValidatedPackage(current.applicationId(), current.packageVersion())
                            .thenCompose(pkg -> {
                                if (pkg.isEmpty()) return CompletableFuture.failedFuture(
                                        new ApplicationInstallationProvisioningException("PACKAGE_NOT_FOUND", false,
                                                "Validated application package is no longer available"));
                                ValidatedApplicationPackage applicationPackage = pkg.get();
                                if (!applicationPackage.applicationId().equals(current.applicationId())
                                        || !applicationPackage.version().equals(current.packageVersion())
                                        || !applicationPackage.sha256Digest().equals(current.packageDigest()))
                                    return CompletableFuture.failedFuture(
                                            new ApplicationInstallationProvisioningException("PACKAGE_DIGEST_MISMATCH", false,
                                                    "Catalog package does not match the committed package digest"));
                                return compatibility.assertCompatible(tenantId, applicationPackage)
                                        .thenCompose(ignored -> moveToProvisioning(validating, event))
                                        .thenCompose(provisioning -> provisioner.ensureProvisioned(
                                                new ApplicationInstallationProvisioningPort.ProvisioningCommand(
                                                        tenantId, provisioning.id().value(),
                                                        provisioning.applicationId().value(),
                                                        provisioning.packageVersion().value(),
                                                        provisioning.packageDigest(), event.eventId(),
                                                        event.actorId(), event.correlationId(), applicationPackage)))
                                        .thenCompose(ignored -> completeInstallation(event, tenantId));
                            }));
        });
    }

    private CompletionStage<ApplicationInstallation> moveToValidation(ApplicationInstallation current, ClaimedEvent event) {
        if (current.status() == ApplicationInstallationStatus.VALIDATING
                || current.status() == ApplicationInstallationStatus.PROVISIONING)
            return CompletableFuture.completedFuture(current);
        if (current.status() != ApplicationInstallationStatus.REQUESTED)
            return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                    "INSTALLATION_STATE_UNSUPPORTED", false, "Installation is not eligible for provisioning"));
        long expectedRevision = current.revision();
        var transition = current.beginValidation(SYSTEM_ACTOR, event.correlationId(), clock.instant());
        return installations.appendTransition(current, transition, expectedRevision)
                .thenCompose(saved -> {
                    if (saved) return CompletableFuture.completedFuture(current);
                    return load(event, current.tenantId()).thenCompose(found -> {
                        if (found.isEmpty()) return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                "INSTALLATION_NOT_FOUND", false, "Installation disappeared during validation"));
                        ApplicationInstallation latest = found.get();
                        if (latest.status() == ApplicationInstallationStatus.VALIDATING
                                || latest.status() == ApplicationInstallationStatus.PROVISIONING)
                            return CompletableFuture.completedFuture(latest);
                        return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                "INSTALLATION_CONCURRENT_MODIFICATION", true, "Installation changed during validation"));
                    });
                });
    }

    private CompletionStage<ApplicationInstallation> moveToProvisioning(ApplicationInstallation validating, ClaimedEvent event) {
        if (validating.status() == ApplicationInstallationStatus.PROVISIONING)
            return CompletableFuture.completedFuture(validating);
        if (validating.status() != ApplicationInstallationStatus.VALIDATING)
            return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                    "INSTALLATION_STATE_UNSUPPORTED", false, "Installation is not ready for provisioning"));
        long expectedRevision = validating.revision();
        var transition = validating.beginProvisioning(SYSTEM_ACTOR, event.correlationId(), clock.instant());
        return installations.appendTransition(validating, transition, expectedRevision)
                .thenCompose(saved -> {
                    if (saved) return CompletableFuture.completedFuture(validating);
                    return load(event, validating.tenantId()).thenCompose(found -> {
                        if (found.isEmpty()) return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                "INSTALLATION_NOT_FOUND", false, "Installation disappeared during provisioning"));
                        if (found.get().status() == ApplicationInstallationStatus.PROVISIONING)
                            return CompletableFuture.completedFuture(found.get());
                        return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                "INSTALLATION_CONCURRENT_MODIFICATION", true, "Installation changed during provisioning"));
                    });
                });
    }

    private CompletionStage<Void> completeInstallation(ClaimedEvent event, TenantId tenantId) {
        return load(event, tenantId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                    "INSTALLATION_NOT_FOUND", false, "Installation disappeared after provisioning"));
            ApplicationInstallation current = found.get();
            if (current.status() == ApplicationInstallationStatus.INSTALLED) return CompletableFuture.completedFuture(null);
            if (current.status() != ApplicationInstallationStatus.PROVISIONING)
                return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                        "INSTALLATION_STATE_CHANGED", true, "Installation state changed before completion"));
            long expectedRevision = current.revision();
            var transition = current.completeProvisioning(SYSTEM_ACTOR, event.correlationId(), clock.instant());
            return installations.appendTransition(current, transition, expectedRevision).thenCompose(saved -> {
                if (saved) return CompletableFuture.completedFuture(null);
                return load(event, tenantId).thenCompose(latest -> latest.isPresent()
                        && latest.get().status() == ApplicationInstallationStatus.INSTALLED
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                "INSTALLATION_CONCURRENT_MODIFICATION", true,
                                "Installation changed before completion")));
            });
        });
    }

    private CompletionStage<Void> markFailed(ClaimedEvent event, TenantId tenantId, String failureCode) {
        return load(event, tenantId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture(null);
            ApplicationInstallation current = found.get();
            if (current.status() == ApplicationInstallationStatus.FAILED
                    || current.status() == ApplicationInstallationStatus.INSTALLED
                    || current.status() == ApplicationInstallationStatus.ENABLED
                    || current.status() == ApplicationInstallationStatus.SUSPENDED
                    || current.status() == ApplicationInstallationStatus.UNINSTALLING
                    || current.status() == ApplicationInstallationStatus.UNINSTALLED)
                return CompletableFuture.completedFuture(null);
            try {
                long expectedRevision = current.revision();
                var transition = current.fail(SYSTEM_ACTOR, event.correlationId(), clock.instant(), failureCode);
                return installations.appendTransition(current, transition, expectedRevision).thenCompose(saved -> {
                    if (saved) return CompletableFuture.completedFuture(null);
                    return load(event, tenantId).thenCompose(latest -> latest.isPresent()
                            && latest.get().status() == ApplicationInstallationStatus.FAILED
                            ? CompletableFuture.completedFuture(null)
                            : CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                                    "INSTALLATION_CONCURRENT_MODIFICATION", true,
                                    "Installation changed while recording provisioning failure")));
                });
            } catch (IllegalStateException invalidTransition) {
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    private CompletionStage<EventOutcome> handleFailure(ClaimedEvent event, TenantId tenantId,
            String workerId, Throwable failure) {
        ApplicationInstallationProvisioningException typed = failure instanceof ApplicationInstallationProvisioningException value
                ? value : new ApplicationInstallationProvisioningException(
                        "PROVISIONING_TEMPORARY_FAILURE", true, "Provisioning dependency failed", failure);
        int attempts = typed.retryable() ? maxAttempts : 1;
        Instant now = clock.instant();
        return outbox.retry(tenantId, event.eventId(), workerId, event.leaseToken(), typed.failureCode(),
                now.plus(backoff(event.attempts())), now, attempts).thenCompose(outcome -> {
            if (outcome == RetryOutcome.DEAD_LETTERED)
                return markFailed(event, tenantId, typed.failureCode()).thenApply(ignored -> new EventOutcome(false, true, false));
            if (outcome == RetryOutcome.RESCHEDULED) return CompletableFuture.completedFuture(new EventOutcome(false, false, true));
            return CompletableFuture.completedFuture(new EventOutcome(false, false, false));
        });
    }

    private CompletionStage<EventOutcome> acknowledge(ClaimedEvent event, TenantId tenantId, String workerId) {
        return outbox.markDelivered(tenantId, event.eventId(), workerId, event.leaseToken(), clock.instant())
                .thenApply(delivered -> new EventOutcome(delivered, false, false));
    }

    private CompletionStage<Optional<ApplicationInstallation>> load(ClaimedEvent event, TenantId tenantId) {
        return installations.find(tenantId, new ApplicationInstallationId(event.installationId()));
    }

    private static boolean matches(ClaimedEvent event, ApplicationInstallation installation) {
        return event.installationId().equals(installation.id().value())
                && event.applicationId().equals(installation.applicationId().value())
                && event.packageVersion().equals(installation.packageVersion().value())
                && event.packageDigest().equals(installation.packageDigest());
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempt - 1));
        try {
            Duration candidate = initialBackoff.multipliedBy(multiplier);
            return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
        } catch (ArithmeticException overflow) {
            return maxBackoff;
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    public record DispatchResult(int claimed, int delivered, int deadLettered, int rescheduled) {
        DispatchResult add(EventOutcome outcome) {
            return new DispatchResult(claimed, delivered + (outcome.delivered ? 1 : 0),
                    deadLettered + (outcome.deadLettered ? 1 : 0),
                    rescheduled + (outcome.rescheduled ? 1 : 0));
        }
    }

    private record EventOutcome(boolean delivered, boolean deadLettered, boolean rescheduled) {}
}
