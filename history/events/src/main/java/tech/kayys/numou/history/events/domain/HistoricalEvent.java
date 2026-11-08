package tech.kayys.numou.history.events.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Canonical historical event record.
 *
 * <p>The date range is historical valid time. recordedAt/updatedAt/publishedAt
 * are record lifecycle timestamps and must never be substituted for it.</p>
 *
 * <p>This aggregate owns editorial lifecycle only. Claims, evidence, source
 * attribution, participants, places, tenant authorization and persistence are
 * separate contracts added by their owning milestones.</p>
 */
public record HistoricalEvent(
        HistoricalEventId id,
        String title,
        String summary,
        HistoricalDateRange dateRange,
        HistoricalCategoryCode category,
        Set<String> tags,
        HistoricalEventStatus status,
        long revision,
        Instant recordedAt,
        Instant updatedAt,
        Instant publishedAt) {

    private static final int MAX_TITLE_LENGTH = 240;
    private static final int MAX_SUMMARY_LENGTH = 4_000;
    private static final int MAX_TAGS = 32;
    private static final Pattern VALID_TAG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public HistoricalEvent {
        Objects.requireNonNull(id, "Event ID is required");
        title = requireText(title, "Event title", MAX_TITLE_LENGTH);
        summary = normalizeOptionalText(summary, MAX_SUMMARY_LENGTH);
        Objects.requireNonNull(dateRange, "Historical date range is required");
        Objects.requireNonNull(category, "Event category is required");
        tags = normalizeTags(tags);
        Objects.requireNonNull(status, "Event status is required");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) {
            throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
        }
        if (status == HistoricalEventStatus.DRAFT && publishedAt != null) {
            throw new IllegalArgumentException("Draft event cannot have a publication timestamp");
        }
        if (status != HistoricalEventStatus.DRAFT && publishedAt == null) {
            throw new IllegalArgumentException("Published or retired event requires a publication timestamp");
        }
        if (publishedAt != null && (publishedAt.isBefore(recordedAt) || publishedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("Publication timestamp must be between record and update timestamps");
        }
    }

    public static HistoricalEvent draft(
            String title, String summary, HistoricalDateRange dateRange,
            HistoricalCategoryCode category, Collection<String> tags, Instant recordedAt) {
        return draft(HistoricalEventId.generate(), title, summary, dateRange, category, tags, recordedAt);
    }

    public static HistoricalEvent draft(
            HistoricalEventId id, String title, String summary, HistoricalDateRange dateRange,
            HistoricalCategoryCode category, Collection<String> tags, Instant recordedAt) {
        Set<String> normalizedTags = normalizeTags(tags == null ? Set.of() : tags);
        return new HistoricalEvent(id, title, summary, dateRange, category, normalizedTags,
                HistoricalEventStatus.DRAFT, 1, recordedAt, recordedAt, null);
    }

    /** Content can be revised only while draft; published history is immutable in this milestone. */
    public HistoricalEvent revise(
            String newTitle, String newSummary, HistoricalDateRange newDateRange,
            HistoricalCategoryCode newCategory, Collection<String> newTags, Instant revisedAt) {
        requireNotBeforeUpdate(revisedAt);
        if (status != HistoricalEventStatus.DRAFT) {
            throw new IllegalStateException("Only draft historical events can be revised; create a new editorial revision");
        }
        return new HistoricalEvent(id, newTitle, newSummary, newDateRange, newCategory,
                normalizeTags(newTags == null ? Set.of() : newTags),
                status, Math.incrementExact(revision), recordedAt, revisedAt, null);
    }

    public HistoricalEvent publish(Instant publicationTime) {
        requireNotBeforeUpdate(publicationTime);
        if (status != HistoricalEventStatus.DRAFT) {
            throw new IllegalStateException("Only draft historical events can be published");
        }
        return new HistoricalEvent(id, title, summary, dateRange, category, tags,
                HistoricalEventStatus.PUBLISHED, Math.incrementExact(revision),
                recordedAt, publicationTime, publicationTime);
    }

    public HistoricalEvent retire(Instant retirementTime) {
        requireNotBeforeUpdate(retirementTime);
        if (status != HistoricalEventStatus.PUBLISHED) {
            throw new IllegalStateException("Only published historical events can be retired");
        }
        return new HistoricalEvent(id, title, summary, dateRange, category, tags,
                HistoricalEventStatus.RETIRED, Math.incrementExact(revision),
                recordedAt, retirementTime, publishedAt);
    }

    private void requireNotBeforeUpdate(Instant candidate) {
        Objects.requireNonNull(candidate, "Transition timestamp is required");
        if (candidate.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Transition timestamp cannot precede the current update timestamp");
        }
    }

    private static String requireText(String value, String label, int maximumLength) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1-" + maximumLength + " characters");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value, int maximumLength) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Event summary cannot exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    private static Set<String> normalizeTags(Collection<String> supplied) {
        Objects.requireNonNull(supplied, "Tags collection is required");
        if (supplied.size() > MAX_TAGS) throw new IllegalArgumentException("An event may have at most " + MAX_TAGS + " tags");
        TreeSet<String> normalized = new TreeSet<>();
        for (String tag : supplied) {
            Objects.requireNonNull(tag, "Event tag cannot be null");
            String value = tag.trim().toLowerCase(java.util.Locale.ROOT);
            if (!VALID_TAG.matcher(value).matches()) throw new IllegalArgumentException("Invalid event tag: " + tag);
            normalized.add(value);
        }
        if (normalized.size() > MAX_TAGS) throw new IllegalArgumentException("An event may have at most " + MAX_TAGS + " unique tags");
        return Collections.unmodifiableSet(normalized);
    }
}
