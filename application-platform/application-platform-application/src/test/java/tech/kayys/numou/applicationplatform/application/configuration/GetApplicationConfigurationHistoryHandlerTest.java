package tech.kayys.numou.applicationplatform.application.configuration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class GetApplicationConfigurationHistoryHandlerTest {
    private static final TenantId TENANT = TenantId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final ApplicationId APP = new ApplicationId("com.example.crm");

    @Test
    void authorizesBeforeReadingAndUsesTenantFromVerifiedActor() {
        var actor = new InstallationActor(TENANT, "user:alice", "corr-1");
        var authorized = new AtomicBoolean();
        var queriedTenant = new java.util.concurrent.atomic.AtomicReference<TenantId>();
        var store = new StubStore((tenant, app, cursor, size) -> {
            queriedTenant.set(tenant);
            return new ApplicationConfigurationStore.HistoryPage(List.of(), null);
        });
        var handler = new GetApplicationConfigurationHistoryHandler(
                () -> actor,
                (who, app) -> {
                    authorized.set(who.equals(actor) && app.equals(APP));
                    return CompletableFuture.completedFuture(null);
                },
                store);

        var result = handler.handle(new GetApplicationConfigurationHistoryQuery(APP, null, 25))
                .toCompletableFuture().join();

        assertThat(authorized).isTrue();
        assertThat(queriedTenant.get()).isEqualTo(TENANT);
        assertThat(result.revisions()).isEmpty();
    }

    @Test
    void deniedReadNeverTouchesTheStore() {
        var read = new AtomicBoolean();
        var handler = new GetApplicationConfigurationHistoryHandler(
                () -> new InstallationActor(TENANT, "user:bob", ""),
                (actor, app) -> CompletableFuture.failedFuture(new SecurityException("denied")),
                new StubStore((tenant, app, cursor, size) -> {
                    read.set(true);
                    return new ApplicationConfigurationStore.HistoryPage(List.of(), null);
                }));

        assertThatThrownBy(() -> handler.handle(new GetApplicationConfigurationHistoryQuery(APP, null, 10))
                .toCompletableFuture().join()).hasRootCauseInstanceOf(SecurityException.class);
        assertThat(read).isFalse();
    }

    @Test
    void queryRejectsUnboundedOrInvalidCursors() {
        assertThatThrownBy(() -> new GetApplicationConfigurationHistoryQuery(APP, 0L, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GetApplicationConfigurationHistoryQuery(APP, null, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @FunctionalInterface
    private interface HistoryReader {
        ApplicationConfigurationStore.HistoryPage read(TenantId tenant, ApplicationId app, Long cursor, int size);
    }

    private static final class StubStore implements ApplicationConfigurationStore {
        private final HistoryReader reader;
        private StubStore(HistoryReader reader) { this.reader = reader; }

        @Override
        public java.util.concurrent.CompletionStage<Optional<ApplicationConfigurationSnapshot>> findCurrent(
                TenantId tenantId, String applicationId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletionStage<HistoryPage> findHistory(
                TenantId tenantId, ApplicationId applicationId, Long beforeRevision, int pageSize) {
            return CompletableFuture.completedFuture(reader.read(tenantId, applicationId, beforeRevision, pageSize));
        }

        @Override
        public java.util.concurrent.CompletionStage<SaveResult> save(
                ApplicationConfigurationSnapshot snapshot, long expectedRevision) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }
}
