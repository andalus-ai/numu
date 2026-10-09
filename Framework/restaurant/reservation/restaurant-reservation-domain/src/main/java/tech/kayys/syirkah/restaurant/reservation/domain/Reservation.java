package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.GuestContact;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;
import tech.kayys.syirkah.restaurant.kernel.domain.ReservationId;
import tech.kayys.syirkah.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationCancelled;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationCompleted;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationConfirmed;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationNoShow;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationRequested;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationRescheduled;
import tech.kayys.syirkah.restaurant.reservation.domain.event.ReservationSeated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A booking. Domain events carry ids and counts only - never the guest's contact details - because events
 * leave the service (message broker, logs).
 */
public final class Reservation extends RestaurantAggregate<ReservationId> {

    private static final long serialVersionUID = 1L;

    public record Snapshot(UUID id, UUID venueId, String guestName, String guestPhone, String guestEmail, int partySize,
                           Instant startsAt, long turnSeconds, String status, String source, String notes,
                           Set<String> dietary, Set<UUID> tableIds, BigDecimal depositAmount, String depositCurrency,
                           boolean depositPaid, String depositRef, Instant seatedAt, Instant endedAt, String cancelReason,
                           Instant createdAt, Instant updatedAt, int version) { }

    private final VenueId venueId;
    private final GuestContact guest;
    private PartySize party;
    private Instant startsAt;
    private java.time.Duration turn;
    private ReservationStatus status;
    private final BookingSource source;
    private String notes;
    private final Set<String> dietary;
    private final Set<DiningTableId> tableIds = new LinkedHashSet<>();
    private Money deposit;            // null when no deposit is required
    private boolean depositPaid;
    private String depositRef;
    private Instant seatedAt;
    private Instant endedAt;
    private String cancelReason;

