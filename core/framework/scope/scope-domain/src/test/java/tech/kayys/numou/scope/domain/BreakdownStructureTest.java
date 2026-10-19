package tech.kayys.numou.scope.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreakdownStructureTest {
    @Test
    void supportsAnExtensibleTypeAndOpaqueOwnerReference() {
        var structure = BreakdownStructure.create(
                BreakdownStructureType.of("wbs"),
                " DELIVERY ",
                " Delivery scope ",
                "project:123");

        assertEquals("WBS", structure.type().code());
        assertEquals("DELIVERY", structure.code());
        assertEquals("Delivery scope", structure.name());
        assertEquals("project:123", structure.ownerReference());
        assertEquals(BreakdownStructureStatus.DRAFT, structure.status());
    }

    @Test
    void onlyDraftStructuresCanBeRenamedAndActivatedOnce() {
        var structure = BreakdownStructure.create(
                BreakdownStructureType.of("PRODUCT"), "P1", "Product tree", null);

        structure.rename("Product breakdown");
        structure.activate();

        assertEquals(BreakdownStructureStatus.ACTIVE, structure.status());
        assertThrows(IllegalStateException.class, () -> structure.rename("Changed"));
        assertThrows(IllegalStateException.class, structure::activate);

        structure.retire();
        assertEquals(BreakdownStructureStatus.RETIRED, structure.status());
        assertThrows(IllegalStateException.class, structure::retire);
    }

    @Test
    void rejectsBlankMetadataAndInvalidTypeKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> BreakdownStructure.create(BreakdownStructureType.of("WBS"), " ", "Name", null));
        assertThrows(IllegalArgumentException.class,
                () -> BreakdownStructureType.of("construction WBS"));
        assertThrows(IllegalArgumentException.class,
                () -> BreakdownStructureType.of(""));
    }
}
