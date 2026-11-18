package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.domain.installation.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class RequestApplicationInstallationHandlerTest {
    private static final ApplicationId APP = new ApplicationId("com.example.retail");
    private static final PackageVersion VERSION = new PackageVersion("1.0.0");
    private static final TenantId TENANT = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final String KEY = "request-idempotency-key-123";
    private static final ValidatedApplicationPackage PACKAGE = new ValidatedApplicationPackage(
            new ApplicationManifest(APP.value(), VERSION.value(), "com.example", "Retail",
                    java.util.List.of(), java.util.List.of(), java.util.Set.of(), java.util.List.of()), "b".repeat(64));

    @Test void createsTenantScopedRequestUsingTrustedActorAndHashedIdempotencyKey() {
        var actor = new InstallationActor(TENANT, "user:alice", "corr-1");
        var storedKey = new AtomicReference<String>();
        var storedEvent = new AtomicReference<ApplicationInstallationTransition>();
        var stored = new AtomicReference<ApplicationInstallation>();
        var authorized = new AtomicBoolean();
        var compatible = new AtomicBoolean();
        ApplicationInstallationStore store = (installation, keyHash, event) -> {
            stored.set(installation); storedKey.set(keyHash); storedEvent.set(event);
            return CompletableFuture.completedFuture(new ApplicationInstallationStore.CreateResult(
                    ApplicationInstallationStore.CreateResult.Outcome.CREATED, Optional.of(installation)));
        };
        var handler = new RequestApplicationInstallationHandler(
                () -> actor,
                (who, app) -> { authorized.set(who.equals(actor) && app.equals(APP)); return CompletableFuture.completedFuture(null); },
                (app, version) -> CompletableFuture.completedFuture(Optional.of(PACKAGE)),
                (tenant, pkg) -> { compatible.set(tenant.equals(TENANT) && pkg.equals(PACKAGE)); return CompletableFuture.completedFuture(null); },
                store,
                Clock.fixed(Instant.parse("2026-11-03T02:00:00Z"), ZoneOffset.UTC));

        var result = handler.handle(new RequestApplicationInstallationCommand(APP, VERSION, KEY))
                .toCompletableFuture().join();
        assertThat(result.outcome()).isEqualTo(RequestApplicationInstallationResult.Outcome.CREATED);
        assertThat(result.installation().tenantId()).isEqualTo(TENANT);
        assertThat(result.installation().status()).isEqualTo(ApplicationInstallationStatus.REQUESTED);
        assertThat(authorized).isTrue();
        assertThat(compatible).isTrue();
        assertThat(storedKey.get()).matches("[a-f0-9]{64}").isNotEqualTo(KEY);
        assertThat(storedEvent.get().toStatus()).isEqualTo(ApplicationInstallationStatus.REQUESTED);
        assertThat(storedEvent.get().correlationId()).isEqualTo("corr-1");
        assertThat(storedEvent.get().occurredAt()).isEqualTo(stored.get().createdAt());
    }

    @Test void missingPackageStopsBeforeCompatibilityAndPersistence() {
        var called = new AtomicBoolean();
        var handler = new RequestApplicationInstallationHandler(
                () -> new InstallationActor(TENANT, "user:alice", "corr"),
                (actor, app) -> CompletableFuture.completedFuture(null),
                (app, version) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, pkg) -> { called.set(true); return CompletableFuture.completedFuture(null); },
                (installation, key, event) -> { called.set(true); return CompletableFuture.failedFuture(new AssertionError("must not persist")); },
                Clock.systemUTC());
        assertThatThrownBy(() -> handler.handle(new RequestApplicationInstallationCommand(APP, VERSION, KEY))
                .toCompletableFuture().join()).hasRootCauseInstanceOf(ApplicationPackageNotFoundException.class);
        assertThat(called).isFalse();
    }

    @Test void compatibilityFailureStopsBeforeInstallationCreation() {
        var persisted = new AtomicBoolean();
        var handler = new RequestApplicationInstallationHandler(
                () -> new InstallationActor(TENANT, "user:alice", "corr"),
                (actor, app) -> CompletableFuture.completedFuture(null),
                (app, version) -> CompletableFuture.completedFuture(Optional.of(PACKAGE)),
                (tenant, pkg) -> CompletableFuture.failedFuture(new IllegalStateException("incompatible")),
                (installation, key, event) -> { persisted.set(true); return CompletableFuture.failedFuture(new AssertionError("must not persist")); },
                Clock.systemUTC());
        assertThatThrownBy(() -> handler.handle(new RequestApplicationInstallationCommand(APP, VERSION, KEY))
                .toCompletableFuture().join()).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(persisted).isFalse();
    }

    @Test void idempotentReplayReturnsTheStoreResult() {
        var existing = ApplicationInstallation.request(TENANT, APP, VERSION, "c".repeat(64), "user:alice", Instant.now());
        var handler = new RequestApplicationInstallationHandler(
                () -> new InstallationActor(TENANT, "user:alice", "corr"),
                (actor, app) -> CompletableFuture.completedFuture(null),
                (app, version) -> CompletableFuture.completedFuture(Optional.of(PACKAGE)),
                (tenant, pkg) -> CompletableFuture.completedFuture(null),
                (installation, key, event) -> CompletableFuture.completedFuture(new ApplicationInstallationStore.CreateResult(
                        ApplicationInstallationStore.CreateResult.Outcome.REPLAYED, Optional.of(existing))),
                Clock.systemUTC());
        var result = handler.handle(new RequestApplicationInstallationCommand(APP, VERSION, KEY))
                .toCompletableFuture().join();
        assertThat(result.outcome()).isEqualTo(RequestApplicationInstallationResult.Outcome.REPLAYED);
        assertThat(result.installation()).isSameAs(existing);
    }
}
