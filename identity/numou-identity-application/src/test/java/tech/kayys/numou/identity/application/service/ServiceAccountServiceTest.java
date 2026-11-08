package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
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
    private ServiceAccountService service;
    private Instant now;
    private TenantId tenantA;
    private TenantId tenantB;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-20T10:00:00Z");
        DomainClock clock = () -> now;
        repository = new InMemoryServiceAccountRepository();
        IdentityOutboxPort outbox = (tenantId, events) -> Uni.createFrom().voidItem();
        service = new ServiceAccountService(repository, outbox, new PassthroughUnitOfWork(), clock);
        tenantA = TenantId.of(UUID.randomUUID());
        tenantB = TenantId.of(UUID.randomUUID());
    }

    @Test
    void shouldCreateAndDisableServiceAccountWithinTenant() {
        UUID ownerId = UUID.randomUUID();
        CreateServiceAccountRequest request = new CreateServiceAccountRequest(
                "ci-runner", "Service account for CI pipeline", ownerId);

        ServiceAccountResponse response = service.create(tenantA, request).await().indefinitely();
        assertNotNull(response.id());
        assertEquals("ci-runner", response.name());
        assertTrue(response.enabled());
        assertEquals(ownerId, response.ownerUserId());

        ServiceAccountResponse disabled = service.disable(tenantA, response.id(), UUID.randomUUID())
                .await().indefinitely();
        assertFalse(disabled.enabled());
    }

    @Test
    void shouldAllowSameNameInDifferentTenants() {
        CreateServiceAccountRequest request = new CreateServiceAccountRequest("agent-x", "Agent", null);

        service.create(tenantA, request).await().indefinitely();
        ServiceAccountResponse otherTenant = service.create(tenantB, request).await().indefinitely();

        assertNotNull(otherTenant.id());
        assertEquals(1, service.listAll(tenantA).await().indefinitely().size());
        assertEquals(1, service.listAll(tenantB).await().indefinitely().size());
    }

    @Test
    void shouldRejectDuplicateNameWithinSameTenant() {
        CreateServiceAccountRequest request = new CreateServiceAccountRequest("agent-x", "Agent", null);
        service.create(tenantA, request).await().indefinitely();

        assertThrows(ApplicationErrorException.class, () ->
                service.create(tenantA, request).await().indefinitely());
    }

    @Test
    void shouldNotAllowAnotherTenantToDisableAccount() {
        ServiceAccountResponse created = service.create(
                tenantA, new CreateServiceAccountRequest("agent-y", "Agent", null))
                .await().indefinitely();

        assertThrows(ApplicationErrorException.class, () ->
                service.disable(tenantB, created.id(), UUID.randomUUID()).await().indefinitely());
        assertTrue(service.listAll(tenantA).await().indefinitely().getFirst().enabled());
    }

    @Test
    void shouldListOnlyAccountsBelongingToRequestedTenant() {
        service.create(tenantA, new CreateServiceAccountRequest("sa-a", "Tenant A", null))
                .await().indefinitely();
        service.create(tenantB, new CreateServiceAccountRequest("sa-b", "Tenant B", null))
                .await().indefinitely();

        List<ServiceAccountResponse> list = service.listAll(tenantA).await().indefinitely();

        assertEquals(1, list.size());
        assertEquals("sa-a", list.getFirst().name());
    }

    static class InMemoryServiceAccountRepository implements ServiceAccountRepository {
        private final Map<ServiceAccountId, ServiceAccount> store = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> save(TenantId tenantId, ServiceAccount sa) {
            if (!tenantId.equals(sa.tenantId())) {
                return Uni.createFrom().failure(new IllegalArgumentException("tenant mismatch"));
            }
            store.put(sa.id(), sa);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Optional<ServiceAccount>> findById(TenantId tenantId, ServiceAccountId id) {
            return Uni.createFrom().item(Optional.ofNullable(store.get(id))
                    .filter(sa -> tenantId.equals(sa.tenantId())));
        }

        @Override
        public Uni<Optional<ServiceAccount>> findByName(TenantId tenantId, String name) {
            return Uni.createFrom().item(store.values().stream()
                    .filter(sa -> tenantId.equals(sa.tenantId()))
                    .filter(sa -> sa.name().equalsIgnoreCase(name))
                    .findFirst());
        }

        @Override
        public Uni<Boolean> existsByName(TenantId tenantId, String name) {
            boolean exists = store.values().stream()
                    .anyMatch(sa -> tenantId.equals(sa.tenantId()) && sa.name().equalsIgnoreCase(name));
            return Uni.createFrom().item(exists);
        }

        @Override
        public Uni<List<ServiceAccount>> findAll(TenantId tenantId) {
            List<ServiceAccount> tenantAccounts = store.values().stream()
                    .filter(sa -> tenantId.equals(sa.tenantId()))
                    .toList();
            return Uni.createFrom().item(tenantAccounts);
        }
    }
}
