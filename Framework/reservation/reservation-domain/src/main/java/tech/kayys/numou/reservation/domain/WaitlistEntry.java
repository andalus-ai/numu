package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.reservation.domain.event.WaitlistCheckedIn;
import tech.kayys.syirkah.reservation.domain.event.WaitlistJoined;
import tech.kayys.syirkah.reservation.domain.event.WaitlistLeft;
import tech.kayys.syirkah.reservation.domain.event.WaitlistNotified;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A party waiting for an available resource when current capacity is full.
 */
public final class WaitlistEntry extends ReservationAggregate<WaitlistEntryId> {

    private static final long serialVersionUID = 1L;

    public enum Status {
        WAITING,
        NOTIFIED,
        ADMITTED,
        SEATED,      // Compatibility alias for ADMITTED
        LEFT,
        EXPIRED
    }

    public record Snapshot(
            UUID id,
            UUID facilityId,
            String customerName,
            String customerPhone,
            String customerEmail,
            int partySize,
            String notes,
            String status,
            Instant joinedAt,
            Instant notifiedAt,
            Instant holdUntil,
            Instant admittedAt,
            Instant leftAt,
            String leaveReason,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) { }

    private final FacilityId facilityId;
    private final CustomerContact customer;
    private final PartySize party;
    private final String notes;
    private Status status = Status.WAITING;
    private final Instant joinedAt;
    private Instant notifiedAt;
    private Instant holdUntil;
    private Instant admittedAt;
    private Instant leftAt;
    private String leaveReason;

    private Duration quotedWait = Duration.ZERO;

    private WaitlistEntry(WaitlistEntryId id, FacilityId facilityId, CustomerContact customer, PartySize party,
                          String notes, Duration quotedWait, Instant now) {
        super(id, now);
        this.facilityId = Objects.requireNonNull(facilityId, "facilityId cannot be null");
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.party = Objects.requireNonNull(party, "party cannot be null");
        this.notes = notes == null ? "" : notes;
        this.quotedWait = quotedWait == null ? Duration.ZERO : quotedWait;
        this.joinedAt = now;
    }

    public static WaitlistEntry join(WaitlistEntryId id, FacilityId facilityId, CustomerContact customer,
                                     PartySize party, String notes, Instant now) {
        return join(id, facilityId, customer, party, Duration.ZERO, notes, now);
    }

    public static WaitlistEntry join(WaitlistEntryId id, FacilityId facilityId, CustomerContact customer,
                                     PartySize party, Duration quotedWait, Instant now) {
        return join(id, facilityId, customer, party, quotedWait, "", now);
    }

    public static WaitlistEntry join(WaitlistEntryId id, FacilityId facilityId, CustomerContact customer,
                                     PartySize party, Duration quotedWait, String notes, Instant now) {
        final var entry = new WaitlistEntry(id, facilityId, customer, party, notes, quotedWait, now);
        entry.raise(WaitlistJoined.at(now, id.value(), facilityId.value(), party.value()));
        return entry;
    }

    public void notifyCustomer(Duration hold, Instant now) {
        if (status != Status.WAITING && status != Status.NOTIFIED) {
            throw new InvalidStateException("Cannot notify a " + status + " waitlist entry");
        }
        this.status = Status.NOTIFIED;
        this.notifiedAt = now;
        this.holdUntil = now.plus(hold);
        touch(now);
        raise(WaitlistNotified.at(now, id().value(), facilityId.value(), holdUntil));
    }

    public void checkIn(Instant now) {
        if (status != Status.WAITING && status != Status.NOTIFIED) {
            throw new InvalidStateException("Cannot admit/check in a " + status + " waitlist entry");
        }
        if (status == Status.NOTIFIED && now.isAfter(holdUntil)) {
            throw new BusinessRuleViolation("The hold window on this notification has expired");
        }
        this.status = Status.ADMITTED;
        this.admittedAt = now;
        touch(now);
        raise(WaitlistCheckedIn.at(now, id().value(), facilityId.value()));
    }

    // Compatibility method for restaurant contexts
    public void seat(Instant now) {
        checkIn(now);
    }

    public void leave(String reason, Instant now) {
        if (status != Status.WAITING && status != Status.NOTIFIED) {
            throw new InvalidStateException("Only active entries can leave, was " + status);
        }
        this.status = Status.LEFT;
        this.leftAt = now;
        this.leaveReason = reason == null ? "" : reason;
        touch(now);
        raise(WaitlistLeft.at(now, id().value(), facilityId.value(), leaveReason));
    }

    public void expire(Instant now) {
        if (status != Status.NOTIFIED) {
            return;
        }
        if (now.isAfter(holdUntil)) {
            this.status = Status.EXPIRED;
            this.leftAt = now;
            this.leaveReason = "EXPIRED";
            touch(now);
            raise(WaitlistLeft.at(now, id().value(), facilityId.value(), "Hold expired"));
        }
    }

    public boolean expireIfOverdue(Instant now) {
        if (status == Status.NOTIFIED && now.isAfter(holdUntil)) {
            expire(now);
            return true;
        }
        return false;
    }

    public void notifyTableReady(Instant now, Duration hold) {
        notifyCustomer(hold, now);
    }

    public static Duration quoteWait(int ahead, int comparableResources, Duration averageTurn) {
        if (comparableResources <= 0) return averageTurn.multipliedBy(Math.max(1, ahead));
        return Duration.ofMinutes((long) Math.ceil((double) ahead / comparableResources * averageTurn.toMinutes()));
    }

    // ---- Accessors ----

    public FacilityId facilityId() { return facilityId; }
    public CustomerContact customer() { return customer; }
    public PartySize party() { return party; }
    public String notes() { return notes; }
    public Status status() { return status; }
    public Instant joinedAt() { return joinedAt; }
    public Instant notifiedAt() { return notifiedAt; }
    public Instant holdUntil() { return holdUntil; }
    public Instant admittedAt() { return admittedAt; }
    public Instant leftAt() { return leftAt; }
    public String leaveReason() { return leaveReason; }

    // Compatibility accessors for restaurant contexts
    public FacilityId venueId() { return facilityId; }
    public CustomerContact guest() { return customer; }
    public Instant seatedAt() { return admittedAt; }
    public Duration quotedWait() { return quotedWait; }

    // ---- Snapshot & Restore ----

    public Snapshot snapshot() {
        return new Snapshot(
                id().value(),
                facilityId.value(),
                customer.name(),
                customer.phone(),
                customer.email(),
                party.value(),
                notes,
                status.name(),
                joinedAt,
                notifiedAt,
                holdUntil,
                admittedAt,
                leftAt,
                leaveReason,
                createdAt,
                updatedAt,
                version
        );
    }

    public static WaitlistEntry restore(Snapshot s) {
        final var entry = new WaitlistEntry(
                WaitlistEntryId.of(s.id()),
                FacilityId.of(s.facilityId()),
                new CustomerContact(s.customerName(), s.customerPhone(), s.customerEmail()),
                PartySize.of(s.partySize()),
                s.notes(),
                Duration.ZERO,
                s.joinedAt()
        );
        entry.status = Status.valueOf(s.status());
        entry.notifiedAt = s.notifiedAt();
        entry.holdUntil = s.holdUntil();
        entry.admittedAt = s.admittedAt();
        entry.leftAt = s.leftAt();
        entry.leaveReason = s.leaveReason();
        entry.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return entry;
    }
}
