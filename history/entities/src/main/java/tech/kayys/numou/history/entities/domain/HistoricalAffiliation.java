package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.util.Objects;

/** Time-bounded relationship between a person and a historical collective. */
public record HistoricalAffiliation(
        HistoricalPersonId personId,
        HistoricalCollectiveId collectiveId,
        String roleCode,
        HistoricalDateRange validDuring) {
    public HistoricalAffiliation {
        Objects.requireNonNull(personId, "Person ID is required");
        Objects.requireNonNull(collectiveId, "Collective ID is required");
        Objects.requireNonNull(roleCode, "Affiliation role is required");
        roleCode = roleCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!roleCode.matches("[a-z0-9][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid affiliation role code");
        Objects.requireNonNull(validDuring, "Affiliation date range is required");
    }
}
