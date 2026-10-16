package tech.kayys.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Strategy interface for payment gateway providers
 */
public interface PaymentGatewayProvider {
    
    /**
     * Get the gateway provider this implementation handles
     */
    GatewayProvider getProvider();
    
    /**
     * Check if this provider supports the given payment method
     */
    boolean supportsPaymentMethod(String paymentMethodCode);
    
    /**
     * Create payment transaction
     */
    GatewayResponse createPayment(GatewayRequest request);
    
    /**
     * Get payment status
     */
    GatewayResponse getPaymentStatus(String transactionId);
    
    /**
     * Cancel payment transaction
     */
    GatewayResponse cancelPayment(String transactionId, String reason);
    
    /**
     * Refund payment (full amount)
     */
    GatewayResponse refundPayment(String transactionId, String reason);
    
    /**
     * Refund payment (partial amount)
     */
    GatewayResponse refundPayment(String transactionId, BigDecimal amount, String reason);
    
    /**
     * Get gateway configuration
     */
    GatewayConfig getConfig();
    
    /**
     * Verify webhook/callback signature
     */
    boolean verifyCallback(String signature, String payload);
}
