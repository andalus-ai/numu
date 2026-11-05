package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed reference to a canonical History record; never embeds another aggregate. */
public record HistoricalEntityReference(HistoricalEntityKind kind, UUID id) implements Comparable<HistoricalEntityReference> {
    public HistoricalEntityReference {
        Objects.requireNonNull(kind, "Entity kind is required");
        Objects.requireNonNull(id, "Entity ID is required");
    }

    @Override
    public int compareTo(HistoricalEntityReference other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : id.compareTo(other.id);
    }

    public static HistoricalEntityReference person(HistoricalPersonId id) {
        return new HistoricalEntityReference(HistoricalEntityKind.PERSON, Objects.requireNonNull(id).value());
    }
    public static HistoricalEntityReference collective(HistoricalCollectiveId id) {
        return new HistoricalEntityReference(HistoricalEntityKind.COLLECTIVE, Objects.requireNonNull(id).value());
    }
    public static HistoricalEntityReference place(HistoricalPlaceId id) {
        return new HistoricalEntityReference(HistoricalEntityKind.PLACE, Objects.requireNonNull(id).value());
    }
    public static HistoricalEntityReference event(tech.kayys.numou.history.events.domain.HistoricalEventId id) {
        return new HistoricalEntityReference(HistoricalEntityKind.EVENT, Objects.requireNonNull(id).value());
    }
}
