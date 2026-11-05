package tech.kayys.numou.history.entities.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.geo.domain.GeoPoint;
import tech.kayys.numou.history.chronology.domain.*;
import tech.kayys.numou.history.events.domain.HistoricalEventId;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalEntitiesTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final HistoricalDateRange PERIOD = new HistoricalDateRange(
            HistoricalDate.year(700, GregorianCalendarDateConverter.ID),
            HistoricalDate.year(1300, GregorianCalendarDateConverter.ID));

    @Test
    void personSupportsAlternativeNamesAndRevisionWithoutLosingDisplayForm() {
        HistoricalPerson person = HistoricalPerson.create("Gajah Mada",
                List.of("Mahapatih Gajah Mada"), null, null, "Majapahit statesman", T0);
        HistoricalPerson revised = person.revise("Gajah Mada",
                List.of("Mahapatih Gajah Mada", "Rakryan Mapatih"), null, null,
                "Updated biography", T0.plusSeconds(60));

        assertEquals(person.id(), revised.id());
        assertEquals(1, person.revision());
        assertEquals(2, revised.revision());
        assertTrue(revised.alternativeNames().contains("Rakryan Mapatih"));
    }

    @Test
    void collectiveSupportsOptionalDatesAndParentReference() {
        HistoricalCollectiveId parentId = HistoricalCollectiveId.generate();
        HistoricalCollective collective = HistoricalCollective.create("Majapahit",
                Set.of("Wilwatikta"), HistoricalEntityKindCode.of("empire"),
                PERIOD, parentId, "Historical Javanese polity", T0);

        assertEquals(parentId, collective.parentCollectiveId());
        assertTrue(collective.activeDates().isPresent());
        assertEquals("empire", collective.kind().value());
        assertTrue(collective.alternativeNames().contains("Wilwatikta"));
    }

    @Test
    void placeKeepsNamesAndLocationsAsTimeBoundedAssertions() {
        HistoricalPlace place = HistoricalPlace.create(
                HistoricalPlaceKindCode.of("settlement"),
                List.of(
                        new HistoricalPlaceName("Batavia", "id", PERIOD, false),
                        new HistoricalPlaceName("Jakarta", "id", null, true)),
                List.of(new HistoricalPlaceLocation(
                        new GeoPoint(-6.1754, 106.8272),
                        HistoricalPlacePrecision.REPRESENTATIVE_CENTROID,
                        PERIOD, "Approximate historic centre")),
                PERIOD, null, T0);

        assertEquals(2, place.names().size());
        assertEquals(1, place.locations().size());
        assertEquals(-6.1754, place.locations().getFirst().point().latitude());
        assertEquals(1, place.revision());
    }

    @Test
    void eventLinksReferenceCanonicalRecordsAndEnforceParticipantShape() {
        HistoricalEventId eventId = HistoricalEventId.generate();
        HistoricalPersonId personId = HistoricalPersonId.generate();
        HistoricalEventParticipant participant = HistoricalEventParticipant.person(eventId, personId, "commander");

        assertEquals(personId, participant.personId());
        assertNull(participant.collectiveId());
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEventParticipant(
                eventId, HistoricalEventParticipant.ParticipantKind.PERSON,
                personId, HistoricalCollectiveId.generate(), "commander"));
        assertDoesNotThrow(() -> new HistoricalEventPlace(eventId, HistoricalPlaceId.generate(), "occurred_at"));
    }

    @Test
    void affiliationRequiresStableReferencesAndTemporalRange() {
        HistoricalAffiliation affiliation = new HistoricalAffiliation(
                HistoricalPersonId.generate(), HistoricalCollectiveId.generate(), "ruler", PERIOD);
        assertEquals("ruler", affiliation.roleCode());
        assertEquals(PERIOD, affiliation.validDuring());
    }

    @Test
    void rejectsInvalidGeoCoordinatesThroughSharedGeoPrimitive() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91.0, 0.0));
    }

    @Test
    void rejectsAlternativeNameThatDuplicatesPrimaryNameIgnoringCase() {
        assertThrows(IllegalArgumentException.class, () -> HistoricalPerson.create(
                "Gajah Mada", List.of("gajah mada"), null, null, "", T0));
    }

    @Test
    void validatesBiographicalOrderUsingCalendarAwareComparison() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalPerson reversed = HistoricalPerson.create("Person", List.of(),
                HistoricalDate.year(1200, GregorianCalendarDateConverter.ID),
                HistoricalDate.year(1100, GregorianCalendarDateConverter.ID), "", T0);

        assertThrows(IllegalArgumentException.class,
                () -> HistoricalEntityTemporalPolicy.assertBirthNotAfterDeath(reversed, chronology));
    }

    @Test
    void validatesAffiliationWithinCollectiveActiveRange() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalCollective collective = HistoricalCollective.create("Majapahit", List.of(),
                HistoricalEntityKindCode.of("empire"), PERIOD, null, "", T0);
        HistoricalAffiliation valid = new HistoricalAffiliation(
                HistoricalPersonId.generate(), collective.id(), "ruler",
                new HistoricalDateRange(
                        HistoricalDate.year(1290, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1300, GregorianCalendarDateConverter.ID)));
        HistoricalAffiliation invalid = new HistoricalAffiliation(
                HistoricalPersonId.generate(), collective.id(), "ruler",
                new HistoricalDateRange(
                        HistoricalDate.year(1290, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1400, GregorianCalendarDateConverter.ID)));

        assertDoesNotThrow(() -> HistoricalEntityTemporalPolicy.assertAffiliationWithinCollective(
                valid, collective, chronology));
        assertThrows(IllegalArgumentException.class, () ->
                HistoricalEntityTemporalPolicy.assertAffiliationWithinCollective(invalid, collective, chronology));
    }

    @Test
    void validatesPlaceNameAndLocationAssertionsAgainstExistenceRange() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalPlace place = HistoricalPlace.create(
                HistoricalPlaceKindCode.of("settlement"),
                List.of(new HistoricalPlaceName("Old name", "id",
                        new HistoricalDateRange(
                                HistoricalDate.year(800, GregorianCalendarDateConverter.ID),
                                HistoricalDate.year(900, GregorianCalendarDateConverter.ID)), true)),
                List.of(new HistoricalPlaceLocation(new GeoPoint(-7.0, 110.0),
                        HistoricalPlacePrecision.APPROXIMATE_LOCATION,
                        new HistoricalDateRange(
                                HistoricalDate.year(850, GregorianCalendarDateConverter.ID),
                                HistoricalDate.year(950, GregorianCalendarDateConverter.ID)), "")),
                PERIOD, null, T0);

        assertThrows(IllegalArgumentException.class, () ->
                HistoricalEntityTemporalPolicy.assertPlaceAssertionsWithinExistence(place, chronology));
    }

    @Test
    void validatesAffiliationAgainstKnownPersonLifespan() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalPerson person = HistoricalPerson.create("Ruler", List.of(),
                HistoricalDate.year(1200, GregorianCalendarDateConverter.ID),
                HistoricalDate.year(1300, GregorianCalendarDateConverter.ID), "", T0);
        HistoricalAffiliation valid = new HistoricalAffiliation(
                person.id(), HistoricalCollectiveId.generate(), "ruler",
                new HistoricalDateRange(
                        HistoricalDate.year(1200, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1290, GregorianCalendarDateConverter.ID)));
        HistoricalAffiliation invalid = new HistoricalAffiliation(
                person.id(), valid.collectiveId(), "ruler",
                new HistoricalDateRange(
                        HistoricalDate.year(1100, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1250, GregorianCalendarDateConverter.ID)));

        assertDoesNotThrow(() -> HistoricalEntityTemporalPolicy.assertAffiliationWithinKnownLifespan(
                valid, person, chronology));
        assertThrows(IllegalArgumentException.class, () ->
                HistoricalEntityTemporalPolicy.assertAffiliationWithinKnownLifespan(invalid, person, chronology));
    }
}
