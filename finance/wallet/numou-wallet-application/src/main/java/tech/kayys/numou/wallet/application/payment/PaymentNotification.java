package tech.kayys.numou.wallet.application.payment;

import java.math.BigDecimal;

/** A provider's callback, after its signature was verified, in provider-neutral form. */
public record PaymentNotification(String orderId, BigDecimal amount, Status status, String providerRef) {

    public enum Status { SETTLED, PENDING, FAILED, EXPIRED }
}
