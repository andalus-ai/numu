package tech.kayys.syirkah.product.domain.classification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeActivated;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeArchived;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeCreated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClassificationScheme aggregate")
class ClassificationSchemeTest {

    private static ClassificationScheme newScheme() {
        return ClassificationScheme.create(
                ClassificationSchemeId.generate(),
                "RETAIL",
                "Retail taxonomy",
                ClassificationSchemeType.RETAIL
        );
    }

    @Test
    void createsDraftSchemeAndRaisesEvent() {
        var scheme = newScheme();

        assertEquals(ClassificationSchemeStatus.DRAFT, scheme.status());
        assertEquals("RETAIL", scheme.code());

        var events = scheme.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ClassificationSchemeCreated.class, events.getFirst());
    }

    @Test
    void activatesDraftAndRaisesEvent() {
        var scheme = newScheme();
        scheme.pullDomainEvents();

        scheme.activate();

        assertEquals(ClassificationSchemeStatus.ACTIVE, scheme.status());
        assertInstanceOf(
                ClassificationSchemeActivated.class,
                scheme.pullDomainEvents().getFirst()
        );
    }

    @Test
    void rejectsActivatingNonDraftScheme() {
        var scheme = newScheme();
        scheme.activate();

        assertThrows(InvalidStateException.class, scheme::activate);
    }

    @Test
    void archivesActiveSchemeAndRaisesEvent() {
        var scheme = newScheme();
        scheme.activate();
        scheme.pullDomainEvents();

        scheme.archive();

        assertEquals(ClassificationSchemeStatus.ARCHIVED, scheme.status());
        assertInstanceOf(
                ClassificationSchemeArchived.class,
                scheme.pullDomainEvents().getFirst()
        );
    }

    @Test
    void rejectsArchivingDraftScheme() {
        var scheme = newScheme();

        assertThrows(InvalidStateException.class, scheme::archive);
    }

    @Test
    void rejectsRenamingArchivedScheme() {
        var scheme = newScheme();
        scheme.activate();
        scheme.archive();

        assertThrows(
                InvalidStateException.class,
                () -> scheme.rename("Renamed")
        );
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClassificationScheme.create(
                        ClassificationSchemeId.generate(),
                        "  ",
                        "Name",
                        ClassificationSchemeType.TAX
                )
        );
    }

    @Test
    void idRejectsNullValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClassificationSchemeId.of(null)
        );
        assertTrue(ClassificationSchemeId.generate().value() != null);
    }
}
