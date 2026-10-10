package tech.kayys.syirkah.reservation.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.reservation.domain.*;
import tech.kayys.syirkah.reservation.spi.DomainEventSink;
import tech.kayys.syirkah.reservation.spi.WaitlistRepository;

import java.time.Duration;
import java.util.*;

public final class WaitlistService {

    private static final int MAX_ATTEMPTS = 3;

    public record View(UUID id, String guestName, int partySize, String status, long quotedWaitMinutes) {
        public static View of(WaitlistEntry e) {
            return new View(e.id().value(), e.guest().name(), e.party().value(), e.status().name(), e.quotedWait().toMinutes());
        }
    }

    private final WaitlistRepository waitlist;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final Duration averageTurn;
    private final Duration waitlistHold;

    public WaitlistService(
            WaitlistRepository waitlist,
            UnitOfWork uow,
            DomainEventSink events,
            DomainClock clock,
            Duration averageTurn,
            Duration waitlistHold
    ) {
        this.waitlist = Objects.requireNonNull(waitlist, "waitlist cannot be null");
        this.uow = Objects.requireNonNull(uow, "uow cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.averageTurn = averageTurn == null ? Duration.ofMinutes(60) : averageTurn;
        this.waitlistHold = waitlistHold == null ? Duration.ofMinutes(10) : waitlistHold;
    }

    public Uni<UUID> join(UUID tenantId, UUID facilityId, CustomerContact customer, int partySize, String notes) {
        final var id = WaitlistEntryId.generate();
        final var now = clock.now();
        final var entry = WaitlistEntry.join(id, FacilityId.of(facilityId), customer, PartySize.of(partySize), notes, now);

        return uow.execute(() ->
                waitlist.save(tenantId, entry)
                        .flatMap(x -> events.publish(tenantId, entry.pullDomainEvents()))
                        .map(x -> id.value())
        );
    }

    public Uni<View> join(UUID tenantId, UUID venueId, String name, String phone, String email, int partySize, int comparableResources) {
        final var facility = FacilityId.of(venueId);
        return uow.execute(() -> waitlist.findOpen(tenantId, facility).flatMap(open -> {
            final var now = clock.now();
            final var ahead = (int) open.stream().filter(e -> e.status() == WaitlistEntry.Status.WAITING).count();
            final var entry = WaitlistEntry.join(
                    WaitlistEntryId.generate(),
                    facility,
                    new CustomerContact(name, phone, email),
                    PartySize.of(partySize),
                    WaitlistEntry.quoteWait(ahead, comparableResources, averageTurn),
                    now
            );
            return waitlist.save(tenantId, entry)
                    .flatMap(x -> events.publish(tenantId, entry.pullDomainEvents()))
                    .replaceWith(View.of(entry));
        }));
    }

    public Uni<Optional<View>> notifyNext(UUID tenantId, UUID venueId, int freedCapacity) {
        final var facility = FacilityId.of(venueId);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> waitlist.findOpen(tenantId, facility).flatMap(open -> {
            final var now = clock.now();
            Uni<Void> expiries = Uni.createFrom().voidItem();
            for (final var e : open) {
                if (e.expireIfOverdue(now)) {
                    expiries = expiries.flatMap(x -> waitlist.save(tenantId, e)).flatMap(x -> events.publish(tenantId, e.pullDomainEvents()));
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
                entry.notifyTableReady(now, waitlistHold);
                return waitlist.save(tenantId, entry)
                        .flatMap(y -> events.publish(tenantId, entry.pullDomainEvents()))
                        .replaceWith(Optional.of(View.of(entry)));
            });
        })));
    }

    public Uni<Void> notifyCustomer(UUID tenantId, UUID waitlistId) {
        return change(tenantId, waitlistId, e -> e.notifyCustomer(waitlistHold, clock.now())).replaceWithVoid();
    }

    public Uni<Void> checkIn(UUID tenantId, UUID waitlistId) {
        return change(tenantId, waitlistId, e -> e.checkIn(clock.now())).replaceWithVoid();
    }

    // Compatibility method for dining contexts
    public Uni<View> seat(UUID tenantId, UUID waitlistId) {
        return change(tenantId, waitlistId, e -> e.seat(clock.now())).map(View::of);
    }

    public Uni<View> leave(UUID tenantId, UUID waitlistId) {
        return change(tenantId, waitlistId, e -> e.leave("Left", clock.now())).map(View::of);
    }

    public Uni<Void> leave(UUID tenantId, UUID waitlistId, String reason) {
        return change(tenantId, waitlistId, e -> e.leave(reason, clock.now())).replaceWithVoid();
    }

    public Uni<Void> expireStale(UUID tenantId, UUID facilityId) {
        final var now = clock.now();
        return waitlist.findOpen(tenantId, FacilityId.of(facilityId)).flatMap(entries -> {
            var chain = Uni.createFrom().voidItem();
            for (WaitlistEntry e : entries) {
                if (e.status() == WaitlistEntry.Status.NOTIFIED && now.isAfter(e.holdUntil())) {
                    e.expire(now);
                    chain = chain.flatMap(x -> uow.execute(() ->
                            waitlist.save(tenantId, e).flatMap(y -> events.publish(tenantId, e.pullDomainEvents()))
                    ));
                }
            }
            return chain;
        });
    }

    public Uni<List<WaitlistEntry>> openEntries(UUID tenantId, UUID facilityId) {
        return waitlist.findOpen(tenantId, FacilityId.of(facilityId));
    }

    public Uni<List<View>> queue(UUID tenantId, UUID facilityId) {
        return waitlist.findOpen(tenantId, FacilityId.of(facilityId)).map(l -> l.stream().map(View::of).toList());
    }

    private Uni<WaitlistEntry> change(UUID tenantId, UUID waitlistId, java.util.function.Consumer<WaitlistEntry> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () ->
                waitlist.find(tenantId, WaitlistEntryId.of(waitlistId))
                        .map(opt -> opt.orElseThrow(() -> new NoSuchElementException("Waitlist entry not found: " + waitlistId)))
                        .flatMap(e -> {
                            action.accept(e);
                            return uow.execute(() ->
                                    waitlist.save(tenantId, e)
                                            .flatMap(x -> events.publish(tenantId, e.pullDomainEvents()))
                                            .replaceWith(e)
                            );
                        })
        );
    }
}
