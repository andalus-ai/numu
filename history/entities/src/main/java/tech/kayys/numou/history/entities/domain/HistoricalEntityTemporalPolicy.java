package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalChronology;
import tech.kayys.numou.history.chronology.domain.TemporalRelation;

import java.util.Objects;

/** Cross-record temporal invariants that require calendar-aware chronology. */
public final class HistoricalEntityTemporalPolicy {
    private HistoricalEntityTemporalPolicy() {
    }

    /**
     * Rejects an explicitly reversed biography. Overlapping precision intervals
     * remain valid because the source dates may be too imprecise to order.
     */
    public static void assertBirthNotAfterDeath(
            HistoricalPerson person, HistoricalChronology chronology) {
        Objects.requireNonNull(person, "Person is required");
        Objects.requireNonNull(chronology, "Chronology is required");
        if (person.birthDate() == null || person.deathDate() == null) return;
        TemporalRelation relation = chronology.compare(person.birthDate(), person.deathDate())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot validate birth/death order: calendar adapter is unavailable"));
        if (relation == TemporalRelation.AFTER) {
            throw new IllegalArgumentException("Birth date must not be after death date");
        }
    }

    /** Ensures a known collective active interval contains its affiliation interval. */
    public static void assertAffiliationWithinCollective(
            HistoricalAffiliation affiliation,
            HistoricalCollective collective,
            HistoricalChronology chronology) {
        Objects.requireNonNull(affiliation, "Affiliation is required");
        Objects.requireNonNull(collective, "Collective is required");
        Objects.requireNonNull(chronology, "Chronology is required");
        if (!collective.id().equals(affiliation.collectiveId())) {
            throw new IllegalArgumentException("Affiliation must reference the supplied collective");
        }
        if (collective.activeRange() == null) return;
        boolean contained = chronology.contains(collective.activeRange(), affiliation.validDuring())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot validate affiliation interval: calendar adapter is unavailable"));
        if (!contained) {
            throw new IllegalArgumentException("Affiliation interval must fit within the collective active range");
        }
    }

    /** Ensures each explicitly dated place assertion fits within a known existence interval. */
    public static void assertPlaceAssertionsWithinExistence(
            HistoricalPlace place, HistoricalChronology chronology) {
        Objects.requireNonNull(place, "Place is required");
        Objects.requireNonNull(chronology, "Chronology is required");
        if (place.existenceRange() == null) return;
        for (HistoricalPlaceName name : place.names()) {
            if (name.validDuring() != null && !contains(place, name.validDuring(), chronology)) {
                throw new IllegalArgumentException("Place-name validity must fit within the place existence range");
            }
        }
        for (HistoricalPlaceLocation location : place.locations()) {
            if (location.validDuring() != null && !contains(place, location.validDuring(), chronology)) {
                throw new IllegalArgumentException("Place-location validity must fit within the place existence range");
            }
        }
    }

    private static boolean contains(
            HistoricalPlace place,
            tech.kayys.numou.history.chronology.domain.HistoricalDateRange assertion,
            HistoricalChronology chronology) {
        return chronology.contains(place.existenceRange(), assertion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot validate place assertion: calendar adapter is unavailable"));
    }
}
