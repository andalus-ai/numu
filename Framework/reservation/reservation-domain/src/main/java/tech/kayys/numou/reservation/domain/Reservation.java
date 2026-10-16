package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.reservation.domain.event.ReservationCancelled;
import tech.kayys.syirkah.reservation.domain.event.ReservationCheckedIn;
import tech.kayys.syirkah.reservation.domain.event.ReservationCompleted;
import tech.kayys.syirkah.reservation.domain.event.ReservationConfirmed;
import tech.kayys.syirkah.reservation.domain.event.ReservationNoShow;
import tech.kayys.syirkah.reservation.domain.event.ReservationRequested;
import tech.kayys.syirkah.reservation.domain.event.ReservationRescheduled;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A generic reservation / booking aggregate.
 * Domain events carry identifiers and metadata only, protecting personal contact details from event streams.
 */
public final class Reservation extends ReservationAggregate<ReservationId> {

    private static final long serialVersionUID = 1L;

    public record Snapshot(
            UUID id,
            UUID facilityId,
            String customerName,
            String customerPhone,
            String customerEmail,
            int partySize,
            Instant startsAt,
            long durationSeconds,
            String status,
            String source,
            String notes,
            Set<String> requirements,
            Set<UUID> resourceIds,
            BigDecimal depositAmount,
            String depositCurrency,
            boolean depositPaid,
            String depositRef,
            Instant checkedInAt,
            Instant endedAt,
            String cancelReason,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) { }

    private final FacilityId facilityId;
    private final CustomerContact customer;
    private PartySize party;
    private Instant startsAt;
    private Duration duration;
    private ReservationStatus status;
    private final BookingSource source;
    private String notes;
    private final Set<String> requirements;
    private final Set<ResourceId> resourceIds = new LinkedHashSet<>();
    private Money deposit;
    private boolean depositPaid;
    private String depositRef;
    private Instant checkedInAt;
    private Instant endedAt;
    private String cancelReason;

