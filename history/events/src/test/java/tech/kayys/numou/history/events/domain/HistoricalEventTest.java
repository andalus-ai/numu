package tech.kayys.numou.history.events.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.chronology.domain.GregorianCalendarDateConverter;
import tech.kayys.numou.history.chronology.domain.HistoricalDate;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalEventTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    private static final HistoricalDateRange RANGE = new HistoricalDateRange(
            HistoricalDate.day(1945, 8, 17, GregorianCalendarDateConverter.ID),
            HistoricalDate.day(1945, 8, 17, GregorianCalendarDateConverter.ID));

    @Test
    void createsDraftWithNormalizedMetadataAndDistinctHistoricalTime() {
        HistoricalEvent event = HistoricalEvent.draft(" Proclamation ", " Independence ",
                RANGE, HistoricalCategoryCode.of("political"), List.of("Indonesia", "INDEPENDENCE"), T0);

        assertEquals("Proclamation", event.title());
        assertEquals("Independence", event.summary());
        assertEquals(1945, event.dateRange().from().astronomicalYear());
        assertEquals(T0, event.recordedAt());
        assertEquals(T0, event.updatedAt());
        assertNull(event.publishedAt());
        assertEquals(HistoricalEventStatus.DRAFT, event.status());
        assertEquals(1, event.revision());
        assertEquals(List.of("independence", "indonesia"), event.tags().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> event.tags().add("new-tag"));
    }

    @Test
    void draftCanBeRevisedAndThenPublished() {
        HistoricalEvent draft = HistoricalEvent.draft("Proclamation", "", RANGE,
                HistoricalCategoryCode.of("political"), List.of(), T0);
        HistoricalEvent revised = draft.revise("Proclamation of Independence", "Indonesia",
                RANGE, HistoricalCategoryCode.of("political"), List.of("indonesia"), T1);
        HistoricalEvent published = revised.publish(T1);

        assertEquals(2, revised.revision());
        assertEquals(3, published.revision());
        assertEquals(HistoricalEventStatus.PUBLISHED, published.status());
        assertEquals(T1, published.publishedAt());
        assertThrows(IllegalStateException.class, () -> published.revise(
                "Changed", "", RANGE, HistoricalCategoryCode.of("political"), List.of(), T1));
    }

    @Test
    void publishedEventCanBeRetiredButNotRepublished() {
        HistoricalEvent published = HistoricalEvent.draft("Event", "", RANGE,
                HistoricalCategoryCode.of("social"), List.of(), T0).publish(T0);
        HistoricalEvent retired = published.retire(T1);

        assertEquals(HistoricalEventStatus.RETIRED, retired.status());
        assertEquals(T0, retired.publishedAt());
        assertEquals(T1, retired.updatedAt());
        assertThrows(IllegalStateException.class, () -> retired.publish(T1));
    }

    @Test
    void rejectsInvalidCategoryAndLifecycleTimestamps() {
        assertThrows(IllegalArgumentException.class, () -> HistoricalCategoryCode.of("Not a code"));
        assertThrows(IllegalArgumentException.class, () -> HistoricalEvent.draft(
                "Event", "", RANGE, HistoricalCategoryCode.of("social"), List.of(), T1)
                .publish(T0));
    }

    @Test
    void rejectsMoreThanThirtyTwoTags() {
        List<String> tags = java.util.stream.IntStream.range(0, 33).mapToObj(i -> "tag-" + i).toList();
        assertThrows(IllegalArgumentException.class, () -> HistoricalEvent.draft(
                "Event", "", RANGE, HistoricalCategoryCode.of("social"), tags, T0));
    }
}
