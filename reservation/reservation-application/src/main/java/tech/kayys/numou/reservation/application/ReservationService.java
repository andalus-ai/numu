package tech.kayys.numou.reservation.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.BookingRulesPort;
import tech.kayys.numou.reservation.spi.DomainEventSink;
import tech.kayys.numou.reservation.spi.LedgerRepository;
import tech.kayys.numou.reservation.spi.IdempotentReservationRepository;
import tech.kayys.numou.reservation.spi.ReservationRepository;
import tech.kayys.numou.reservation.spi.ResourceDirectoryPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public final class ReservationService {

    private static final int MAX_ATTEMPTS = 3;

    private final ReservationRepository reservations;
    private final LedgerRepository ledgers;
    private final ResourceDirectoryPort resourceDirectory;
    private final BookingRulesPort rulesPort;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public ReservationService(
            ReservationRepository reservations,
            LedgerRepository ledgers,
            ResourceDirectoryPort resourceDirectory,
            BookingRulesPort rulesPort,
            UnitOfWork uow,
            DomainEventSink events,
            DomainClock clock
    ) {
        this.reservations = Objects.requireNonNull(reservations, "reservations cannot be null");
        this.ledgers = Objects.requireNonNull(ledgers, "ledgers cannot be null");
        this.resourceDirectory = Objects.requireNonNull(resourceDirectory, "resourceDirectory cannot be null");
        this.rulesPort = Objects.requireNonNull(rulesPort, "rulesPort cannot be null");
        this.uow = Objects.requireNonNull(uow, "uow cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ReservationService(
            ReservationRepository reservations,
            LedgerRepository ledgers,
            BookingRulesPort rulesPort,
            ResourceDirectoryPort resourceDirectory,
            UnitOfWork uow,
            DomainEventSink events,
            DomainClock clock
    ) {
        this(reservations, ledgers, resourceDirectory, rulesPort, uow, events, clock);
    }

    public record CreateCommand(UUID tenantId, UUID venueId, String guestName, String guestPhone, String guestEmail,
                                int partySize, Instant startsAt, BookingSource source, String notes, Set<String> dietary) { }

    public record CancelResult(ReservationView reservation, CancellationOutcome outcome) { }

    public Uni<ReservationView> create(CreateCommand cmd) {
        final var customer = new CustomerContact(cmd.guestName(), cmd.guestPhone(), cmd.guestEmail());
        final var fId = FacilityId.of(cmd.venueId());
        final var party = PartySize.of(cmd.partySize());
        final var id = ReservationId.generate();

        return rulesPort.rulesFor(cmd.tenantId(), fId).flatMap(rules ->
                resourceDirectory.resourcesFor(cmd.tenantId(), fId).flatMap(resList ->
                        ConflictRetry.run(MAX_ATTEMPTS, () ->
                                uow.execute(() -> doBook(cmd.tenantId(), fId, id, customer, party, cmd.startsAt(),
                                        cmd.source() == null ? BookingSource.WEB : cmd.source(),
                                        cmd.notes(), cmd.dietary() == null ? Set.of() : cmd.dietary(), rules, resList))
                        ).onFailure(NoAvailabilityException.class).recoverWithUni(f ->
                                alternatives(cmd.tenantId(), fId, party, cmd.startsAt(), rules, resList).flatMap(alts ->
                                        Uni.<ReservationView>createFrom().failure(new SlotUnavailableException(f.getMessage(), alts)))
                        )
                )
        );
    }

    private Uni<List<Instant>> alternatives(UUID tenantId, FacilityId facilityId, PartySize party, Instant startsAt,
                                            BookingRules rules, List<BookableResource> resList) {
        final Instant now = clock.now();
        final var day = startsAt.atZone(rules.zone()).toLocalDate();
        return ledgers.findOrCreate(tenantId, facilityId, day, now).map(ledger ->
                ledger.availableStarts(party, rules, resList, now).stream()
                        .sorted(Comparator.comparingLong((Instant s) -> Math.abs(s.getEpochSecond() - startsAt.getEpochSecond())))
                        .limit(3).sorted().toList()
        );
    }

    private Uni<ReservationView> doBook(UUID tenantId, FacilityId facilityId, ReservationId id, CustomerContact customer,
                                        PartySize party, Instant startsAt, BookingSource source, String notes,
                                        Set<String> requirements, BookingRules rules, List<BookableResource> resList) {
        return doBook(tenantId, facilityId, id, customer, party, startsAt, source, notes, requirements,
                rules.turnTimeFor(party), Set.of(), rules, resList);
    }

    private Uni<ReservationView> doBook(UUID tenantId, FacilityId facilityId, ReservationId id, CustomerContact customer,
                                        PartySize party, Instant startsAt, BookingSource source, String notes,
                                        Set<String> requirements, java.time.Duration requestedDuration,
                                        Set<String> requiredResourceTags, BookingRules rules, List<BookableResource> resList) {
        final var day = startsAt.atZone(rules.zone()).toLocalDate();
        final var now = clock.now();
        return ledgers.findOrCreate(tenantId, facilityId, day, now).flatMap(ledger -> {
            if (!ledger.canAccommodate(party, startsAt, requestedDuration, requiredResourceTags, rules, resList)) {
                throw new NoAvailabilityException("No resource matching capacity and required capabilities at " + startsAt);
            }
            final var res = Reservation.request(id, facilityId, customer, party, startsAt, source, notes, requirements,
                    requestedDuration, rules, now);
            final var alloc = ledger.allocate(id, party, startsAt, requestedDuration, requiredResourceTags, rules, resList, now);
            res.assignResources(alloc.resourceIds(), now);

            return reservations.save(tenantId, res)
                    .flatMap(x -> ledgers.save(tenantId, ledger))
                    .flatMap(x -> publishEvents(tenantId, res, ledger))
                    .map(x -> ReservationView.of(res));
        });
    }

    public Uni<ReservationView> book(UUID tenantId, UUID facilityId, CustomerContact customer, int partySize,
                                     Instant startsAt, BookingSource source, String notes, Set<String> requirements) {
        final var fId = FacilityId.of(facilityId);
        final var party = PartySize.of(partySize);
        final var id = ReservationId.generate();

        return rulesPort.rulesFor(tenantId, fId).flatMap(rules ->
                resourceDirectory.resourcesFor(tenantId, fId).flatMap(resList ->
                        ConflictRetry.run(MAX_ATTEMPTS, () ->
                                uow.execute(() -> doBook(tenantId, fId, id, customer, party, startsAt, source, notes, requirements, rules, resList))
                        ).onFailure(NoAvailabilityException.class).recoverWithUni(f ->
                                alternatives(tenantId, fId, party, startsAt, rules, resList).flatMap(alts ->
                                        Uni.<ReservationView>createFrom().failure(new SlotUnavailableException(f.getMessage(), alts)))
                        )
                )
        );
    }

    /**
     * Books a domain-specific service using its own duration and required resource capabilities.
     * Allocation remains authoritative inside the existing unit-of-work boundary.
     */
    public Uni<ReservationView> bookWithConstraints(UUID tenantId, UUID facilityId, CustomerContact customer,
                                                   int partySize, Instant startsAt, java.time.Duration serviceDuration,
                                                   Set<String> requiredResourceTags, BookingSource source,
                                                   String notes, Set<String> requirements) {
        return bookWithConstraints(tenantId, facilityId, customer, partySize, startsAt, serviceDuration,
                requiredResourceTags, source, notes, requirements, null);
    }

    /**
     * Books a domain-specific service with an optional tenant-scoped idempotency key.
     * Repositories used with a key must implement the atomic idempotency contract.
     */
    public Uni<ReservationView> bookWithConstraints(UUID tenantId, UUID facilityId, CustomerContact customer,
                                                   int partySize, Instant startsAt, Duration serviceDuration,
                                                   Set<String> requiredResourceTags, BookingSource source,
                                                   String notes, Set<String> requirements, String idempotencyKey) {
        Objects.requireNonNull(serviceDuration, "serviceDuration cannot be null");
        if (serviceDuration.isZero() || serviceDuration.isNegative()) {
            return Uni.createFrom().failure(new IllegalArgumentException("serviceDuration must be positive"));
        }
        final var fId = FacilityId.of(facilityId);
        final var party = PartySize.of(partySize);
        final var id = ReservationId.generate();
        final var tags = requiredResourceTags == null ? Set.<String>of() : Set.copyOf(requiredResourceTags);
        final var normalizedSource = source == null ? BookingSource.WEB : source;
        final var normalizedRequirements = requirements == null ? Set.<String>of() : Set.copyOf(requirements);
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("idempotencyKey cannot be blank"));
        }
        if (idempotencyKey != null && !(reservations instanceof IdempotentReservationRepository)) {
            return Uni.createFrom().failure(new IllegalStateException(
                    "Idempotency key supplied but reservation repository does not support atomic idempotency"));
        }
        return rulesPort.rulesFor(tenantId, fId).flatMap(rules ->
                resourceDirectory.resourcesFor(tenantId, fId).flatMap(resList ->
                        ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> {
                            if (idempotencyKey == null) {
                                return doBook(tenantId, fId, id, customer, party, startsAt, normalizedSource, notes,
                                        normalizedRequirements, serviceDuration, tags, rules, resList);
                            }
                            return doBookIdempotently(tenantId, fId, id, customer, party, startsAt, normalizedSource,
                                    notes, normalizedRequirements, serviceDuration, tags, idempotencyKey, rules, resList);
                        })).onFailure(NoAvailabilityException.class).recoverWithUni(f ->
                                Uni.<ReservationView>createFrom().failure(new SlotUnavailableException(f.getMessage(), List.of()))
                        )
                )
        );
    }

    private Uni<ReservationView> doBookIdempotently(UUID tenantId, FacilityId facilityId, ReservationId id,
                                                     CustomerContact customer, PartySize party, Instant startsAt,
                                                     BookingSource source, String notes, Set<String> requirements,
                                                     Duration requestedDuration, Set<String> requiredResourceTags,
                                                     String idempotencyKey, BookingRules rules,
                                                     List<BookableResource> resList) {
        var repository = (IdempotentReservationRepository) reservations;
        final String fingerprint = ReservationRequestFingerprint.of(
                new ReservationRequestFingerprint.CreateReservationCommand(
                        tenantId.toString(), facilityId.value().toString(), customer, party.value(), startsAt,
                        requestedDuration, source, notes, requirements, requiredResourceTags));
        return repository.findByIdempotencyKey(tenantId, idempotencyKey).flatMap(existing -> {
            if (existing.isPresent()) {
                // The atomic repository compares fingerprints and returns the canonical reservation.
                return repository.saveIdempotently(tenantId, idempotencyKey, fingerprint, existing.get())
                        .map(result -> ReservationView.of(result.reservation()));
            }
            final var day = startsAt.atZone(rules.zone()).toLocalDate();
            final var now = clock.now();
            return ledgers.findOrCreate(tenantId, facilityId, day, now).flatMap(ledger -> {
                if (!ledger.canAccommodate(party, startsAt, requestedDuration, requiredResourceTags, rules, resList)) {
                    throw new NoAvailabilityException("No resource matching capacity and required capabilities at " + startsAt);
                }
                final var candidate = Reservation.request(id, facilityId, customer, party, startsAt, source, notes,
                        requirements, requestedDuration, rules, now);
                final var allocation = ledger.allocate(id, party, startsAt, requestedDuration, requiredResourceTags,
                        rules, resList, now);
                candidate.assignResources(allocation.resourceIds(), now);
                return repository.saveIdempotently(tenantId, idempotencyKey, fingerprint, candidate).flatMap(result -> {
                    if (!result.created()) {
                        // A concurrent request won this key; discard this attempt's unpersisted allocation/events.
                        return Uni.createFrom().item(ReservationView.of(result.reservation()));
                    }
                    return ledgers.save(tenantId, ledger)
                            .flatMap(ignored -> publishEvents(tenantId, result.reservation(), ledger))
                            .map(ignored -> ReservationView.of(result.reservation()));
                });
            });
        });
    }

    public Uni<Void> confirmDeposit(UUID tenantId, UUID reservationId, String paymentRef) {
        return change(tenantId, reservationId, (r, rules) -> {
            r.confirmDeposit(paymentRef, clock.now());
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<Void> checkIn(UUID tenantId, UUID reservationId) {
        return change(tenantId, reservationId, (r, rules) -> {
            r.checkIn(clock.now(), rules);
            return Uni.createFrom().voidItem();
        });
    }

    // Compatibility method for dining contexts
    public Uni<Void> seat(UUID tenantId, UUID reservationId) {
        return checkIn(tenantId, reservationId);
    }

    public Uni<Void> complete(UUID tenantId, UUID reservationId) {
        return changeWithLedger(tenantId, reservationId, (r, ledger, rules) -> {
            final var now = clock.now();
            r.complete(now);
            ledger.release(r.id(), now);
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<CancellationOutcome> cancel(UUID tenantId, UUID reservationId, String reason, boolean waivePenalty) {
        final var outcomeHolder = new CancellationOutcome[1];
        return changeWithLedger(tenantId, reservationId, (r, ledger, rules) -> {
            final var now = clock.now();
            outcomeHolder[0] = r.cancel(reason, now, rules, waivePenalty);
            ledger.release(r.id(), now);
            return Uni.createFrom().voidItem();
        }).map(x -> outcomeHolder[0]);
    }

    public Uni<CancellationOutcome> markNoShow(UUID tenantId, UUID reservationId, boolean waivePenalty) {
        final var outcomeHolder = new CancellationOutcome[1];
        return changeWithLedger(tenantId, reservationId, (r, ledger, rules) -> {
            final var now = clock.now();
            outcomeHolder[0] = r.markNoShow(now, rules, waivePenalty);
            ledger.release(r.id(), now);
            return Uni.createFrom().voidItem();
        }).map(x -> outcomeHolder[0]);
    }

    public Uni<Void> reschedule(UUID tenantId, UUID reservationId, Instant newStart, int newPartySize) {
        final var newParty = PartySize.of(newPartySize);
        return ConflictRetry.run(MAX_ATTEMPTS, () ->
                load(tenantId, reservationId).flatMap(r ->
                        rulesPort.rulesFor(tenantId, r.facilityId()).flatMap(rules ->
                                resourceDirectory.resourcesFor(tenantId, r.facilityId()).flatMap(resList -> {
                                    final var oldDay = r.startsAt().atZone(rules.zone()).toLocalDate();
                                    final var newDay = newStart.atZone(rules.zone()).toLocalDate();
                                    final var now = clock.now();

                                    return ledgers.findOrCreate(tenantId, r.facilityId(), oldDay, now).flatMap(oldLedger ->
                                            ledgers.findOrCreate(tenantId, r.facilityId(), newDay, now).flatMap(newLedger -> {
                                                if (oldDay.equals(newDay)) {
                                                    oldLedger.release(r.id(), now);
                                                    if (!oldLedger.canAccommodate(newParty, newStart, rules, resList)) {
                                                        return Uni.createFrom().failure(new SlotUnavailableException(newPartySize, newStart));
                                                    }
                                                    r.reschedule(newStart, newParty, rules, now);
                                                    final var alloc = oldLedger.allocate(r.id(), newParty, newStart, rules, resList, now);
                                                    r.assignResources(alloc.resourceIds(), now);

                                                    return uow.execute(() ->
                                                            reservations.save(tenantId, r)
                                                                    .flatMap(x -> ledgers.save(tenantId, oldLedger))
                                                                    .flatMap(x -> publishEvents(tenantId, r, oldLedger))
                                                    );
                                                } else {
                                                    if (!newLedger.canAccommodate(newParty, newStart, rules, resList)) {
                                                        return Uni.createFrom().failure(new SlotUnavailableException(newPartySize, newStart));
                                                    }
                                                    oldLedger.release(r.id(), now);
                                                    r.reschedule(newStart, newParty, rules, now);
                                                    final var alloc = newLedger.allocate(r.id(), newParty, newStart, rules, resList, now);
                                                    r.assignResources(alloc.resourceIds(), now);

                                                    return uow.execute(() ->
                                                            reservations.save(tenantId, r)
                                                                    .flatMap(x -> ledgers.save(tenantId, oldLedger))
                                                                    .flatMap(x -> ledgers.save(tenantId, newLedger))
                                                                    .flatMap(x -> publishEvents(tenantId, r, oldLedger, newLedger))
                                                    );
                                                }
                                            })
                                    );
                                })
                        )
                )
        );
    }

    public Uni<List<Instant>> availableSlots(UUID tenantId, UUID facilityId, LocalDate day, int partySize) {
        final var fId = FacilityId.of(facilityId);
        final var party = PartySize.of(partySize);
        final var now = clock.now();

        return rulesPort.rulesFor(tenantId, fId).flatMap(rules ->
                resourceDirectory.resourcesFor(tenantId, fId).flatMap(resList ->
                        ledgers.findOrCreate(tenantId, fId, day, now).map(ledger -> {
                            final var out = new ArrayList<Instant>();
                            final var localDate = day;
                            for (OperatingWindow window : rules.periodsOn(localDate.getDayOfWeek())) {
                                LocalTime cursor = window.opens();
                                while (!cursor.isAfter(window.lastBooking())) {
                                    final var slot = localDate.atTime(cursor).atZone(rules.zone()).toInstant();
                                    if (!slot.isBefore(now.plus(rules.minLeadTime()))
                                            && ledger.canAccommodate(party, slot, rules, resList)) {
                                        out.add(slot);
                                    }
                                    cursor = cursor.plusMinutes(15);
                                }
                            }
                            return out;
                        })
                )
        );
    }

    public Uni<ReservationView> booking(UUID tenantId, UUID reservationId) {
        return load(tenantId, reservationId).map(ReservationView::of);
    }

    public Uni<List<ReservationView>> upcoming(UUID tenantId, UUID facilityId, Instant from, Instant to) {
        return reservations.findStartingBetween(tenantId, FacilityId.of(facilityId), from, to)
                .map(list -> list.stream().map(ReservationView::of).toList());
    }

    // ---- Helpers ----

    private Uni<Reservation> load(UUID tenantId, UUID reservationId) {
        return reservations.find(tenantId, ReservationId.of(reservationId))
                .map(opt -> opt.orElseThrow(() -> new NoSuchElementException("Reservation not found: " + reservationId)));
    }

    private Uni<Void> change(UUID tenantId, UUID reservationId, java.util.function.BiFunction<Reservation, BookingRules, Uni<Void>> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () ->
                load(tenantId, reservationId).flatMap(r ->
                        rulesPort.rulesFor(tenantId, r.facilityId()).flatMap(rules ->
                                action.apply(r, rules).flatMap(x ->
                                        uow.execute(() ->
                                                reservations.save(tenantId, r)
                                                        .flatMap(y -> publishEvents(tenantId, r))
                                        )
                                )
                        )
                )
        );
    }

    private <T> Uni<T> changeWithLedger(UUID tenantId, UUID reservationId,
                                       TriFunction<Reservation, AvailabilityLedger, BookingRules, Uni<T>> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () ->
                load(tenantId, reservationId).flatMap(r ->
                        rulesPort.rulesFor(tenantId, r.facilityId()).flatMap(rules -> {
                            final var day = r.startsAt().atZone(rules.zone()).toLocalDate();
                            final var now = clock.now();
                            return ledgers.findOrCreate(tenantId, r.facilityId(), day, now).flatMap(ledger ->
                                    action.apply(r, ledger, rules).flatMap(result ->
                                            uow.execute(() ->
                                                    reservations.save(tenantId, r)
                                                            .flatMap(x -> ledgers.save(tenantId, ledger))
                                                            .flatMap(x -> publishEvents(tenantId, r, ledger))
                                                            .map(x -> result)
                                            )
                                    )
                            );
                        })
                )
        );
    }

    private Uni<Void> publishEvents(UUID tenantId, ReservationAggregate<?>... aggregates) {
        final var allEvents = new ArrayList<DomainEvent>();
        for (ReservationAggregate<?> agg : aggregates) {
            allEvents.addAll(agg.pullDomainEvents());
        }
        return allEvents.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, allEvents);
    }

    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}
