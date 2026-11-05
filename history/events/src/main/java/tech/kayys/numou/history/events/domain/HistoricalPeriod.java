package tech.kayys.numou.history.events.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A named historical interval such as an era, dynasty, reign, movement or
 * archaeological period. Its category is data-driven rather than a closed enum.
 */
public record HistoricalPeriod(
        HistoricalPeriodId id,
        String name,
        String description,
        HistoricalDateRange dateRange,
        HistoricalCategoryCode category,
        HistoricalPeriodId parentPeriodId,
        long revision,
        Instant recordedAt,
        Instant updatedAt) {

    public HistoricalPeriod {
        Objects.requireNonNull(id, "Period ID is required");
        name = requireText(name, "Period name", 240);
        description = description == null ? "" : description.trim();
        if (description.length() > 4_000) throw new IllegalArgumentException("Period description cannot exceed 4000 characters");
        Objects.requireNonNull(dateRange, "Historical period range is required");
        Objects.requireNonNull(category, "Period category is required");
        if (id.equals(parentPeriodId)) throw new IllegalArgumentException("A period cannot be its own parent");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
    }

    public static HistoricalPeriod create(
            String name, String description, HistoricalDateRange dateRange,
            HistoricalCategoryCode category, HistoricalPeriodId parentPeriodId, Instant recordedAt) {
        return new HistoricalPeriod(HistoricalPeriodId.generate(), name, description, dateRange,
                category, parentPeriodId, 1, recordedAt, recordedAt);
    }

    public HistoricalPeriod revise(
            String newName, String newDescription, HistoricalDateRange newDateRange,
            HistoricalCategoryCode newCategory, HistoricalPeriodId newParentPeriodId, Instant revisedAt) {
        Objects.requireNonNull(revisedAt, "Revision timestamp is required");
        if (revisedAt.isBefore(updatedAt)) throw new IllegalArgumentException("Revision timestamp cannot precede update timestamp");
        return new HistoricalPeriod(id, newName, newDescription, newDateRange, newCategory,
                newParentPeriodId, Math.incrementExact(revision), recordedAt, revisedAt);
    }

    public Optional<HistoricalPeriodId> parent() {
        return Optional.ofNullable(parentPeriodId);
    }

    private static String requireText(String value, String label, int maximumLength) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1-" + maximumLength + " characters");
        }
        return normalized;
    }
}
