package tech.kayys.payment.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.PaymentGatewayProvider;

/**
 * Registry for managing payment gateway providers
 * Allows dynamic registration and lookup of gateway implementations
 */
@ApplicationScoped
public class PaymentGatewayRegistry {
    
    private final Map<String, PaymentGatewayProvider> providersByCode = new ConcurrentHashMap<>();
    private final Map<GatewayProvider, PaymentGatewayProvider> providersByEnum = new ConcurrentHashMap<>();
    
    @Inject
    public PaymentGatewayRegistry(Instance<PaymentGatewayProvider> gatewayProviders) {
        // Auto-register all discovered gateway providers
        for (PaymentGatewayProvider provider : gatewayProviders) {
            registerProvider(provider);
        }
    }
    
    /**
     * Register a payment gateway provider
     */
    public void registerProvider(PaymentGatewayProvider provider) {
        GatewayProvider enumProvider = provider.getProvider();
        providersByEnum.put(enumProvider, provider);
        providersByCode.put(enumProvider.getShortName().toUpperCase(), provider);
        providersByCode.put(enumProvider.name(), provider);
    }
    
    /**
     * Unregister a payment gateway provider
     */
    public void unregisterProvider(GatewayProvider provider) {
        PaymentGatewayProvider removed = providersByEnum.remove(provider);
        if (removed != null) {
            providersByCode.remove(provider.getShortName().toUpperCase());
            providersByCode.remove(provider.name());
        }
    }
    
    /**
     * Get a gateway provider by enum
     */
    public Optional<PaymentGatewayProvider> getProvider(GatewayProvider provider) {
        return Optional.ofNullable(providersByEnum.get(provider));
    }
    
    /**
     * Get a gateway provider by code string (case-insensitive)
     */
    public Optional<PaymentGatewayProvider> getProvider(String providerCode) {
        if (providerCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersByCode.get(providerCode.toUpperCase()));
    }
    
    /**
     * Check if a provider is registered
     */
    public boolean hasProvider(GatewayProvider provider) {
        return providersByEnum.containsKey(provider);
    }
    
    /**
     * Check if a provider is registered by code
     */
    public boolean hasProvider(String providerCode) {
        return providersByCode.containsKey(providerCode.toUpperCase());
    }
    
    /**
     * Get all registered providers
     */
    public List<PaymentGatewayProvider> getAllProviders() {
        return new ArrayList<>(providersByEnum.values());
    }
    
    /**
     * Get all providers of a specific type
     */
    public List<PaymentGatewayProvider> getProvidersByType(tech.kayys.payment.gateway.GatewayType type) {
        List<PaymentGatewayProvider> result = new ArrayList<>();
        for (PaymentGatewayProvider provider : providersByEnum.values()) {
            if (provider.getProvider().getType() == type) {
                result.add(provider);
            }
        }
        return result;
    }
    
    /**
     * Get all aggregator providers
     */
    public List<PaymentGatewayProvider> getAggregatorProviders() {
        return getProvidersByType(tech.kayys.payment.gateway.GatewayType.AGGREGATOR);
    }
    
    /**
     * Get all bank providers
     */
    public List<PaymentGatewayProvider> getBankProviders() {
        return getProvidersByType(tech.kayys.payment.gateway.GatewayType.BANK);
    }
    
    /**
     * Get all e-wallet providers
     */
    public List<PaymentGatewayProvider> getEWalletProviders() {
        return getProvidersByType(tech.kayys.payment.gateway.GatewayType.E_WALLET);
    }
    
    /**
     * Get all QRIS providers
     */
    public List<PaymentGatewayProvider> getQRISProviders() {
        return getProvidersByType(tech.kayys.payment.gateway.GatewayType.QRIS);
    }
    
    /**
     * Get all fintech providers
     */
    public List<PaymentGatewayProvider> getFintechProviders() {
        return getProvidersByType(tech.kayys.payment.gateway.GatewayType.FINTECH);
    }
    
    /**
     * Enable or disable a provider
     */
    public void setProviderEnabled(GatewayProvider provider, boolean enabled) {
        PaymentGatewayProvider gatewayProvider = providersByEnum.get(provider);
        if (gatewayProvider != null) {
            tech.kayys.payment.gateway.GatewayConfig config = gatewayProvider.getConfig();
            config.setEnabled(enabled);
        }
    }
    
    /**
     * Check if a provider is enabled
     */
    public boolean isProviderEnabled(GatewayProvider provider) {
        PaymentGatewayProvider gatewayProvider = providersByEnum.get(provider);
        if (gatewayProvider != null) {
            return gatewayProvider.getConfig().isEnabled();
        }
        return false;
    }
}
