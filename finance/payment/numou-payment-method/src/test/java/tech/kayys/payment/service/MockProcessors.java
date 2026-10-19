package tech.kayys.payment.service;

import java.math.BigDecimal;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;

public class MockProcessors {

    abstract static class BaseMockProcessor implements PaymentMethodProcessor {
        @Override
        public PaymentMethodResult initiate(PaymentMethodContext context) {
            return PaymentMethodResult.builder().success(true).transactionId("MOCK-TXN").build();
        }

        @Override
        public PaymentMethodResult verify(String transactionId) {
            return PaymentMethodResult.builder().success(true).transactionId(transactionId).build();
        }

        @Override
        public PaymentMethodResult cancel(String transactionId, String reason) {
            return PaymentMethodResult.builder().success(true).transactionId(transactionId).build();
        }

        @Override
        public PaymentMethodResult refund(String transactionId, BigDecimal amount, String reason) {
            return PaymentMethodResult.builder().success(true).transactionId(transactionId).build();
        }

        @Override
        public boolean supports(PaymentMethodType methodType) {
            return getPaymentMethodType() == methodType;
        }
    }

    @ApplicationScoped
    public static class BankTransferProcessor extends BaseMockProcessor {
        @Override
        public PaymentMethodType getPaymentMethodType() {
            return PaymentMethodType.BANK_TRANSFER;
        }
    }

    @ApplicationScoped
    public static class QrisProcessor extends BaseMockProcessor {
        @Override
        public PaymentMethodType getPaymentMethodType() {
            return PaymentMethodType.QRIS;
        }
    }

    @ApplicationScoped
    public static class EWalletProcessor extends BaseMockProcessor {
        @Override
        public PaymentMethodType getPaymentMethodType() {
            return PaymentMethodType.E_WALLET;
        }
    }

    @ApplicationScoped
    public static class FintechProcessor extends BaseMockProcessor {
        @Override
        public PaymentMethodType getPaymentMethodType() {
            return PaymentMethodType.FINTECH;
        }
    }
}
