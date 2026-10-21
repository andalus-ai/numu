package tech.kayys.syirkah.restaurant.reservation.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.aggregate.application.ConflictRetry;
import tech.kayys.syirkah.restaurant.kernel.domain.GuestContact;
import tech.kayys.syirkah.aggregate.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;
import tech.kayys.syirkah.restaurant.kernel.domain.ReservationId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.aggregate.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.reservation.domain.AvailabilityLedger;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingRules;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingSource;
import tech.kayys.syirkah.restaurant.reservation.domain.CancellationOutcome;
import tech.kayys.syirkah.restaurant.reservation.domain.LedgerId;
import tech.kayys.syirkah.restaurant.reservation.domain.NoAvailabilityException;
import tech.kayys.syirkah.restaurant.reservation.domain.Reservation;
import tech.kayys.syirkah.restaurant.reservation.domain.TableInfo;
import tech.kayys.syirkah.restaurant.reservation.spi.BookingRulesPort;
import tech.kayys.syirkah.restaurant.reservation.spi.LedgerRepository;
import tech.kayys.syirkah.restaurant.reservation.spi.ReservationRepository;
import tech.kayys.syirkah.restaurant.reservation.spi.TableDirectoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reservation use cases. Each mutating use case is one {@link UnitOfWork}: aggregate writes and the events
 * they raise commit together (the event sink appends to the transactional outbox in the same transaction), and
 * the whole unit is retried when it loses an optimistic-lock race on the availability ledger.
 */