    private Reservation(ReservationId id, FacilityId facilityId, CustomerContact customer, PartySize party, Instant startsAt,
                        Duration duration, BookingSource source, String notes, Set<String> requirements, Instant now) {
        super(id, now);
        this.facilityId = Objects.requireNonNull(facilityId, "facilityId cannot be null");
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.party = Objects.requireNonNull(party, "party cannot be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt cannot be null");
        this.duration = Objects.requireNonNull(duration, "duration cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.notes = notes == null ? "" : notes;
        this.requirements = requirements == null ? new TreeSet<>() : new TreeSet<>(requirements);
    }

    public static Reservation request(ReservationId id, FacilityId facilityId, CustomerContact customer, PartySize party,
                                      Instant startsAt, BookingSource source, String notes, Set<String> requirements,
                                      BookingRules rules, Instant now) {
        validateWindow(party, startsAt, rules, now);
        final var r = new Reservation(id, facilityId, customer, party, startsAt, rules.turnTimeFor(party), source, notes, requirements, now);
        if (rules.requiresDeposit(party)) {
            r.deposit = rules.depositFor(party);
            r.status = ReservationStatus.PENDING_DEPOSIT;
        } else {
            r.status = ReservationStatus.CONFIRMED;
        }
        r.raise(ReservationRequested.at(now, id.value(), facilityId.value(), party.value(), startsAt, r.status.name()));
        if (r.status == ReservationStatus.CONFIRMED) {
            r.raise(ReservationConfirmed.at(now, id.value(), facilityId.value(), startsAt));
        }
        return r;
    }

    public static Reservation request(ReservationId id, FacilityId facilityId, CustomerContact customer, PartySize party,
                                      Instant startsAt, BookingSource source, String notes, Set<String> requirements,
                                      Duration turnTime, BookingRules rules, Instant now) {
        validateWindow(party, startsAt, rules, now);
        final var r = new Reservation(id, facilityId, customer, party, startsAt, turnTime, source, notes, requirements, now);
        if (rules.requiresDeposit(party)) {
            r.deposit = rules.depositFor(party);
            r.status = ReservationStatus.PENDING_DEPOSIT;
        } else {
            r.status = ReservationStatus.CONFIRMED;
        }
        r.raise(ReservationRequested.at(now, id.value(), facilityId.value(), party.value(), startsAt, r.status.name()));
        if (r.status == ReservationStatus.CONFIRMED) {
            r.raise(ReservationConfirmed.at(now, id.value(), facilityId.value(), startsAt));
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
        if (startsAt.isAfter(now.plus(Duration.ofDays(rules.maxAdvanceDays())))) {
            throw new BusinessRuleViolation("Bookings open at most " + rules.maxAdvanceDays() + " days ahead");
        }
        final var local = startsAt.atZone(rules.zone());
        final boolean inService = rules.periodsOn(local.getDayOfWeek()).stream()
                .anyMatch(p -> !local.toLocalTime().isBefore(p.opens()) && !local.toLocalTime().isAfter(p.lastBooking()));
        if (!inService) {
            throw new BusinessRuleViolation("The facility does not accept bookings at that time");
        }
    }

    // ---- Lifecycle ----

    public void confirmDeposit(String paymentRef, Instant now) {
        if (status != ReservationStatus.PENDING_DEPOSIT) {
            throw new InvalidStateException("Only a reservation awaiting its deposit can be confirmed, was " + status);
        }
        this.depositPaid = true;
        this.depositRef = Objects.requireNonNull(paymentRef, "paymentRef cannot be null");
        this.status = ReservationStatus.CONFIRMED;
        touch(now);
        raise(ReservationConfirmed.at(now, id().value(), facilityId.value(), startsAt));
    }

    public void checkIn(Instant now, BookingRules rules) {
        if (status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only a confirmed reservation can be checked in, was " + status);
        }
        if (resourceIds.isEmpty()) {
            throw new InvalidStateException("No resource assigned to this reservation");
        }
        if (now.isBefore(startsAt.minus(rules.earlyArrivalWindow()))) {
            throw new BusinessRuleViolation("Too early to check in");
        }
        this.status = ReservationStatus.CHECKED_IN;
        this.checkedInAt = now;
        touch(now);
        raise(ReservationCheckedIn.at(now, id().value(), facilityId.value(), resourceIds.stream().map(ResourceId::value).sorted().toList()));
    }

    // Compatibility method for restaurant contexts
    public void seat(Instant now, BookingRules rules) {
        checkIn(now, rules);
    }

    public void complete(Instant now) {
        if (!status.isAdmitted()) {
            throw new InvalidStateException("Only an admitted / checked-in reservation can be completed, was " + status);
        }
        this.status = ReservationStatus.COMPLETED;
        this.endedAt = now;
        touch(now);
        raise(ReservationCompleted.at(now, id().value(), facilityId.value()));
    }

    public CancellationOutcome cancel(String reason, Instant now, BookingRules rules) {
        return cancel(reason, now, rules, false);
    }

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
        raise(ReservationCancelled.at(now, id().value(), facilityId.value(), outcome.charge().name(),
                outcome.chargeAmount().amount(), outcome.chargeAmount().currency().code()));
        return outcome;
    }

    public CancellationOutcome markNoShow(Instant now, BookingRules rules) {
        return markNoShow(now, rules, false);
    }

    public CancellationOutcome markNoShow(Instant now, BookingRules rules, boolean waivePenalty) {
        if (status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only a confirmed reservation can be marked as no-show, was " + status);
        }
        if (now.isBefore(startsAt.plus(rules.noShowGrace()))) {
            throw new BusinessRuleViolation("The grace period has not passed yet");
        }
        final var outcome = waivePenalty ? freeCancellation() : penalty(rules);
        this.status = ReservationStatus.NO_SHOW;
        this.endedAt = now;
        touch(now);
        raise(ReservationNoShow.at(now, id().value(), facilityId.value(), outcome.charge().name(),
                outcome.chargeAmount().amount(), outcome.chargeAmount().currency().code()));
        return outcome;
    }

    public void reschedule(Instant newStart, PartySize newParty, BookingRules rules, Instant now) {
        if (status != ReservationStatus.PENDING_DEPOSIT && status != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("Only an upcoming reservation can be rescheduled, was " + status);
        }
        validateWindow(newParty, newStart, rules, now);
        this.startsAt = newStart;
        this.party = newParty;
        this.duration = rules.turnTimeFor(newParty);
        this.resourceIds.clear();
        if (rules.requiresDeposit(newParty) && !depositPaid) {
            this.deposit = rules.depositFor(newParty);
            this.status = ReservationStatus.PENDING_DEPOSIT;
        }
        touch(now);
        raise(ReservationRescheduled.at(now, id().value(), facilityId.value(), newStart, newParty.value()));
    }

    public void assignResources(Set<ResourceId> resources, Instant now) {
        if (!status.isActive()) {
            throw new InvalidStateException("Cannot assign resources to a " + status + " reservation");
        }
        this.resourceIds.clear();
        this.resourceIds.addAll(resources);
        touch(now);
    }

    // Compatibility method for restaurant contexts
    public void assignTables(Set<tech.kayys.syirkah.reservation.domain.ResourceId> tables, Instant now) {
        assignResources(tables, now);
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

    // ---- Accessors ----

    public FacilityId facilityId() { return facilityId; }
    public CustomerContact customer() { return customer; }
    public PartySize party() { return party; }
    public Instant startsAt() { return startsAt; }
    public Duration duration() { return duration; }
    public ReservationStatus status() { return status; }
    public BookingSource source() { return source; }
    public String notes() { return notes; }
    public Set<String> requirements() { return Collections.unmodifiableSet(requirements); }
    public Set<ResourceId> resourceIds() { return Collections.unmodifiableSet(resourceIds); }
    public Money deposit() { return deposit; }
    public boolean depositPaid() { return depositPaid; }
    public Instant checkedInAt() { return checkedInAt; }

    // Compatibility accessors for restaurant contexts
    public FacilityId venueId() { return facilityId; }
    public CustomerContact guest() { return customer; }
    public Duration turn() { return duration; }
    public Set<String> dietary() { return requirements(); }
    public Set<ResourceId> tableIds() { return resourceIds(); }
    public Instant seatedAt() { return checkedInAt(); }

    // ---- Snapshot & Restore ----

    public Snapshot snapshot() {
        return new Snapshot(
                id().value(),
                facilityId.value(),
                customer.name(),
                customer.phone(),
                customer.email(),
                party.value(),
                startsAt,
                duration.getSeconds(),
                status.name(),
                source.name(),
                notes,
                new TreeSet<>(requirements),
                resourceIds.stream().map(ResourceId::value).collect(Collectors.toCollection(TreeSet::new)),
                deposit == null ? null : deposit.amount(),
                deposit == null ? null : deposit.currency().code(),
                depositPaid,
                depositRef,
                checkedInAt,
                endedAt,
                cancelReason,
                createdAt,
                updatedAt,
                version
        );
    }

    public static Reservation restore(Snapshot s) {
        final var r = new Reservation(
                ReservationId.of(s.id()),
                FacilityId.of(s.facilityId()),
                new CustomerContact(s.customerName(), s.customerPhone(), s.customerEmail()),
                PartySize.of(s.partySize()),
                s.startsAt(),
                Duration.ofSeconds(s.durationSeconds()),
                BookingSource.valueOf(s.source()),
                s.notes(),
                s.requirements(),
                s.createdAt()
        );
        r.status = ReservationStatus.valueOf(s.status());
        if (s.resourceIds() != null) {
            s.resourceIds().forEach(res -> r.resourceIds.add(ResourceId.of(res)));
        }
        r.deposit = s.depositAmount() == null ? null : Money.of(s.depositAmount(), s.depositCurrency());
        r.depositPaid = s.depositPaid();
        r.depositRef = s.depositRef();
        r.checkedInAt = s.checkedInAt();
        r.endedAt = s.endedAt();
        r.cancelReason = s.cancelReason();
        r.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return r;
    }
}
