package tech.kayys.numou.history.entities.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.chronology.domain.CalendarSystemId;
import tech.kayys.numou.history.chronology.domain.HistoricalDate;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalRelationshipTest {
    private final HistoricalRelationshipVocabulary vocabulary = HistoricalRelationshipVocabulary.standard();
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");

    @Test void createsTypedTimeBoundedRelationship() {
        var subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        var object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        var range = new HistoricalDateRange(HistoricalDate.year(1000, CalendarSystemId.JULIAN),
                HistoricalDate.year(1100, CalendarSystemId.JULIAN));
        var relation = HistoricalRelationship.create(subject, object,
                HistoricalRelationshipKindCode.of("member-of"), range, "Member of court", now, vocabulary);
        assertEquals(subject, relation.subject());
        assertEquals(object, relation.object());
        assertEquals(range, relation.validDuring());
        assertEquals(1, relation.revision());
    }

    @Test void rejectsInvalidEndpointKinds() {
        var event = HistoricalEntityReference.event(tech.kayys.numou.history.events.domain.HistoricalEventId.generate());
        var person = HistoricalEntityReference.person(HistoricalPersonId.generate());
        assertThrows(IllegalArgumentException.class, () -> HistoricalRelationship.create(event, person,
                HistoricalRelationshipKindCode.of("member-of"), null, "", now, vocabulary));
    }

    @Test void symmetricRelationshipsHaveOneCanonicalEndpointOrder() {
        var a = HistoricalEntityReference.collective(HistoricalCollectiveId.of(new UUID(0, 1)));
        var b = HistoricalEntityReference.collective(HistoricalCollectiveId.of(new UUID(0, 2)));
        assertDoesNotThrow(() -> HistoricalRelationship.create(a, b,
                HistoricalRelationshipKindCode.of("allied-with"), null, "", now, vocabulary));
        assertThrows(IllegalArgumentException.class, () -> HistoricalRelationship.create(b, a,
                HistoricalRelationshipKindCode.of("allied-with"), null, "", now, vocabulary));
    }

    @Test void rejectsUnknownVocabularyAndSelfLinks() {
        var a = HistoricalEntityReference.place(HistoricalPlaceId.generate());
        assertThrows(IllegalArgumentException.class, () -> HistoricalRelationship.create(a, a,
                HistoricalRelationshipKindCode.of("related-to"), null, "", now, vocabulary));
        assertThrows(IllegalArgumentException.class, () -> HistoricalRelationship.create(a,
                HistoricalEntityReference.place(HistoricalPlaceId.generate()),
                HistoricalRelationshipKindCode.of("unknown-kind"), null, "", now, vocabulary));
    }

    @Test void inverseDefinitionsMustBeReciprocal() {
        var forward = new HistoricalRelationshipDefinition(HistoricalRelationshipKindCode.of("parent-of"),
                HistoricalRelationshipDefinition.Direction.DIRECTED, Set.of(HistoricalEntityKind.PERSON),
                Set.of(HistoricalEntityKind.PERSON), false, HistoricalRelationshipKindCode.of("child-of"));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalRelationshipVocabulary(List.of(forward)));
    }

    @Test void revisionsPreserveIdentityAndAdvanceMonotonically() {
        var a = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        var b = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        var relation = HistoricalRelationship.create(a, b, HistoricalRelationshipKindCode.of("allied-with"),
                null, "", now, vocabulary);
        var revised = relation.revise(b, a, HistoricalRelationshipKindCode.of("allied-with"),
                null, "Alliance recorded differently", now.plusSeconds(1), vocabulary);
        assertEquals(relation.id(), revised.id());
        assertEquals(2, revised.revision());
    }
}
