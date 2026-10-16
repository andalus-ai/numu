package tech.kayys.syirkah.accounting.domain.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TaxReturnTest {

    @Test
    void compile_and_file_tax_return() {
        var ret = new TaxReturn(TaxReturnId.generate(), TaxKind.PPN, "2026-03", "ID");
        assertEquals(TaxReturnStatus.DRAFT, ret.status());

        // Output = 1100, Input = 800 -> Net Payable = 300
        ret.updateTotals(new BigDecimal("1100"), new BigDecimal("800"));
        assertEquals(TaxReturnStatus.READY, ret.status());
        assertEquals(new BigDecimal("300"), ret.netPayable());

        ret.file("CORETAX-BPE-998877");
        assertEquals(TaxReturnStatus.FILED, ret.status());
        assertEquals("CORETAX-BPE-998877", ret.filingReceipt());
    }
}
