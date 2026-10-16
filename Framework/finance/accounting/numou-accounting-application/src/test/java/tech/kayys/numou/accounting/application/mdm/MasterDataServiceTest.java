package tech.kayys.syirkah.accounting.application.mdm;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.mdm.*;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MasterDataServiceTest {

    @Test
    void register_publish_and_resolve_effective_record() {
        var service = new MasterDataService();
        var id = MasterDataId.generate();
        var code = MasterDataCode.of("PROD-001");

        service.register(id, code, MasterDataKind.PRODUCT, "Entsyirkahrise Server",
                LocalDate.of(2026, 1, 1), Map.of("taxNumber", "VAT-ID-12345"));
        service.publish(id);

        var found = service.findEffective(code, LocalDate.of(2026, 5, 1));
        assertTrue(found.isPresent());
        assertEquals("Entsyirkahrise Server", found.get().name());

        // Test duplicate detection / golden record lookup
        var byTax = service.findByTaxNumber("VAT-ID-12345");
        assertEquals(1, byTax.size());
    }
}
