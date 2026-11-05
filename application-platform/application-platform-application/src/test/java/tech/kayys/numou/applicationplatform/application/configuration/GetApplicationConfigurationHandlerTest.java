package tech.kayys.numou.applicationplatform.application.configuration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class GetApplicationConfigurationHandlerTest {
    @Test
    void authorizesBeforeReadingCurrentTenantConfiguration() {
        TenantId tenant = TenantId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        ApplicationId app = new ApplicationId("com.example.workspace");
        var actor = new InstallationActor(tenant, "user:alice", "corr");
        var snapshot = ApplicationConfigurationSnapshot.first(tenant, app, "1.0",
                "{\"theme\":\"dark\"}", "user:alice", Instant.parse("2026-11-04T00:00:00Z"));
        var authorized = new AtomicBoolean();
        var read = new AtomicBoolean();
        ApplicationConfigurationStore store = new ApplicationConfigurationStore() {
            @Override
            public java.util.concurrent.CompletionStage<Optional<ApplicationConfigurationSnapshot>> findCurrent(
                    TenantId tenantId, String applicationId) {
                read.set(true);
                assertThat(tenantId).isEqualTo(tenant);
                assertThat(applicationId).isEqualTo(app.value());
                return CompletableFuture.completedFuture(Optional.of(snapshot));
            }
            @Override
            public java.util.concurrent.CompletionStage<HistoryPage> findHistory(
                    TenantId tenantId, ApplicationId applicationId, Long cursor, int size) {
                return CompletableFuture.completedFuture(new HistoryPage(java.util.List.of(), null));
            }
            @Override
            public java.util.concurrent.CompletionStage<SaveResult> save(
                    ApplicationConfigurationSnapshot value, long expectedRevision) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        var handler = new GetApplicationConfigurationHandler(
                () -> actor,
                (who, requestedApp) -> {
                    authorized.set(who.equals(actor) && requestedApp.equals(app));
                    return CompletableFuture.completedFuture(null);
                },
                store);

        assertThat(handler.handle(new GetApplicationConfigurationQuery(app)).toCompletableFuture().join())
                .contains(snapshot);
        assertThat(authorized).isTrue();
        assertThat(read).isTrue();
    }

    @Test
    void deniedReadDoesNotQueryConfigurationStore() {
        TenantId tenant = TenantId.generate();
        ApplicationId app = new ApplicationId("com.example.private");
        var read = new AtomicBoolean();
        ApplicationConfigurationStore store = new ApplicationConfigurationStore() {
            @Override
            public java.util.concurrent.CompletionStage<Optional<ApplicationConfigurationSnapshot>> findCurrent(
                    TenantId tenantId, String applicationId) {
                read.set(true);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            @Override
            public java.util.concurrent.CompletionStage<HistoryPage> findHistory(
                    TenantId tenantId, ApplicationId applicationId, Long cursor, int size) {
                return CompletableFuture.completedFuture(new HistoryPage(java.util.List.of(), null));
            }
            @Override
            public java.util.concurrent.CompletionStage<SaveResult> save(
                    ApplicationConfigurationSnapshot value, long expectedRevision) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        var handler = new GetApplicationConfigurationHandler(
                () -> new InstallationActor(tenant, "user:bob", ""),
                (actor, requestedApp) -> CompletableFuture.failedFuture(new SecurityException("denied")),
                store);

        assertThatThrownBy(() -> handler.handle(new GetApplicationConfigurationQuery(app))
                .toCompletableFuture().join()).hasRootCauseInstanceOf(SecurityException.class);
        assertThat(read).isFalse();
    }
}
