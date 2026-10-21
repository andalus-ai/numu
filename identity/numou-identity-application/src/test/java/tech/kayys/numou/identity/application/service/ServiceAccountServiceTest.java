package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.CreateServiceAccountRequest;
import tech.kayys.numou.identity.application.dto.ServiceAccountResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.ServiceAccountRepository;
import tech.kayys.numou.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccountId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAccountServiceTest {

    private InMemoryServiceAccountRepository repository;
    private IdentityOutboxPort outbox;
    private DomainClock clock;
    private ServiceAccountService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-20T10:00:00Z");
        clock = () -> now;
        repository = new InMemoryServiceAccountRepository();
        outbox = (tenantId, events) -> Uni.createFrom().voidItem();
        service = new ServiceAccountService(repository, outbox, new PassthroughUnitOfWork(), clock);
    }

    @Test
    void shouldCreateAndDisableServiceAccount() {
        UUID ownerId = UUID.randomUUID();
        CreateServiceAccountRequest request = new CreateServiceAccountRequest(
                "ci-runner",
                "Service account for CI pipeline",
                ownerId
        );

        ServiceAccountResponse response = service.create(request).await().indefinitely();
        assertNotNull(response.id());
        assertEquals("ci-runner", response.name());
        assertTrue(response.enabled());
        assertEquals(ownerId, response.ownerUserId());

        // Disable
        ServiceAccountResponse disabled = service.disable(response.id()).await().indefinitely();
        assertFalse(disabled.enabled());
    }

    @Test
    void shouldRejectDuplicateName() {
        CreateServiceAccountRequest request = new CreateServiceAccountRequest("agent-x", "Agent", null);
        service.create(request).await().indefinitely();

        assertThrows(ApplicationErrorException.class, () ->
                service.create(request).await().indefinitely()
        );
    }

    @Test
    void shouldListAllServiceAccounts() {
        service.create(new CreateServiceAccountRequest("sa-1", "First", null)).await().indefinitely();
        service.create(new CreateServiceAccountRequest("sa-2", "Second", null)).await().indefinitely();

        List<ServiceAccountResponse> list = service.listAll().await().indefinitely();
        assertEquals(2, list.size());
    }

    static class InMemoryServiceAccountRepository implements ServiceAccountRepository {
        private final Map<ServiceAccountId, ServiceAccount> store = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> save(ServiceAccount sa) {
            store.put(sa.id(), sa);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Optional<ServiceAccount>> findById(ServiceAccountId id) {
            return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Uni<Optional<ServiceAccount>> findByName(String name) {
            return Uni.createFrom().item(store.values().stream()
                    .filter(sa -> sa.name().equalsIgnoreCase(name))
                    .findFirst());
        }

        @Override
        public Uni<Boolean> existsByName(String name) {
            boolean exists = store.values().stream().anyMatch(sa -> sa.name().equalsIgnoreCase(name));
            return Uni.createFrom().item(exists);
        }

        @Override
        public Uni<List<ServiceAccount>> findAll() {
            return Uni.createFrom().item(new ArrayList<>(store.values()));
        }
    }
}
