package tech.kayys.payment.method.processor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayResponse;
import tech.kayys.payment.gateway.PaymentGatewayProvider;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.service.PaymentGatewayRegistry;

/**
 * Abstract base class for payment method processors
 */
@ApplicationScoped
public abstract class AbstractPaymentMethodProcessor implements PaymentMethodProcessor {
    
    @Inject
    PaymentGatewayRegistry gatewayRegistry;
    
    @Override
    public boolean supports(PaymentMethodType methodType) {
        return getPaymentMethodType() == methodType;
    }
    
    @Override
    public PaymentMethodResult initiate(PaymentMethodContext context) {
        Optional<PaymentGatewayProvider> provider = gatewayRegistry.getProvider(
            context.getGatewayProvider());
        
        if (provider.isEmpty()) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Gateway provider not found: " + context.getGatewayProvider())
                .errorCode("GATEWAY_NOT_FOUND")
                .build();
        }
        
        PaymentGatewayProvider gateway = provider.get();
        GatewayRequest request = buildGatewayRequest(context);
        
        GatewayResponse response = gateway.createPayment(request);
        
        return convertToMethodResult(response);
    }
    
    @Override
    public PaymentMethodResult verify(String transactionId) {
        // Try all registered gateways to find the transaction
        List<PaymentGatewayProvider> providers = gatewayRegistry.getAllProviders();
        
        for (PaymentGatewayProvider provider : providers) {
            GatewayResponse response = provider.getPaymentStatus(transactionId);
            if (response.isSuccess() || response.getPaymentStatus() != null) {
                return convertToMethodResult(response);
            }
        }
        
        return PaymentMethodResult.builder()
            .success(false)
            .errorMessage("Transaction not found in any gateway")
            .errorCode("TRANSACTION_NOT_FOUND")
            .build();
    }
    
    @Override
    public PaymentMethodResult cancel(String transactionId, String reason) {
        // Try all registered gateways
        List<PaymentGatewayProvider> providers = gatewayRegistry.getAllProviders();
        
        for (PaymentGatewayProvider provider : providers) {
            GatewayResponse response = provider.cancelPayment(transactionId, reason);
            if (response.isSuccess()) {
                return convertToMethodResult(response);
            }
        }
        
        return PaymentMethodResult.builder()
            .success(false)
            .errorMessage("Failed to cancel transaction")
            .errorCode("CANCEL_FAILED")
            .build();
    }
    
    @Override
    public PaymentMethodResult refund(String transactionId, BigDecimal amount, String reason) {
        List<PaymentGatewayProvider> providers = gatewayRegistry.getAllProviders();
        
        for (PaymentGatewayProvider provider : providers) {
            GatewayResponse response = provider.refundPayment(transactionId, amount, reason);
            if (response.isSuccess()) {
                return convertToMethodResult(response);
            }
        }
        
        return PaymentMethodResult.builder()
            .success(false)
            .errorMessage("Failed to refund transaction")
            .errorCode("REFUND_FAILED")
            .build();
    }
    
    protected abstract GatewayRequest buildGatewayRequest(PaymentMethodContext context);
    
    protected PaymentMethodResult convertToMethodResult(GatewayResponse response) {
        PaymentMethodResult.Builder builder = PaymentMethodResult.builder()
            .success(response.isSuccess())
            .transactionId(response.getTransactionId())
            .externalTransactionId(response.getExternalTransactionId())
            .amount(response.getAmount())
            .currency(response.getCurrency())
            .errorMessage(response.getErrorMessage())
            .errorCode(response.getErrorCode())
            .expiryTime(response.getExpiryTime());
        
        // Convert gateway status to method status
        if (response.getPaymentStatus() != null) {
            builder.status(convertStatus(response.getPaymentStatus()));
        }
        
        // Copy payment method specific fields
        if (response.getPaymentUrl() != null) {
            builder.paymentUrl(response.getPaymentUrl());
        }
        if (response.getQrCodeUrl() != null) {
            builder.qrCodeUrl(response.getQrCodeUrl());
        }
        if (response.getQrCodeString() != null) {
            builder.qrCodeString(response.getQrCodeString());
        }
        if (response.getVirtualAccountNumber() != null) {
            builder.virtualAccountNumber(response.getVirtualAccountNumber());
        }
        if (response.getBankCode() != null) {
            builder.bankCode(response.getBankCode());
        }
        
        // Copy metadata
        if (response.getMetadata() != null) {
            response.getMetadata().forEach(builder::addMetadata);
        }
        
        return builder.build();
    }
    
    private PaymentMethodResult.PaymentStatus convertStatus(GatewayResponse.PaymentStatus status) {
        switch (status) {
            case SUCCESS:
                return PaymentMethodResult.PaymentStatus.SUCCESS;
            case PENDING:
                return PaymentMethodResult.PaymentStatus.PENDING;
            case FAILED:
                return PaymentMethodResult.PaymentStatus.FAILED;
            case CANCELLED:
                return PaymentMethodResult.PaymentStatus.CANCELLED;
            case EXPIRED:
                return PaymentMethodResult.PaymentStatus.EXPIRED;
            case REFUNDED:
                return PaymentMethodResult.PaymentStatus.REFUNDED;
            case PARTIALLY_REFUNDED:
                return PaymentMethodResult.PaymentStatus.PARTIALLY_REFUNDED;
            default:
                return PaymentMethodResult.PaymentStatus.FAILED;
        }
    }
}
