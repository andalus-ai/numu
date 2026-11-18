package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationHostLifecycleDispatcherTest {
    private static final TenantId TENANT = TenantId.generate();
    private static final UUID INSTALLATION = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();
    private static final String APP = "com.example.inventory";
    private static final String VERSION = "1.2.3";
    private static final String DIGEST = "a".repeat(64);

    @Test
    void enabledTransitionActivatesOnlyAfterVerifiedPackageDigestMatches() {
        AtomicInteger activated = new AtomicInteger();
        ApplicationHostCompositionPort host = new ApplicationHostCompositionPort() {
            public java.util.concurrent.CompletionStage<Void> activate(ActivationCommand command) {
                assertThat(command.operationId()).isEqualTo(EVENT);
                assertThat(command.applicationPackage().sha256Digest()).isEqualTo(DIGEST);
                activated.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
            public java.util.concurrent.CompletionStage<Void> deactivate(DeactivationCommand command) {
                throw new AssertionError("must not deactivate");
            }
        };
        ApplicationHostLifecycleDispatcher dispatcher = new ApplicationHostLifecycleDispatcher(
                (id, version) -> CompletableFuture.completedFuture(Optional.of(
                        new ValidatedApplicationPackage(
                                new tech.kayys.numou.plugin.application.manifest.ApplicationManifest(
                                        APP, VERSION, "test-publisher", "Inventory", java.util.List.of(),
                                        java.util.List.of(), java.util.Set.of(), java.util.List.of()), DIGEST))),
                (tenant, pkg) -> CompletableFuture.completedFuture(null), host);

        assertThat(dispatcher.dispatch(TENANT, event("ENABLED")).toCompletableFuture().join())
                .isEqualTo(ApplicationHostLifecycleDispatcher.Outcome.ACTIVATED);
        assertThat(activated).hasValue(1);
        assertThat(activated.get()).isEqualTo(1);
    }


    @Test
    void incompatibleCapabilitiesPreventHostActivation() {
        AtomicInteger activated = new AtomicInteger();
        var dispatcher = new ApplicationHostLifecycleDispatcher(
                (id, version) -> CompletableFuture.completedFuture(Optional.of(
                        new ValidatedApplicationPackage(
                                new tech.kayys.numou.plugin.application.manifest.ApplicationManifest(
                                        APP, VERSION, "test-publisher", "Inventory", java.util.List.of(),
                                        java.util.List.of(), java.util.Set.of(), java.util.List.of()), DIGEST))),
                (tenant, pkg) -> CompletableFuture.failedFuture(new IllegalStateException("missing required capability")),
                new ApplicationHostCompositionPort() {
                    public java.util.concurrent.CompletionStage<Void> activate(ActivationCommand command) {
                        activated.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }
                    public java.util.concurrent.CompletionStage<Void> deactivate(DeactivationCommand command) {
                        return CompletableFuture.completedFuture(null);
                    }
                });

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                dispatcher.dispatch(TENANT, event("ENABLED")).toCompletableFuture().join())
                .hasRootCauseMessage("missing required capability");
        assertThat(activated.get()).isZero();
    }

    @Test
    void suspensionDeactivatesWithoutLoadingOrExecutingPackageCode() {
        AtomicInteger deactivated = new AtomicInteger();
        ApplicationHostCompositionPort host = new ApplicationHostCompositionPort() {
            public java.util.concurrent.CompletionStage<Void> activate(ActivationCommand command) {
                throw new AssertionError("must not activate");
            }
            public java.util.concurrent.CompletionStage<Void> deactivate(DeactivationCommand command) {
                assertThat(command.reason()).isEqualTo("SUSPENDED");
                assertThat(command.operationId()).isEqualTo(EVENT);
                deactivated.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };
        ApplicationHostLifecycleDispatcher dispatcher = new ApplicationHostLifecycleDispatcher(
                (id, version) -> CompletableFuture.failedFuture(new AssertionError("catalog must not be called")),
                (tenant, pkg) -> CompletableFuture.completedFuture(null), host);

        assertThat(dispatcher.dispatch(TENANT, event("SUSPENDED")).toCompletableFuture().join())
                .isEqualTo(ApplicationHostLifecycleDispatcher.Outcome.DEACTIVATED);
        assertThat(deactivated).hasValue(1);
        assertThat(deactivated.get()).isEqualTo(1);
    }

    @Test
    void unrelatedTransitionsAreIgnored() {
        ApplicationHostLifecycleDispatcher dispatcher = new ApplicationHostLifecycleDispatcher(
                (id, version) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, pkg) -> CompletableFuture.completedFuture(null),
                new ApplicationHostCompositionPort() {
                    public java.util.concurrent.CompletionStage<Void> activate(ActivationCommand command) {
                        throw new AssertionError();
                    }
                    public java.util.concurrent.CompletionStage<Void> deactivate(DeactivationCommand command) {
                        throw new AssertionError();
                    }
                });
        assertThat(dispatcher.dispatch(TENANT, event("INSTALLED")).toCompletableFuture().join())
                .isEqualTo(ApplicationHostLifecycleDispatcher.Outcome.IGNORED);
    }

    private static ClaimedEvent event(String status) {
        return new ClaimedEvent(EVENT, INSTALLATION, APP, VERSION, DIGEST,
                "APPLICATION_INSTALLATION_STATUS_CHANGED", status, 4, "operator:1", "corr-1",
                "", Instant.parse("2026-11-05T04:00:00Z"), 1, "worker", UUID.randomUUID(),
                Instant.parse("2026-11-05T04:01:00Z"));
    }
}
