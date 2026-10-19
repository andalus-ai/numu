package tech.kayys.payment.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.PaymentGatewayProvider;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Factory for creating payment processors based on method and gateway configuration
 */
@ApplicationScoped
public class PaymentProcessorFactory {
    
    @Inject
    PaymentMethodRegistry methodRegistry;
    
    @Inject
    PaymentGatewayRegistry gatewayRegistry;
    
    /**
     * Get the appropriate payment method processor
     */
    public Optional<PaymentMethodProcessor> getMethodProcessor(PaymentMethodType methodType) {
        return methodRegistry.getProcessor(methodType);
    }
    
    /**
     * Get the appropriate payment method processor by code
     */
    public Optional<PaymentMethodProcessor> getMethodProcessor(String methodCode) {
        return methodRegistry.getProcessor(methodCode);
    }
    
    /**
     * Get the appropriate gateway provider
     */
    public Optional<PaymentGatewayProvider> getGatewayProvider(GatewayProvider provider) {
        return gatewayRegistry.getProvider(provider);
    }
    
    /**
     * Get the appropriate gateway provider by code
     */
    public Optional<PaymentGatewayProvider> getGatewayProvider(String providerCode) {
        return gatewayRegistry.getProvider(providerCode);
    }
    
    /**
     * Get the best gateway provider for a payment method
     * This uses a priority system based on payment method type
     */
    public Optional<PaymentGatewayProvider> getBestGatewayForMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            // Default to aggregator (Midtrans/Xendit) for general payments
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        // Select gateway based on payment method type
        if (methodType.isBankTransfer()) {
            // Prefer aggregators for VA support
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        if (methodType.isQRIS()) {
            // Prefer QRIS-specific providers
            var qrisProviders = gatewayRegistry.getQRISProviders();
            if (!qrisProviders.isEmpty()) {
                return Optional.of(qrisProviders.get(0));
            }
            // Fall back to aggregators
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        if (methodType.isEWallet()) {
            // Prefer aggregators for e-wallet support
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        if (methodType.isFintech()) {
            // Prefer fintech-specific providers
            var fintechProviders = gatewayRegistry.getFintechProviders();
            if (!fintechProviders.isEmpty()) {
                return Optional.of(fintechProviders.get(0));
            }
            // Fall back to aggregators
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        if (methodType.isCard()) {
            // Prefer aggregators for card processing
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        if (methodType.isCashPayment()) {
            // Prefer aggregators for retail payments
            return gatewayRegistry.getAggregatorProviders().stream().findFirst();
        }
        
        // Default to first available aggregator
        return gatewayRegistry.getAggregatorProviders().stream().findFirst();
    }
    
    /**
     * Validate if a gateway supports a payment method
     */
    public boolean validateGatewaySupportsMethod(GatewayProvider gateway, PaymentMethodType methodType) {
        Optional<PaymentGatewayProvider> provider = gatewayRegistry.getProvider(gateway);
        if (provider.isEmpty()) {
            return false;
        }
        
        return provider.get().supportsPaymentMethod(methodType.getCode());
    }
}
