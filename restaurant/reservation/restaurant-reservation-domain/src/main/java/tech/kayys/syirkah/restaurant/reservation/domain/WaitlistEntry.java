package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.restaurant.kernel.domain.GuestContact;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;
import tech.kayys.syirkah.aggregate.domain.VersionedAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.domain.WaitlistEntryId;
import tech.kayys.syirkah.restaurant.reservation.domain.event.WaitlistJoined;
import tech.kayys.syirkah.restaurant.reservation.domain.event.WaitlistLeft;
import tech.kayys.syirkah.restaurant.reservation.domain.event.WaitlistNotified;
import tech.kayys.syirkah.restaurant.reservation.domain.event.WaitlistSeated;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A walk-in party waiting for a table. First come, first served among parties that fit the freed table. */
public final class WaitlistEntry extends VersionedAggregate<WaitlistEntryId> {

    private static final long serialVersionUID = 1L;

    public enum Status { WAITING, NOTIFIED, SEATED, LEFT, EXPIRED }

    public record Snapshot(UUID id, UUID venueId, String guestName, String guestPhone, String guestEmail, int partySize,
                           Instant joinedAt, long quotedWaitSeconds, String status, Instant notifiedAt, Instant holdUntil,
                           Instant createdAt, Instant updatedAt, int version) { }

    private final VenueId venueId;
    private final GuestContact guest;
    private final PartySize party;
    private final Instant joinedAt;
    private final Duration quotedWait;
    private Status status = Status.WAITING;
    private Instant notifiedAt;
    private Instant holdUntil;

    private WaitlistEntry(WaitlistEntryId id, VenueId venueId, GuestContact guest, PartySize party,
                          Instant joinedAt, Duration quotedWait) {
        super(id, joinedAt);
        this.venueId = Objects.requireNonNull(venueId, "venueId cannot be null");
        this.guest = Objects.requireNonNull(guest, "guest cannot be null");
        this.party = Objects.requireNonNull(party, "party cannot be null");
        this.joinedAt = joinedAt;
        this.quotedWait = Objects.requireNonNull(quotedWait, "quotedWait cannot be null");
    }

    public static WaitlistEntry join(WaitlistEntryId id, VenueId venueId, GuestContact guest, PartySize party,
                                     Duration quotedWait, Instant now) {
        final var e = new WaitlistEntry(id, venueId, guest, party, now, quotedWait);
        e.raise(WaitlistJoined.at(now, id.value(), venueId.value(), party.value()));
        return e;
    }

    /** Tells the guest a table is ready and holds it for {@code holdFor}. */
    public void notifyTableReady(Instant now, Duration holdFor) {
        if (status != Status.WAITING) {
            throw new InvalidStateException("Only a waiting party can be notified, was " + status);
        }
        this.status = Status.NOTIFIED;
        this.notifiedAt = now;
        this.holdUntil = now.plus(holdFor);
        touch(now);
        raise(WaitlistNotified.at(now, id().value(), venueId.value()));
    }

    public void seat(Instant now) {
        if (status != Status.WAITING && status != Status.NOTIFIED) {
            throw new InvalidStateException("Party is not waiting, was " + status);
        }
        if (status == Status.NOTIFIED && now.isAfter(holdUntil)) {
            throw new InvalidStateException("The table hold expired");
        }
        this.status = Status.SEATED;
        touch(now);
        raise(WaitlistSeated.at(now, id().value(), venueId.value()));
    }

    public void leave(Instant now) {
        if (status != Status.WAITING && status != Status.NOTIFIED) {
            throw new InvalidStateException("Party is not waiting, was " + status);
        }
        this.status = Status.LEFT;
        touch(now);
        raise(WaitlistLeft.at(now, id().value(), venueId.value(), "left"));
    }

    /** Expires a notified party that did not show up in time, freeing the hold. Returns true if it expired. */
    public boolean expireIfOverdue(Instant now) {
        if (status == Status.NOTIFIED && now.isAfter(holdUntil)) {
            this.status = Status.EXPIRED;
            touch(now);
            raise(WaitlistLeft.at(now, id().value(), venueId.value(), "hold-expired"));
            return true;
        }
        return false;
    }

    /** Rough quote: parties ahead / tables that could take them x average turn. Never below 5 minutes. */
    public static Duration quoteWait(int partiesAhead, int comparableTables, Duration averageTurn) {
        final int lanes = Math.max(1, comparableTables);
        final long waves = (long) Math.ceil((partiesAhead + 1) / (double) lanes);
        final var estimate = averageTurn.multipliedBy(waves).dividedBy(2); // tables free up spread across the turn
        return estimate.compareTo(Duration.ofMinutes(5)) < 0 ? Duration.ofMinutes(5) : estimate;
    }

    public VenueId venueId() { return venueId; }
    public GuestContact guest() { return guest; }
    public PartySize party() { return party; }
    public Instant joinedAt() { return joinedAt; }
    public Duration quotedWait() { return quotedWait; }
    public Status status() { return status; }
    public Instant holdUntil() { return holdUntil; }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), venueId.value(), guest.name(), guest.phone(), guest.email(), party.value(),
                joinedAt, quotedWait.getSeconds(), status.name(), notifiedAt, holdUntil, createdAt, updatedAt, version);
    }

    public static WaitlistEntry restore(Snapshot s) {
        final var e = new WaitlistEntry(WaitlistEntryId.of(s.id()), VenueId.of(s.venueId()),
                new GuestContact(s.guestName(), s.guestPhone(), s.guestEmail()), PartySize.of(s.partySize()),
                s.joinedAt(), Duration.ofSeconds(s.quotedWaitSeconds()));
        e.status = Status.valueOf(s.status());
        e.notifiedAt = s.notifiedAt();
        e.holdUntil = s.holdUntil();
        e.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return e;
    }
}
