package tech.kayys.numou.aggregate;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;
import tech.kayys.numou.aggregate.domain.TimeRange;
import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateKernelTest {

    record TestId(UUID value) implements DomainId<UUID> {
        static TestId generate() { return new TestId(UUID.randomUUID()); }
    }

    static final class Counter extends VersionedAggregate<TestId> {
        int n;
        Counter(TestId id, Instant now) { super(id, now); }
        void inc(Instant now) { n++; touch(now); }
        record Snap(UUID id, int n) { }
        Snap snap() { return new Snap(id().value(), n); }
        static Counter from(Snap s) { final var c = new Counter(new TestId(s.id()), Instant.EPOCH); c.n = s.n(); return c; }
    }

    private final Instant t0 = Instant.parse("2026-10-07T10:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final InMemoryDocumentRepository<Counter, TestId, Counter.Snap> repo = new InMemoryDocumentRepository<>(Counter::snap, Counter::from);

    @Test
    void backToBackRangesDoNotOverlap() {
        final var a = TimeRange.of(t0, Duration.ofHours(1));
        final var b = TimeRange.of(t0.plus(Duration.ofHours(1)), Duration.ofHours(1));
        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(TimeRange.of(t0.plusSeconds(3599), Duration.ofHours(1)))).isTrue();
    }

    @Test
    void savesAndReloadsFreshInstances() {
        final var c = new Counter(TestId.generate(), t0); c.inc(t0);
        repo.save(tenant, c).await().indefinitely();
        final var loaded = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        assertThat(loaded).isNotSameAs(c);
        assertThat(loaded.n).isEqualTo(1);
        assertThat(loaded.storedVersion()).isEqualTo(1);
    }

    @Test
    void staleWriterIsRejected() {
        final var c = new Counter(TestId.generate(), t0);
        repo.save(tenant, c).await().indefinitely();
        final var w1 = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        final var w2 = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        w1.inc(t0); repo.save(tenant, w1).await().indefinitely();
        w2.inc(t0);
        assertThatThrownBy(() -> repo.save(tenant, w2).await().indefinitely()).isInstanceOf(ConcurrencyConflictException.class);
        assertThat(repo.find(tenant, c.id()).await().indefinitely().orElseThrow().n).isEqualTo(1);
    }

    @Test
    void creatingTheSameIdTwiceConflicts() {
        final var id = TestId.generate();
        repo.save(tenant, new Counter(id, t0)).await().indefinitely();
        assertThatThrownBy(() -> repo.save(tenant, new Counter(id, t0)).await().indefinitely()).isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void anotherTenantCannotSeeTheAggregate() {
        final var c = new Counter(TestId.generate(), t0);
        repo.save(tenant, c).await().indefinitely();
        assertThat(repo.find(UUID.randomUUID(), c.id()).await().indefinitely()).isEmpty();
    }

    @Test
    void sameIdInTwoTenantsAreIndependentRows() {
        final var id = TestId.generate();
        repo.save(tenant, new Counter(id, t0)).await().indefinitely();
        repo.save(UUID.randomUUID(), new Counter(id, t0)).await().indefinitely();
    }
}
