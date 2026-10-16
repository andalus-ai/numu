package tech.kayys.syirkah.accounting.interfaces.rest.dto;

import java.math.BigDecimal;

public record ReceiveCustomerPaymentRequest(
        String paymentId,
        String customerId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String cashAccount,
        String arAccount
) {}
