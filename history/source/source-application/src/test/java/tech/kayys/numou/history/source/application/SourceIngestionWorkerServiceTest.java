package tech.kayys.numou.history.source.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.source.domain.SourceIngestionLease;
import tech.kayys.numou.history.source.spi.SourceIngestionDispatchRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SourceIngestionWorkerServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void claimUsesTrustedTenantAndBoundedLeaseDuration() {
        UUID tenant = UUID.randomUUID();
        UUID ingestion = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AtomicInteger authorized = new AtomicInteger();
        var repository = new DispatchFake(ingestion, token);
        var service = new SourceIngestionWorkerService(repository, () -> tenant,
                new SourceIngestionWorkerAuthorizationPort() {
                    public void requireCanClaim(UUID t, String worker) {
                        assertEquals(tenant, t);
                        assertEquals("worker:ocr", worker);
                        authorized.incrementAndGet();
                    }
                    public void requireCanTransition(UUID t, UUID i, String worker) {}
                }, Clock.fixed(NOW, ZoneOffset.UTC));

        var lease = service.claimNext(" worker:ocr ", Duration.ofMinutes(5)).orElseThrow();

        assertEquals(token, lease.leaseToken());
        assertEquals(1, lease.attempt());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), repository.leaseUntil);
        assertEquals(1, authorized.get());
        assertThrows(IllegalArgumentException.class,
                () -> service.claimNext("worker:ocr", Duration.ofMillis(500)));
        assertThrows(IllegalArgumentException.class,
                () -> service.claimNext("worker:ocr", Duration.ofMinutes(31)));
    }

    @Test
    void completionIsAuthorizedAndDelegatesWorkerAndLeaseTokenForFencing() {
        UUID tenant = UUID.randomUUID();
        UUID ingestion = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AtomicInteger authorized = new AtomicInteger();
        var repository = new DispatchFake(ingestion, token);
        var service = new SourceIngestionWorkerService(repository, () -> tenant,
                new SourceIngestionWorkerAuthorizationPort() {
                    public void requireCanClaim(UUID t, String worker) {}
                    public void requireCanTransition(UUID t, UUID i, String worker) {
                        assertEquals(tenant, t);
                        assertEquals(ingestion, i);
                        assertEquals("worker:ocr", worker);
                        authorized.incrementAndGet();
                    }
                }, Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(service.complete(ingestion, "worker:ocr", token));
        assertEquals(token, repository.transitionToken);
        assertEquals("worker:ocr", repository.transitionWorker);
        assertEquals(1, authorized.get());
    }

    private static final class DispatchFake implements SourceIngestionDispatchRepository {
        private final UUID ingestion;
        private final UUID token;
        private Instant leaseUntil;
        private UUID transitionToken;
        private String transitionWorker;

        private DispatchFake(UUID ingestion, UUID token) {
            this.ingestion = ingestion;
            this.token = token;
        }

        public Optional<SourceIngestionLease> claimNext(UUID tenant, String worker, Instant now, Instant until) {
            leaseUntil = until;
            return Optional.of(new SourceIngestionLease(ingestion, worker, token, 1, now, until));
        }
        public boolean complete(UUID tenant, UUID id, String worker, UUID leaseToken, Instant at) {
            transitionToken = leaseToken;
            transitionWorker = worker;
            return true;
        }
        public boolean scheduleRetry(UUID tenant, UUID id, String worker, UUID leaseToken, Instant availableAt,
                                      Instant failedAt, String error) {
            transitionToken = leaseToken;
            transitionWorker = worker;
            return true;
        }
        public boolean deadLetter(UUID tenant, UUID id, String worker, UUID leaseToken, Instant failedAt, String error) {
            transitionToken = leaseToken;
            transitionWorker = worker;
            return true;
        }
    }
}