public final class ReservationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ALTERNATIVES = 3;

    private final ReservationRepository reservations;
    private final LedgerRepository ledgers;
    private final BookingRulesPort rulesPort;
    private final TableDirectoryPort tablesPort;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public ReservationService(ReservationRepository reservations, LedgerRepository ledgers, BookingRulesPort rulesPort,
                              TableDirectoryPort tablesPort, UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.reservations = reservations;
        this.ledgers = ledgers;
        this.rulesPort = rulesPort;
        this.tablesPort = tablesPort;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    public record CreateCommand(UUID tenantId, UUID venueId, String guestName, String guestPhone, String guestEmail,
                                int partySize, Instant startsAt, BookingSource source, String notes, Set<String> dietary) { }

    public record CancelResult(ReservationView reservation, CancellationOutcome outcome) { }

    // ---------------------------------------------------------------- create

    public Uni<ReservationView> create(CreateCommand cmd) {
        final var venue = VenueId.of(cmd.venueId());
        final var id = ReservationId.generate(); // fixed across retries: a retry must not create a second booking
        return rulesPort.rulesFor(cmd.tenantId(), venue).flatMap(rules ->
                tablesPort.tablesFor(cmd.tenantId(), venue).flatMap(tables ->
                        ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> doCreate(cmd, venue, id, rules, tables)))
                                .onFailure(NoAvailabilityException.class).recoverWithUni(f ->
                                        alternatives(cmd, venue, rules, tables).flatMap(alts ->
                                                Uni.<ReservationView>createFrom().failure(new SlotUnavailableException(f.getMessage(), alts))))));
    }

    private Uni<ReservationView> doCreate(CreateCommand cmd, VenueId venue, ReservationId id, BookingRules rules, List<TableInfo> tables) {
        final Instant now = clock.now();
        final var party = PartySize.of(cmd.partySize());
        final var reservation = Reservation.request(id, venue, new GuestContact(cmd.guestName(), cmd.guestPhone(), cmd.guestEmail()),
                party, cmd.startsAt(), cmd.source(), cmd.notes(), cmd.dietary(), rules, now);
        return loadOrOpenLedger(cmd.tenantId(), venue, dayOf(cmd.startsAt(), rules), now).flatMap(ledger -> {
            final var allocation = ledger.allocate(id, party, cmd.startsAt(), rules, tables, now);
            reservation.assignTables(allocation.tableIds(), now);
            return ledgers.save(cmd.tenantId(), ledger)
                    .flatMap(x -> reservations.save(cmd.tenantId(), reservation))
                    .flatMap(x -> publish(cmd.tenantId(), ledger.pullDomainEvents(), reservation.pullDomainEvents()))
                    .replaceWith(ReservationView.of(reservation));
        });
    }

    private Uni<List<Instant>> alternatives(CreateCommand cmd, VenueId venue, BookingRules rules, List<TableInfo> tables) {
        final Instant now = clock.now();
        final var party = PartySize.of(cmd.partySize());
        return loadOrOpenLedger(cmd.tenantId(), venue, dayOf(cmd.startsAt(), rules), now).map(ledger ->
                ledger.availableStarts(party, rules, tables, now).stream()
                        .sorted(Comparator.comparingLong((Instant s) -> Math.abs(s.getEpochSecond() - cmd.startsAt().getEpochSecond())))
                        .limit(MAX_ALTERNATIVES).sorted().toList());
    }

    // ---------------------------------------------------------------- availability query

    public Uni<List<Instant>> availableSlots(UUID tenantId, UUID venueId, LocalDate day, int partySize) {
        final var venue = VenueId.of(venueId);
        return rulesPort.rulesFor(tenantId, venue).flatMap(rules ->
                tablesPort.tablesFor(tenantId, venue).flatMap(tables ->
                        loadOrOpenLedger(tenantId, venue, day, clock.now()).map(ledger ->
                                ledger.availableStarts(PartySize.of(partySize), rules, tables, clock.now()))));
    }

    // ---------------------------------------------------------------- lifecycle

    public Uni<ReservationView> confirmDeposit(UUID tenantId, UUID reservationId, String paymentRef) {
        return mutate(tenantId, reservationId, (r, rules, now) -> {
            r.confirmDeposit(paymentRef, now);
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<ReservationView> seat(UUID tenantId, UUID reservationId) {
        return mutate(tenantId, reservationId, (r, rules, now) -> {
            r.seat(now, rules);
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<ReservationView> complete(UUID tenantId, UUID reservationId) {
        return mutate(tenantId, reservationId, (r, rules, now) -> {
            r.complete(now);
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<CancelResult> cancel(UUID tenantId, UUID reservationId, String reason, boolean waivePenalty) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, reservationId).flatMap(r ->
                rulesPort.rulesFor(tenantId, r.venueId()).flatMap(rules -> {
                    final Instant now = clock.now();
                    final var outcome = r.cancel(reason, now, rules, waivePenalty);
                    return releaseAndSave(tenantId, r, rules, now)
                            .replaceWith(new CancelResult(ReservationView.of(r), outcome));
                }))));
    }

    public Uni<CancelResult> markNoShow(UUID tenantId, UUID reservationId, boolean waivePenalty) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, reservationId).flatMap(r ->
                rulesPort.rulesFor(tenantId, r.venueId()).flatMap(rules -> {
                    final Instant now = clock.now();
                    final var outcome = r.markNoShow(now, rules, waivePenalty);
                    return releaseAndSave(tenantId, r, rules, now)
                            .replaceWith(new CancelResult(ReservationView.of(r), outcome));
                }))));
    }

    /**
     * Moves a booking. The new slot is allocated <em>before</em> the old one is released, in one unit of work,
     * so a failed reschedule (no availability) leaves the original booking untouched.
     */
    public Uni<ReservationView> reschedule(UUID tenantId, UUID reservationId, Instant newStart, int newPartySize) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, reservationId).flatMap(r ->
                rulesPort.rulesFor(tenantId, r.venueId()).flatMap(rules ->
                        tablesPort.tablesFor(tenantId, r.venueId()).flatMap(tables -> {
                            final Instant now = clock.now();
                            final var oldDay = dayOf(r.startsAt(), rules);
                            final var newDay = dayOf(newStart, rules);
                            final var party = PartySize.of(newPartySize);
                            r.reschedule(newStart, party, rules, now);
                            return loadOrOpenLedger(tenantId, r.venueId(), newDay, now).flatMap(newLedger ->
                                    oldDay.equals(newDay)
                                            ? sameDayMove(tenantId, r, party, newStart, rules, tables, newLedger, now)
                                            : crossDayMove(tenantId, r, party, newStart, rules, tables, oldDay, newLedger, now));
                        })))));
    }

    private Uni<ReservationView> sameDayMove(UUID tenantId, Reservation r, PartySize party, Instant newStart, BookingRules rules,
                                             List<TableInfo> tables, AvailabilityLedger ledger, Instant now) {
        ledger.release(r.id(), now); // same aggregate: release + allocate commit atomically, so no window where the slot is lost
        final var allocation = ledger.allocate(r.id(), party, newStart, rules, tables, now);
        r.assignTables(allocation.tableIds(), now);
        return ledgers.save(tenantId, ledger)
                .flatMap(x -> reservations.save(tenantId, r))
                .flatMap(x -> publish(tenantId, ledger.pullDomainEvents(), r.pullDomainEvents()))
                .replaceWith(ReservationView.of(r));
    }

    private Uni<ReservationView> crossDayMove(UUID tenantId, Reservation r, PartySize party, Instant newStart, BookingRules rules,
                                              List<TableInfo> tables, LocalDate oldDay, AvailabilityLedger newLedger, Instant now) {
        final var allocation = newLedger.allocate(r.id(), party, newStart, rules, tables, now);
        r.assignTables(allocation.tableIds(), now);
        return loadOrOpenLedger(tenantId, r.venueId(), oldDay, now).flatMap(oldLedger -> {
            oldLedger.release(r.id(), now);
            return ledgers.save(tenantId, newLedger)
                    .flatMap(x -> ledgers.save(tenantId, oldLedger))
                    .flatMap(x -> reservations.save(tenantId, r))
                    .flatMap(x -> publish(tenantId, newLedger.pullDomainEvents(), oldLedger.pullDomainEvents(), r.pullDomainEvents()))
                    .replaceWith(ReservationView.of(r));
        });
    }

    @FunctionalInterface
    private interface Mutation {
        Uni<Void> apply(Reservation r, BookingRules rules, Instant now);
    }

    private Uni<ReservationView> mutate(UUID tenantId, UUID reservationId, Mutation mutation) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, reservationId).flatMap(r ->
                rulesPort.rulesFor(tenantId, r.venueId()).flatMap(rules ->
                        mutation.apply(r, rules, clock.now())
                                .flatMap(x -> reservations.save(tenantId, r))
                                .flatMap(x -> publish(tenantId, r.pullDomainEvents()))
                                .replaceWith(ReservationView.of(r))))));
    }

    public Uni<ReservationView> get(UUID tenantId, UUID reservationId) {
        return load(tenantId, reservationId).map(ReservationView::of);
    }

    public Uni<List<ReservationView>> listForDay(UUID tenantId, UUID venueId, LocalDate day) {
        final var venue = VenueId.of(venueId);
        return rulesPort.rulesFor(tenantId, venue).flatMap(rules -> {
            final var from = day.atStartOfDay(rules.zone()).toInstant();
            final var to = day.plusDays(1).atStartOfDay(rules.zone()).toInstant();
            return reservations.findStartingBetween(tenantId, venue, from, to)
                    .map(list -> list.stream().map(ReservationView::of).toList());
        });
    }

    // ---------------------------------------------------------------- helpers

    private Uni<Void> releaseAndSave(UUID tenantId, Reservation r, BookingRules rules, Instant now) {
        return loadOrOpenLedger(tenantId, r.venueId(), dayOf(r.startsAt(), rules), now).flatMap(ledger -> {
            ledger.release(r.id(), now);
            return ledgers.save(tenantId, ledger)
                    .flatMap(x -> reservations.save(tenantId, r))
                    .flatMap(x -> publish(tenantId, ledger.pullDomainEvents(), r.pullDomainEvents()));
        });
    }

    private Uni<Reservation> load(UUID tenantId, UUID reservationId) {
        return reservations.find(tenantId, ReservationId.of(reservationId)).map(o ->
                o.orElseThrow(() -> new NotFoundException("Reservation", reservationId)));
    }

    private Uni<AvailabilityLedger> loadOrOpenLedger(UUID tenantId, VenueId venue, LocalDate day, Instant now) {
        return ledgers.find(tenantId, LedgerId.forVenueDay(venue, day))
                .map(o -> o.orElseGet(() -> AvailabilityLedger.open(venue, day, now)));
    }

    private static LocalDate dayOf(Instant instant, BookingRules rules) {
        return instant.atZone(rules.zone()).toLocalDate();
    }

    @SafeVarargs
    private Uni<Void> publish(UUID tenantId, List<DomainEvent>... batches) {
        final var all = new ArrayList<DomainEvent>();
        for (final var b : batches) {
            all.addAll(b);
        }
        return all.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, all);
    }
}