    private Reservation(ReservationId id, VenueId venueId, GuestContact guest, PartySize party, Instant startsAt,
                        java.time.Duration turn, BookingSource source, String notes, Set<String> dietary, Instant now) {
        super(id, now);
        this.venueId = Objects.requireNonNull(venueId, "venueId cannot be null");
        this.guest = Objects.requireNonNull(guest, "guest cannot be null");
        this.party = Objects.requireNonNull(party, "party cannot be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt cannot be null");
        this.turn = Objects.requireNonNull(turn, "turn cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.notes = notes == null ? "" : notes;
        this.dietary = dietary == null ? new TreeSet<>() : new TreeSet<>(dietary);
    }

    /** Validates against the venue's rules and creates a CONFIRMED (or PENDING_DEPOSIT) booking. */
    public static Reservation request(ReservationId id, VenueId venueId, GuestContact guest, PartySize party,
                                      Instant startsAt, BookingSource source, String notes, Set<String> dietary,
                                      BookingRules rules, Instant now) {
        validateWindow(party, startsAt, rules, now);
        final var r = new Reservation(id, venueId, guest, party, startsAt, rules.turnTimeFor(party), source, notes, dietary, now);
        if (rules.requiresDeposit(party)) {
            r.deposit = rules.depositFor(party);
            r.status = ReservationStatus.PENDING_DEPOSIT;
        } else {
            r.status = ReservationStatus.CONFIRMED;
        }
        r.raise(ReservationRequested.at(now, id.value(), venueId.value(), party.value(), startsAt, r.status.name()));
        if (r.status == ReservationStatus.CONFIRMED) {
            r.raise(ReservationConfirmed.at(now, id.value(), venueId.value(), startsAt));
        }
        return r;
    }

    private static void validateWindow(PartySize party, Instant startsAt, BookingRules rules, Instant now) {
        if (party.value() < rules.minParty() || party.value() > rules.maxParty()) {
            throw new BusinessRuleViolation("Party size must be between " + rules.minParty() + " and " + rules.maxParty());
        }
        if (startsAt.isBefore(now.plus(rules.minLeadTime()))) {
            throw new BusinessRuleViolation("Bookings need at least " + rules.minLeadTime().toMinutes() + " minutes notice");
        }
        if (startsAt.isAfter(now.plus(java.time.Duration.ofDays(rules.maxAdvanceDays())))) {
            throw new BusinessRuleViolation("Bookings open at most " + rules.maxAdvanceDays() + " days ahead");
        }
        final var local = startsAt.atZone(rules.zone());
        final boolean inService = rules.periodsOn(local.getDayOfWeek()).stream()
                .anyMatch(p -> !local.toLocalTime().isBefore(p.opens()) && !local.toLocalTime().isAfter(p.lastSeating()));
        if (!inService) {
            throw new BusinessRuleViolation("The restaurant does not take bookings at that time");
        }
    }

    // ---- lifecycle ----

    public void confirmDeposit(String paymentRef, Instant now) {
        if (status != ReservationStatus.PENDING_DEPOSIT) {
            throw new InvalidStateException("Only a reservation awaiting its deposit can be confirmed, was " + status);
        }
        this.depositPaid = true;
        this.depositRef = Objects.requireNonNull(paymentRef, "paymentRef cannot be null");
        this.status = ReservationStatus.CONFIRMED;
        touch(now);
        raise(ReservationConfirmed.at(now, id().value(), venueId.value(), startsAt));
    }

    public void seat(Instant now, BookingRules rules) {
        if (status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only a confirmed reservation can be seated, was " + status);
        }
        if (tableIds.isEmpty()) {
            throw new InvalidStateException("No table assigned to this reservation");
        }
        if (now.isBefore(startsAt.minus(rules.earlySeatingWindow()))) {
            throw new BusinessRuleViolation("Too early to seat this party");
        }
        this.status = ReservationStatus.SEATED;
        this.seatedAt = now;
        touch(now);
        raise(ReservationSeated.at(now, id().value(), venueId.value(), tableIds.stream().map(DiningTableId::value).sorted().toList()));
    }

    public void complete(Instant now) {
        if (status != ReservationStatus.SEATED) {
            throw new InvalidStateException("Only a seated reservation can be completed, was " + status);
        }
        this.status = ReservationStatus.COMPLETED;
        this.endedAt = now;
        touch(now);
        raise(ReservationCompleted.at(now, id().value(), venueId.value()));
    }

    /** Cancels and reports what it costs. A manager waiving fees is a separate, audited application decision. */
    public CancellationOutcome cancel(String reason, Instant now, BookingRules rules) {
        return cancel(reason, now, rules, false);
    }

    /** @param waivePenalty a manager decision (audited by the caller): no fee, deposit refunded */
    public CancellationOutcome cancel(String reason, Instant now, BookingRules rules, boolean waivePenalty) {
        if (status != ReservationStatus.PENDING_DEPOSIT && status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only an upcoming reservation can be cancelled, was " + status);
        }
        final boolean late = now.isAfter(startsAt.minus(rules.freeCancellationBefore()));
        final var outcome = late && !waivePenalty ? penalty(rules) : freeCancellation();
        this.status = ReservationStatus.CANCELLED;
        this.cancelReason = reason == null ? "" : reason;
        this.endedAt = now;
        touch(now);
        raise(ReservationCancelled.at(now, id().value(), venueId.value(), outcome.charge().name(),
                outcome.chargeAmount().amount(), outcome.chargeAmount().currency().code()));
        return outcome;
    }

    public CancellationOutcome markNoShow(Instant now, BookingRules rules) {
        return markNoShow(now, rules, false);
    }

    /** @param waivePenalty a manager decision (audited by the caller): no fee, deposit refunded */
    public CancellationOutcome markNoShow(Instant now, BookingRules rules, boolean waivePenalty) {
        if (status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only a confirmed reservation can be a no-show, was " + status);
        }
        if (now.isBefore(startsAt.plus(rules.noShowGrace()))) {
            throw new BusinessRuleViolation("The grace period has not passed yet");
        }
        final var outcome = waivePenalty ? freeCancellation() : penalty(rules);
        this.status = ReservationStatus.NO_SHOW;
        this.endedAt = now;
        touch(now);
        raise(ReservationNoShow.at(now, id().value(), venueId.value(), outcome.charge().name(),
                outcome.chargeAmount().amount(), outcome.chargeAmount().currency().code()));
        return outcome;
    }

    /**
     * Moves the booking. Tables are cleared: the caller must allocate again in the (possibly different day's)
     * ledger. A deposit already paid is kept; a party that newly crosses the deposit threshold goes back to
     * PENDING_DEPOSIT.
     */
    public void reschedule(Instant newStart, PartySize newParty, BookingRules rules, Instant now) {
        if (status != ReservationStatus.PENDING_DEPOSIT && status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only an upcoming reservation can be rescheduled, was " + status);
        }
        validateWindow(newParty, newStart, rules, now);
        this.startsAt = newStart;
        this.party = newParty;
        this.turn = rules.turnTimeFor(newParty);
        this.tableIds.clear();
        if (rules.requiresDeposit(newParty) && !depositPaid) {
            this.deposit = rules.depositFor(newParty);
            this.status = ReservationStatus.PENDING_DEPOSIT;
        }
        touch(now);
        raise(ReservationRescheduled.at(now, id().value(), venueId.value(), newStart, newParty.value()));
    }

    /** Records which tables the ledger allocated. */
    public void assignTables(Set<DiningTableId> tables, Instant now) {
        if (!status.isActive()) {
            throw new InvalidStateException("Cannot assign tables to a " + status + " reservation");
        }
        this.tableIds.clear();
        this.tableIds.addAll(tables);
        touch(now);
    }

    private CancellationOutcome freeCancellation() {
        final var zero = zero();
        return new CancellationOutcome(CancellationOutcome.Charge.NONE, zero, depositPaid ? deposit : zero);
    }

    private CancellationOutcome penalty(BookingRules rules) {
        if (depositPaid) {
            return new CancellationOutcome(CancellationOutcome.Charge.DEPOSIT_FORFEITED, deposit, zero());
        }
        final var fee = rules.lateFeeFor(party);
        return fee.amount().signum() > 0
                ? new CancellationOutcome(CancellationOutcome.Charge.LATE_FEE, fee, zero())
                : new CancellationOutcome(CancellationOutcome.Charge.NONE, zero(), zero());
    }

    private Money zero() {
        return Money.zero(deposit != null ? deposit.currency() : tech.kayys.syirkah.foundation.domain.valueobject.Currency.IDR());
    }

    // ---- accessors ----

    public VenueId venueId() { return venueId; }
    public GuestContact guest() { return guest; }
    public PartySize party() { return party; }
    public Instant startsAt() { return startsAt; }
    public java.time.Duration turn() { return turn; }
    public ReservationStatus status() { return status; }
    public BookingSource source() { return source; }
    public String notes() { return notes; }
    public Set<String> dietary() { return Collections.unmodifiableSet(dietary); }
    public Set<DiningTableId> tableIds() { return Collections.unmodifiableSet(tableIds); }
    public Money deposit() { return deposit; }
    public boolean depositPaid() { return depositPaid; }
    public Instant seatedAt() { return seatedAt; }

    // ---- persistence ----

    public Snapshot snapshot() {
        return new Snapshot(id().value(), venueId.value(), guest.name(), guest.phone(), guest.email(), party.value(),
                startsAt, turn.getSeconds(), status.name(), source.name(), notes, new TreeSet<>(dietary),
                tableIds.stream().map(DiningTableId::value).collect(Collectors.toCollection(TreeSet::new)),
                deposit == null ? null : deposit.amount(), deposit == null ? null : deposit.currency().code(),
                depositPaid, depositRef, seatedAt, endedAt, cancelReason, createdAt, updatedAt, version);
    }

    public static Reservation restore(Snapshot s) {
        final var r = new Reservation(ReservationId.of(s.id()), VenueId.of(s.venueId()),
                new GuestContact(s.guestName(), s.guestPhone(), s.guestEmail()), PartySize.of(s.partySize()),
                s.startsAt(), java.time.Duration.ofSeconds(s.turnSeconds()), BookingSource.valueOf(s.source()),
                s.notes(), s.dietary(), s.createdAt());
        r.status = ReservationStatus.valueOf(s.status());
        s.tableIds().forEach(t -> r.tableIds.add(DiningTableId.of(t)));
        r.deposit = s.depositAmount() == null ? null : Money.of(s.depositAmount(), s.depositCurrency());
        r.depositPaid = s.depositPaid();
        r.depositRef = s.depositRef();
        r.seatedAt = s.seatedAt();
        r.endedAt = s.endedAt();
        r.cancelReason = s.cancelReason();
        r.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return r;
    }
}
