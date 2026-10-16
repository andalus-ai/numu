package tech.kayys.syirkah.accounting.application.tax;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.tax.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TaxServiceTest {

    @Test
    void compute_tax_and_compile_periodic_return() {
        var service = new TaxService();

        // 1. Sales invoice: 10,000 @ 11% PPN Output Tax = 1,100
        service.computeAndRecord(TaxKind.PPN, TaxDirection.OUTPUT_TAX, "ID",
                new BigDecimal("10000"), new BigDecimal("11.00"), "IDR", "INV-1001");

        // 2. Vendor invoice: 5,000 @ 11% PPN Input Tax = 550
        service.computeAndRecord(TaxKind.PPN, TaxDirection.INPUT_TAX, "ID",
                new BigDecimal("5000"), new BigDecimal("11.00"), "IDR", "VINV-5001");

        // 3. Compile return
        var returnId = TaxReturnId.generate();
        var ret = service.compileReturn(returnId, TaxKind.PPN, "2026-03", "ID");

        assertEquals(new BigDecimal("1100.0000"), ret.totalOutputTax());
        assertEquals(new BigDecimal("550.0000"), ret.totalInputTax());
        assertEquals(new BigDecimal("550.0000"), ret.netPayable());

        // 4. File
        service.fileReturn(returnId, "DGT-SPT-1111-202603");
        assertEquals(TaxReturnStatus.FILED, service.getReturn(returnId).get().status());
    }
}
