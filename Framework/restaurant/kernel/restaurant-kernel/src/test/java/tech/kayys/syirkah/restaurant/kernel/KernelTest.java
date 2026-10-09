package tech.kayys.syirkah.restaurant.kernel;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.restaurant.kernel.memory.InMemoryDocumentRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelTest {

    static final class Counter extends RestaurantAggregate<VenueId> {
        int n;
        Counter(VenueId id, Instant now) { super(id, now); }
        void inc(Instant now) { n++; touch(now); }
        record Snap(UUID id, int n) { }
        Snap snap() { return new Snap(id().value(), n); }
        static Counter from(Snap s) { final var c = new Counter(VenueId.of(s.id()), Instant.EPOCH); c.n = s.n(); return c; }
    }

    private final Instant t0 = Instant.parse("2026-10-07T10:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final InMemoryDocumentRepository<Counter, VenueId, Counter.Snap> repo =
            new InMemoryDocumentRepository<>(Counter::snap, Counter::from);

    @Test
    void guestContactToStringNeverLeaksPersonalData() {
        final var g = new GuestContact("Budi Santoso", "+62 812 3456 7890", "budi@example.com");
        assertThat(g.toString()).doesNotContain("Budi", "Santoso", "3456", "7890 ", "budi@");
        assertThat(g.toString()).contains("***90", "@example.com");
    }

    @Test
    void guestContactNeedsAWayToReachTheGuestAndValidInput() {
        assertThatThrownBy(() -> new GuestContact("A", null, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestContact("A", "abc", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestContact("A", null, "not-an-email")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new GuestContact("A", null, "a@b.co").email()).isEqualTo("a@b.co");
    }

    @Test
    void backToBackRangesDoNotOverlap() {
        final var a = TimeRange.of(t0, Duration.ofHours(1));
        final var b = TimeRange.of(t0.plus(Duration.ofHours(1)), Duration.ofHours(1));
        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(TimeRange.of(t0.plusSeconds(3599), Duration.ofHours(1)))).isTrue();
    }

    @Test
    void savesAndReloadsFreshInstances() {
        final var c = new Counter(VenueId.generate(), t0); c.inc(t0);
        repo.save(tenant, c).await().indefinitely();
        final var loaded = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        assertThat(loaded).isNotSameAs(c);
        assertThat(loaded.n).isEqualTo(1);
        assertThat(loaded.storedVersion()).isEqualTo(1);
    }

    @Test
    void staleWriterIsRejected() {
        final var c = new Counter(VenueId.generate(), t0);
        repo.save(tenant, c).await().indefinitely();
        final var writer1 = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        final var writer2 = repo.find(tenant, c.id()).await().indefinitely().orElseThrow();
        writer1.inc(t0); repo.save(tenant, writer1).await().indefinitely();
        writer2.inc(t0);
        assertThatThrownBy(() -> repo.save(tenant, writer2).await().indefinitely()).isInstanceOf(ConcurrencyConflictException.class);
        assertThat(repo.find(tenant, c.id()).await().indefinitely().orElseThrow().n).isEqualTo(1);
    }

    @Test
    void creatingTheSameIdTwiceConflicts() {
        final var id = VenueId.generate();
        repo.save(tenant, new Counter(id, t0)).await().indefinitely();
        assertThatThrownBy(() -> repo.save(tenant, new Counter(id, t0)).await().indefinitely()).isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void anotherTenantCannotSeeTheAggregate() {
        final var c = new Counter(VenueId.generate(), t0);
        repo.save(tenant, c).await().indefinitely();
        assertThat(repo.find(UUID.randomUUID(), c.id()).await().indefinitely()).isEmpty();
    }

    @Test
    void sameIdInTwoTenantsAreIndependentRows() {
        final var id = VenueId.generate();
        repo.save(tenant, new Counter(id, t0)).await().indefinitely();
        repo.save(UUID.randomUUID(), new Counter(id, t0)).await().indefinitely(); // no conflict
    }
}
