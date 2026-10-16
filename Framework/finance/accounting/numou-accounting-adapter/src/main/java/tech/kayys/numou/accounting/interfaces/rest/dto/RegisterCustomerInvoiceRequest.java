package tech.kayys.syirkah.accounting.interfaces.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegisterCustomerInvoiceRequest(
        String invoiceId,
        String customerId,
        String customerRef,
        String currency,
        List<InvoiceLineDto> lines
) {
    public record InvoiceLineDto(
            String lineId,
            String accountCode,
            String description,
            BigDecimal netAmount,
            BigDecimal taxAmount
    ) {}
}
