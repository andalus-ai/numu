package tech.kayys.numou.billing.application.port;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.concurrent.CompletionStage;

/**
 * Port for payment processing.
 */
public interface PaymentPort {

    /**
     * Processes a payment using a token.
     */
    CompletionStage<PaymentResult> processPayment(
        String token,
        Money amount,
        String currencyCode
    );

    /**
     * Refunds a payment.
     */
    CompletionStage<PaymentResult> refundPayment(
        String transactionId,
        Money amount,
        String currencyCode
    );

    record PaymentResult(
        boolean success,
        String transactionId,
        String message
    ) {}
}