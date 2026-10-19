package tech.kayys.numou.foundation.application.identifier;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.identifier.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierAllocationServiceTest {

    private InMemoryIdentifierSequenceStore sequenceStore;
    private Clock fixedClock;
    private IdentifierPolicy testPolicy;

    @BeforeEach
    void setUp() {
        sequenceStore = new InMemoryIdentifierSequenceStore();
        Instant fixedInstant = Instant.parse("2026-06-15T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        testPolicy = new IdentifierPolicy(
                IdentifierPolicyId.generate(),
                "SALES_ORDER",
                IdentifierFormat.of("SO-", 6),
                IdentifierPolicyStatus.ACTIVE,
                false,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
    }

    @Test
    void shouldAllocateIdentifierSuccessfully() {
        IdentifierPolicyRepository repository = (namespace, scope) -> Uni.createFrom().item(Optional.of(testPolicy));
        IdentifierAllocationService service = new IdentifierAllocationService(repository, sequenceStore, fixedClock);

        TenantId tenantId = TenantId.generate();
        IdentifierScope scope = IdentifierScope.ofTenant(tenantId);

        IdentifierAllocation first = service.allocate("SALES_ORDER", scope).await().indefinitely();
        assertEquals("SO-2026-000001", first.identifier().value());
        assertEquals(1L, first.sequenceNumber());

        IdentifierAllocation second = service.allocate("SALES_ORDER", scope).await().indefinitely();
        assertEquals("SO-2026-000002", second.identifier().value());
        assertEquals(2L, second.sequenceNumber());
    }

    @Test
    void shouldIsolateSequencesAcrossTenants() {
        IdentifierPolicyRepository repository = (namespace, scope) -> Uni.createFrom().item(Optional.of(testPolicy));
        IdentifierAllocationService service = new IdentifierAllocationService(repository, sequenceStore, fixedClock);

        TenantId tenant1 = TenantId.generate();
        TenantId tenant2 = TenantId.generate();

        IdentifierAllocation alloc1 = service.allocate("SALES_ORDER", IdentifierScope.ofTenant(tenant1)).await().indefinitely();
        IdentifierAllocation alloc2 = service.allocate("SALES_ORDER", IdentifierScope.ofTenant(tenant2)).await().indefinitely();

        assertEquals("SO-2026-000001", alloc1.identifier().value());
        assertEquals("SO-2026-000001", alloc2.identifier().value());
    }

    @Test
    void shouldFailWhenPolicyNotFoundOrInactive() {
        IdentifierPolicyRepository emptyRepo = (namespace, scope) -> Uni.createFrom().item(Optional.empty());
        IdentifierAllocationService service = new IdentifierAllocationService(emptyRepo, sequenceStore, fixedClock);

        assertThrows(IllegalStateException.class, () ->
                service.allocate("UNKNOWN", IdentifierScope.ofTenant(TenantId.generate())).await().indefinitely());
    }

    @Test
    void shouldHandleConcurrentAllocationsWithoutDuplicates() throws Exception {
        IdentifierPolicyRepository repository = (namespace, scope) -> Uni.createFrom().item(Optional.of(testPolicy));
        IdentifierAllocationService service = new IdentifierAllocationService(repository, sequenceStore, fixedClock);

        TenantId tenantId = TenantId.generate();
        IdentifierScope scope = IdentifierScope.ofTenant(tenantId);

        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                IdentifierAllocation alloc = service.allocate("SALES_ORDER", scope).await().indefinitely();
                return alloc.sequenceNumber();
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        List<Long> sequences = futures.stream().map(CompletableFuture::join).sorted().toList();
        assertEquals(concurrency, sequences.size());
        assertEquals(1L, sequences.getFirst());
        assertEquals((long) concurrency, sequences.getLast());

        // Ensure all are unique
        assertEquals(concurrency, sequences.stream().distinct().count());
    }
}
