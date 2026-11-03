package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.applicationplatform.domain.installation.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationInstallationOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final TenantId TENANT = TenantId.generate();
    private static final ApplicationId APP = new ApplicationId("com.example.retail");
    private static final PackageVersion VERSION = new PackageVersion("1.0.0");
    private static final ValidatedApplicationPackage PACKAGE = new ValidatedApplicationPackage(
            new ApplicationManifest(APP.value(), VERSION.value(), "com.example", "Retail",
                    List.of(), List.of(), Set.of(), List.of()), "a".repeat(64));

    @Test
    void retriesAfterSideEffectFailureAndResumesWithSameIdempotencyOperation() {
        var installation = ApplicationInstallation.request(TENANT, APP, VERSION, PACKAGE.sha256Digest(),
                "user:alice", NOW);
        var persisted = new FakeInstallationStore(installation);
        var outbox = new FakeOutbox(event(installation, 1));
        var provisionCalls = new AtomicInteger();
        var operationId = new AtomicReference<UUID>();
        ApplicationInstallationProvisioningPort provisioner = command -> {
            provisionCalls.incrementAndGet();
            if (operationId.get() == null) operationId.set(command.operationId());
            else assertThat(command.operationId()).isEqualTo(operationId.get());
            if (provisionCalls.get() == 1)
                return CompletableFuture.failedFuture(new ApplicationInstallationProvisioningException(
                        "PROVISIONING_TEMPORARY_FAILURE", true, "transient host failure"));
            return CompletableFuture.completedFuture(null);
        };

        var dispatcher = dispatcher(outbox, persisted, provisioner, Optional.of(PACKAGE));
        var first = dispatcher.dispatchOnce(TENANT, "worker-a").toCompletableFuture().join();
        assertThat(first.rescheduled()).isEqualTo(1);
        assertThat(persisted.current().status()).isEqualTo(ApplicationInstallationStatus.PROVISIONING);
        assertThat(outbox.delivered).isFalse();

        outbox.requeueWithAttempt(2);
        var second = dispatcher.dispatchOnce(TENANT, "worker-a").toCompletableFuture().join();
        assertThat(second.delivered()).isEqualTo(1);
        assertThat(provisionCalls).hasValue(2);
        assertThat(persisted.current().status()).isEqualTo(ApplicationInstallationStatus.INSTALLED);
        assertThat(outbox.delivered).isTrue();
        assertThat(outbox.retryCount).isEqualTo(1);
    }

    @Test
    void permanentPackageDigestMismatchDeadLettersAndFailsInstallation() {
        var installation = ApplicationInstallation.request(TENANT, APP, VERSION, PACKAGE.sha256Digest(),
                "user:alice", NOW);
        var persisted = new FakeInstallationStore(installation);
        var outbox = new FakeOutbox(event(installation, 1));
        var dispatcher = dispatcher(outbox, persisted, command -> CompletableFuture.completedFuture(null),
                Optional.of(new ValidatedApplicationPackage(PACKAGE.manifest(), "b".repeat(64))));

        var result = dispatcher.dispatchOnce(TENANT, "worker-a").toCompletableFuture().join();

        assertThat(result.deadLettered()).isEqualTo(1);
        assertThat(outbox.deadFailureCode).isEqualTo("PACKAGE_DIGEST_MISMATCH");
        assertThat(persisted.current().status()).isEqualTo(ApplicationInstallationStatus.FAILED);
        assertThat(persisted.current().failureCode()).contains("PACKAGE_DIGEST_MISMATCH");
        assertThat(outbox.delivered).isFalse();
    }

    @Test
    void nonProvisioningTransitionEventsAreAcknowledgedWithoutCallingProvisioner() {
        var installation = ApplicationInstallation.request(TENANT, APP, VERSION, PACKAGE.sha256Digest(),
                "user:alice", NOW);
        var outbox = new FakeOutbox(new ClaimedEvent(UUID.randomUUID(), installation.id().value(),
                APP.value(), VERSION.value(), PACKAGE.sha256Digest(), "APPLICATION_INSTALLATION_STATUS_CHANGED",
                "VALIDATING", 2, "system:application-platform", "corr-1", "", NOW, 1, "worker-a",
                UUID.randomUUID(), NOW.plusSeconds(30)));
        var provisionCalls = new AtomicInteger();
        var dispatcher = dispatcher(outbox, new FakeInstallationStore(installation), command -> {
            provisionCalls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }, Optional.of(PACKAGE));

        var result = dispatcher.dispatchOnce(TENANT, "worker-a").toCompletableFuture().join();

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(provisionCalls).hasValue(0);
        assertThat(outbox.delivered).isTrue();
    }

    private static ApplicationInstallationOutboxDispatcher dispatcher(FakeOutbox outbox,
            FakeInstallationStore installations, ApplicationInstallationProvisioningPort provisioner,
            Optional<ValidatedApplicationPackage> pkg) {
        return new ApplicationInstallationOutboxDispatcher(outbox, installations,
                (app, version) -> CompletableFuture.completedFuture(pkg),
                (tenant, applicationPackage) -> CompletableFuture.completedFuture(null),
                provisioner, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30),
                Duration.ofSeconds(5), Duration.ofMinutes(5), 10, 3);
    }

    private static ClaimedEvent event(ApplicationInstallation installation, int attempt) {
        return new ClaimedEvent(UUID.randomUUID(), installation.id().value(), APP.value(), VERSION.value(),
                PACKAGE.sha256Digest(), "APPLICATION_INSTALLATION_REQUESTED", "REQUESTED", 1,
                "user:alice", "corr-1", "INSTALL_REQUESTED", NOW, attempt, "worker-a",
                UUID.randomUUID(), NOW.plusSeconds(30));
    }

    private static ApplicationInstallation copy(ApplicationInstallation value) {
        return ApplicationInstallation.reconstitute(value.id(), value.tenantId(), value.applicationId(),
                value.packageVersion(), value.packageDigest(), value.status(), value.revision(),
                value.createdAt(), value.updatedAt(), value.createdBy(), value.updatedBy(),
                value.failureCode().orElse(null));
    }

    private static final class FakeInstallationStore implements ApplicationInstallationTransitionStore {
        private ApplicationInstallation current;
        private FakeInstallationStore(ApplicationInstallation initial) { current = copy(initial); }
        ApplicationInstallation current() { return copy(current); }

        @Override public CompletionStage<Optional<ApplicationInstallation>> find(TenantId tenantId,
                ApplicationInstallationId id) {
            if (!current.tenantId().equals(tenantId) || !current.id().equals(id))
                return CompletableFuture.completedFuture(Optional.empty());
            return CompletableFuture.completedFuture(Optional.of(copy(current)));
        }

        @Override public CompletionStage<Boolean> appendTransition(ApplicationInstallation changed,
                ApplicationInstallationTransition transition, long expectedRevision) {
            if (!current.tenantId().equals(changed.tenantId()) || !current.id().equals(changed.id())
                    || current.revision() != expectedRevision)
                return CompletableFuture.completedFuture(false);
            current = copy(changed);
            return CompletableFuture.completedFuture(true);
        }
    }

    private static final class FakeOutbox implements ApplicationInstallationOutboxStore {
        private ClaimedEvent event;
        private boolean delivered;
        private int retryCount;
        private String deadFailureCode;
        private FakeOutbox(ClaimedEvent event) { this.event = event; }

        void requeueWithAttempt(int attempt) {
            event = new ClaimedEvent(event.eventId(), event.installationId(), event.applicationId(),
                    event.packageVersion(), event.packageDigest(), event.eventType(), event.toStatus(),
                    event.revision(), event.actorId(), event.correlationId(), event.reasonCode(),
                    event.occurredAt(), attempt, event.workerId(), UUID.randomUUID(), NOW.plusSeconds(30));
        }

        @Override public CompletionStage<List<ClaimedEvent>> claimDue(TenantId tenantId, String workerId,
                Instant now, Duration lease, int limit) {
            if (delivered || event == null) return CompletableFuture.completedFuture(List.of());
            ClaimedEvent claimed = new ClaimedEvent(event.eventId(), event.installationId(), event.applicationId(),
                    event.packageVersion(), event.packageDigest(), event.eventType(), event.toStatus(),
                    event.revision(), event.actorId(), event.correlationId(), event.reasonCode(),
                    event.occurredAt(), event.attempts(), workerId, event.leaseToken(), now.plus(lease));
            return CompletableFuture.completedFuture(List.of(claimed));
        }

        @Override public CompletionStage<Boolean> markDelivered(TenantId tenantId, UUID eventId,
                String workerId, UUID leaseToken, Instant now) {
            if (!event.eventId().equals(eventId) || !event.leaseToken().equals(leaseToken))
                return CompletableFuture.completedFuture(false);
            delivered = true;
            return CompletableFuture.completedFuture(true);
        }

        @Override public CompletionStage<RetryOutcome> retry(TenantId tenantId, UUID eventId, String workerId,
                UUID leaseToken, String failureCode, Instant retryAt, Instant now, int maxAttempts) {
            retryCount++;
            if (event.attempts() >= maxAttempts) {
                deadFailureCode = failureCode;
                return CompletableFuture.completedFuture(RetryOutcome.DEAD_LETTERED);
            }
            return CompletableFuture.completedFuture(RetryOutcome.RESCHEDULED);
        }
    }
}
