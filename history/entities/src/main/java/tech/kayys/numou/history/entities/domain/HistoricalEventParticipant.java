package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.events.domain.HistoricalEventId;

import java.util.Objects;

/** Links a canonical historical event to a person or collective without copying either record. */
public record HistoricalEventParticipant(
        HistoricalEventId eventId,
        ParticipantKind participantKind,
        HistoricalPersonId personId,
        HistoricalCollectiveId collectiveId,
        String roleCode) {

    public enum ParticipantKind { PERSON, COLLECTIVE }

    public HistoricalEventParticipant {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(participantKind, "Participant kind is required");
        Objects.requireNonNull(roleCode, "Participant role is required");
        roleCode = roleCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!roleCode.matches("[a-z0-9][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid participant role code");
        boolean personPresent = personId != null;
        boolean collectivePresent = collectiveId != null;
        if (participantKind == ParticipantKind.PERSON && (!personPresent || collectivePresent)) {
            throw new IllegalArgumentException("PERSON participant requires only personId");
        }
        if (participantKind == ParticipantKind.COLLECTIVE && (!collectivePresent || personPresent)) {
            throw new IllegalArgumentException("COLLECTIVE participant requires only collectiveId");
        }
    }

    public static HistoricalEventParticipant person(HistoricalEventId eventId, HistoricalPersonId personId, String role) {
        return new HistoricalEventParticipant(eventId, ParticipantKind.PERSON, personId, null, role);
    }

    public static HistoricalEventParticipant collective(HistoricalEventId eventId, HistoricalCollectiveId collectiveId, String role) {
        return new HistoricalEventParticipant(eventId, ParticipantKind.COLLECTIVE, null, collectiveId, role);
    }
}
