package tech.kayys.payment.method;

import java.math.BigDecimal;

/**
 * Strategy interface for processing different payment methods
 */
public interface PaymentMethodProcessor {
    
    /**
     * Get the payment method type this processor handles
     */
    PaymentMethodType getPaymentMethodType();
    
    /**
     * Initialize payment transaction
     */
    PaymentMethodResult initiate(PaymentMethodContext context);
    
    /**
     * Verify payment status (for async payments)
     */
    PaymentMethodResult verify(String transactionId);
    
    /**
     * Cancel payment transaction
     */
    PaymentMethodResult cancel(String transactionId, String reason);
    
    /**
     * Refund completed payment
     */
    PaymentMethodResult refund(String transactionId, BigDecimal amount, String reason);
    
    /**
     * Check if this processor supports the given payment method
     */
    boolean supports(PaymentMethodType methodType);
}
