package tech.kayys.numou.automotiveparts.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AutomotivePartsDomainTest {

    @Test
    void profileReferencesCanonicalProductAndRejectsDuplicateReferences() {
        var productId = ProductId.of(UUID.randomUUID());
        var reference = new PartReference(PartReferenceType.OEM, "  abc-123 ");
        var profile = new AutomotivePartProfile("tenant-a", productId,
                AutomotivePartProfile.PartKind.REPLACEMENT_PART, "Oil filter", List.of(reference));

        assertEquals(productId, profile.productId());
        assertEquals("ABC-123", profile.references().getFirst().value());
        assertThrows(IllegalArgumentException.class, () -> new AutomotivePartProfile("tenant-a", productId,
                AutomotivePartProfile.PartKind.REPLACEMENT_PART, "Oil filter", List.of(reference, reference)));
    }

    @Test
    void supplierSkuRequiresIssuer() {
        assertThrows(IllegalArgumentException.class,
                () -> new PartReference(PartReferenceType.SUPPLIER_SKU, "SUP-1"));
        assertEquals("VENDOR-1", new PartReference(PartReferenceType.SUPPLIER_SKU, "sup-1", "VENDOR-1", "ID")
                .issuer());
    }

    @Test
    void fitmentMatchesRequiredAndOptionalVehicleAttributesAndDates() {
        var fitment = new VehicleFitment("tenant-a", "ID", "Toyota", "Avanza", "GEN-2",
                2020, 2024, "2NR-VE", null, "OEM_CATALOG", "ref-42",
                LocalDate.of(2024, 1, 1), null);
        var matchingVehicle = new VehicleSpecification("ID", "toyota", "avanza", "gen-2",
                2022, "2nr-ve", "G");
        var wrongEngine = new VehicleSpecification("ID", "Toyota", "Avanza", "GEN-2",
                2022, "1NR-VE", "G");

        assertTrue(fitment.matches(matchingVehicle, LocalDate.of(2025, 1, 1)));
        assertFalse(fitment.matches(wrongEngine, LocalDate.of(2025, 1, 1)));
        assertFalse(fitment.matches(matchingVehicle, LocalDate.of(2023, 1, 1)));
    }

    @Test
    void fitmentRejectsInvalidYearRangeAndEffectiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleFitment("t", "ID", "A", "B", null,
                2025, 2020, null, null, "source", null, LocalDate.of(2024, 1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new VehicleFitment("t", "ID", "A", "B", null,
                2020, 2025, null, null, "source", null, LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)));
    }

    @Test
    void substitutionIsDirectionalAndDateBounded() {
        var source = ProductId.of(UUID.randomUUID());
        var substitute = ProductId.of(UUID.randomUUID());
        var relation = new PartSubstitution("tenant-a", source, substitute,
                PartSubstitution.SubstitutionKind.CONDITIONAL, true, "Only with adapter kit",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertTrue(relation.isEffectiveOn(LocalDate.of(2025, 6, 1)));
        assertFalse(relation.isEffectiveOn(LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new PartSubstitution("tenant-a", source, source,
                PartSubstitution.SubstitutionKind.APPROVED_EQUIVALENT, false, "",
                LocalDate.of(2025, 1, 1), null));
    }
}