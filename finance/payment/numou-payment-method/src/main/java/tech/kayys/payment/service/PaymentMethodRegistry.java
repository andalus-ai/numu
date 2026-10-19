package tech.kayys.payment.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Registry for managing payment method processors
 * Allows dynamic registration and lookup of processor implementations
 */
@ApplicationScoped
public class PaymentMethodRegistry {
    
    private final Map<PaymentMethodType, PaymentMethodProcessor> processorsByType = new ConcurrentHashMap<>();
    private final Map<String, PaymentMethodProcessor> processorsByCode = new ConcurrentHashMap<>();
    
    @Inject
    public PaymentMethodRegistry(Instance<PaymentMethodProcessor> methodProcessors) {
        // Auto-register all discovered method processors
        for (PaymentMethodProcessor processor : methodProcessors) {
            registerProcessor(processor);
        }
    }
    
    /**
     * Register a payment method processor
     */
    public void registerProcessor(PaymentMethodProcessor processor) {
        PaymentMethodType type = processor.getPaymentMethodType();
        processorsByType.put(type, processor);
        processorsByCode.put(type.getCode(), processor);
        processorsByCode.put(type.name(), processor);
    }
    
    /**
     * Unregister a payment method processor
     */
    public void unregisterProcessor(PaymentMethodType type) {
        PaymentMethodProcessor removed = processorsByType.remove(type);
        if (removed != null) {
            processorsByCode.remove(type.getCode());
            processorsByCode.remove(type.name());
        }
    }
    
    /**
     * Get a processor by payment method type
     */
    public Optional<PaymentMethodProcessor> getProcessor(PaymentMethodType type) {
        return Optional.ofNullable(processorsByType.get(type));
    }
    
    /**
     * Get a processor by payment method code string
     */
    public Optional<PaymentMethodProcessor> getProcessor(String methodCode) {
        if (methodCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(processorsByCode.get(methodCode.toUpperCase()));
    }
    
    /**
     * Check if a processor is registered for a payment method
     */
    public boolean hasProcessor(PaymentMethodType type) {
        return processorsByType.containsKey(type);
    }
    
    /**
     * Check if a processor is registered for a method code
     */
    public boolean hasProcessor(String methodCode) {
        return processorsByCode.containsKey(methodCode.toUpperCase());
    }
    
    /**
     * Get all registered processors
     */
    public List<PaymentMethodProcessor> getAllProcessors() {
        return List.copyOf(processorsByType.values());
    }
    
    /**
     * Get all bank transfer processors
     */
    public List<PaymentMethodProcessor> getBankTransferProcessors() {
        return processorsByType.entrySet().stream()
            .filter(e -> e.getKey().isBankTransfer())
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get all QRIS processors
     */
    public List<PaymentMethodProcessor> getQRISProcessors() {
        return processorsByType.entrySet().stream()
            .filter(e -> e.getKey().isQRIS())
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get all e-wallet processors
     */
    public List<PaymentMethodProcessor> getEWalletProcessors() {
        return processorsByType.entrySet().stream()
            .filter(e -> e.getKey().isEWallet())
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get all fintech processors
     */
    public List<PaymentMethodProcessor> getFintechProcessors() {
        return processorsByType.entrySet().stream()
            .filter(e -> e.getKey().isFintech())
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get all card processors
     */
    public List<PaymentMethodProcessor> getCardProcessors() {
        return processorsByType.entrySet().stream()
            .filter(e -> e.getKey().isCard())
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get all available payment method types
     */
    public List<PaymentMethodType> getAvailablePaymentMethods() {
        return List.copyOf(processorsByType.keySet());
    }
}
