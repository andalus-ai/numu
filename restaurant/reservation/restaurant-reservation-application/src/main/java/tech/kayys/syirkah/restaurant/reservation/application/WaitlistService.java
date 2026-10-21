package tech.kayys.syirkah.restaurant.reservation.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.aggregate.application.ConflictRetry;
import tech.kayys.syirkah.restaurant.kernel.domain.GuestContact;
import tech.kayys.syirkah.aggregate.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.domain.WaitlistEntryId;
import tech.kayys.syirkah.aggregate.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.reservation.domain.WaitlistEntry;
import tech.kayys.syirkah.restaurant.reservation.spi.WaitlistRepository;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Walk-in waitlist use cases. */
public final class WaitlistService {

    private static final int MAX_ATTEMPTS = 3;

    public record View(UUID id, String guestName, int partySize, String status, long quotedWaitMinutes) {
        static View of(WaitlistEntry e) {
            return new View(e.id().value(), e.guest().name(), e.party().value(), e.status().name(), e.quotedWait().toMinutes());
        }
    }

    private final WaitlistRepository repo;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final Duration averageTurn;
    private final Duration holdFor;

    public WaitlistService(WaitlistRepository repo, UnitOfWork uow, DomainEventSink events, DomainClock clock,
                           Duration averageTurn, Duration holdFor) {
        this.repo = repo;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.averageTurn = averageTurn;
        this.holdFor = holdFor;
    }

    public Uni<View> join(UUID tenantId, UUID venueId, String name, String phone, String email, int partySize, int comparableTables) {
        final var venue = VenueId.of(venueId);
        return uow.execute(() -> repo.findOpen(tenantId, venue).flatMap(open -> {
            final var now = clock.now();
            final var ahead = (int) open.stream().filter(e -> e.status() == WaitlistEntry.Status.WAITING).count();
            final var entry = WaitlistEntry.join(WaitlistEntryId.generate(), venue, new GuestContact(name, phone, email),
                    PartySize.of(partySize), WaitlistEntry.quoteWait(ahead, comparableTables, averageTurn), now);
            return repo.save(tenantId, entry)
                    .flatMap(x -> events.publish(tenantId, entry.pullDomainEvents()))
                    .replaceWith(View.of(entry));
        }));
    }

    /**
     * A table seating up to {@code freedCapacity} became free: expire stale holds, then notify the longest-waiting
     * party that fits. Returns empty when nobody fits.
     */
    public Uni<Optional<View>> notifyNext(UUID tenantId, UUID venueId, int freedCapacity) {
        final var venue = VenueId.of(venueId);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> repo.findOpen(tenantId, venue).flatMap(open -> {
            final var now = clock.now();
            Uni<Void> expiries = Uni.createFrom().voidItem();
            for (final var e : open) {
                if (e.expireIfOverdue(now)) {
                    expiries = expiries.flatMap(x -> repo.save(tenantId, e)).flatMap(x -> events.publish(tenantId, e.pullDomainEvents()));
                }
            }
            final var next = open.stream()
                    .filter(e -> e.status() == WaitlistEntry.Status.WAITING && e.party().value() <= freedCapacity)
                    .min(Comparator.comparing(WaitlistEntry::joinedAt));
            return expiries.flatMap(x -> {
                if (next.isEmpty()) {
                    return Uni.createFrom().item(Optional.<View>empty());
                }
                final var entry = next.get();
                entry.notifyTableReady(now, holdFor);
                return repo.save(tenantId, entry)
                        .flatMap(y -> events.publish(tenantId, entry.pullDomainEvents()))
                        .replaceWith(Optional.of(View.of(entry)));
            });
        })));
    }

    public Uni<View> seat(UUID tenantId, UUID entryId) {
        return change(tenantId, entryId, e -> e.seat(clock.now()));
    }

    public Uni<View> leave(UUID tenantId, UUID entryId) {
        return change(tenantId, entryId, e -> e.leave(clock.now()));
    }

    public Uni<List<View>> queue(UUID tenantId, UUID venueId) {
        return repo.findOpen(tenantId, VenueId.of(venueId)).map(l -> l.stream().map(View::of).toList());
    }

    private Uni<View> change(UUID tenantId, UUID entryId, java.util.function.Consumer<WaitlistEntry> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() ->
                repo.find(tenantId, WaitlistEntryId.of(entryId)).map(o -> o.orElseThrow(() -> new NotFoundException("Waitlist entry", entryId)))
                        .flatMap(entry -> {
                            action.accept(entry);
                            return repo.save(tenantId, entry)
                                    .flatMap(x -> events.publish(tenantId, entry.pullDomainEvents()))
                                    .replaceWith(View.of(entry));
                        })));
    }
}
