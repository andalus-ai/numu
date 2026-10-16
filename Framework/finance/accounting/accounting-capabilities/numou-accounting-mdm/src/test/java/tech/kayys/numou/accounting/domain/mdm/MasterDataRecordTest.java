package tech.kayys.syirkah.accounting.domain.mdm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MasterDataRecordTest {

    @Test
    void full_lifecycle_and_effective_dating() {
        var id = MasterDataId.generate();
        var code = MasterDataCode.of("CUST-100");
        var record = new MasterDataRecord(
                id, code, MasterDataKind.CUSTOMER, "Acme Corp",
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)),
                Map.of("taxNumber", "TAX-998877"));

        assertEquals(MasterDataStatus.DRAFT, record.status());
        assertFalse(record.isEffectiveAt(LocalDate.of(2026, 6, 1))); // not published yet

        record.submitForReview();
        assertEquals(MasterDataStatus.REVIEW, record.status());

        record.approve();
        assertEquals(MasterDataStatus.APPROVED, record.status());

        record.publish();
        assertEquals(MasterDataStatus.PUBLISHED, record.status());
        assertTrue(record.isEffectiveAt(LocalDate.of(2026, 6, 1)));
        assertFalse(record.isEffectiveAt(LocalDate.of(2025, 12, 31))); // before validFrom
    }

    @Test
    void attribute_update_increments_version() {
        var id = MasterDataId.generate();
        var code = MasterDataCode.of("VEND-200");
        var record = new MasterDataRecord(
                id, code, MasterDataKind.SUPPLIER, "Global Tech",
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)),
                Map.of("paymentTerms", "NET30"));

        assertEquals(1, record.version());
        record.updateAttributes(Map.of("paymentTerms", "NET60"), LocalDate.of(2026, 2, 1));
        assertEquals(2, record.version());
        assertEquals("NET60", record.attributes().get("paymentTerms"));
    }
}
