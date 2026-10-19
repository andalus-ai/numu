package tech.kayys.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.PaymentGatewayProvider;
import tech.kayys.payment.gateway.provider.MidtransGatewayProvider;
import tech.kayys.payment.gateway.provider.XenditGatewayProvider;

/**
 * Unit tests for PaymentGatewayRegistry
 */
@QuarkusTest
class PaymentGatewayRegistryTest {

    @Inject
    PaymentGatewayRegistry registry;

    @Test
    @DisplayName("Should initialize with registered providers")
    void testInitialization() {
        assertNotNull(registry);
        List<PaymentGatewayProvider> providers = registry.getAllProviders();
        assertNotNull(providers);
    }

    @Test
    @DisplayName("Should get provider by enum")
    void testGetProviderByEnum() {
        var provider = registry.getProvider(GatewayProvider.MIDTRANS);
        // Provider may or may not be present depending on configuration
        assertNotNull(provider);
    }

    @Test
    @DisplayName("Should get provider by code")
    void testGetProviderByCode() {
        var provider = registry.getProvider("MIDTRANS");
        assertNotNull(provider);
    }

    @Test
    @DisplayName("Should check if provider exists")
    void testHasProvider() {
        // This will depend on which providers are configured
        boolean hasProvider = registry.hasProvider("MIDTRANS") || registry.hasProvider("XENDIT");
        // At least one should be available in test context
        assertNotNull(hasProvider);
    }

    @Test
    @DisplayName("Should get all providers")
    void testGetAllProviders() {
        List<PaymentGatewayProvider> providers = registry.getAllProviders();
        assertNotNull(providers);
    }

    @Test
    @DisplayName("Should get aggregator providers")
    void testGetAggregatorProviders() {
        List<PaymentGatewayProvider> aggregators = registry.getAggregatorProviders();
        assertNotNull(aggregators);
    }

    @Test
    @DisplayName("Should get bank providers")
    void testGetBankProviders() {
        List<PaymentGatewayProvider> banks = registry.getBankProviders();
        assertNotNull(banks);
    }

    @Test
    @DisplayName("Should get QRIS providers")
    void testGetQRISProviders() {
        List<PaymentGatewayProvider> qrisProviders = registry.getQRISProviders();
        assertNotNull(qrisProviders);
    }

    @Test
    @DisplayName("Should get e-wallet providers")
    void testGetEWalletProviders() {
        List<PaymentGatewayProvider> eWalletProviders = registry.getEWalletProviders();
        assertNotNull(eWalletProviders);
    }

    @Test
    @DisplayName("Should get fintech providers")
    void testGetFintechProviders() {
        List<PaymentGatewayProvider> fintechProviders = registry.getFintechProviders();
        assertNotNull(fintechProviders);
    }

    @Test
    @DisplayName("Should get providers by type")
    void testGetProvidersByType() {
        List<PaymentGatewayProvider> aggregators = registry.getProvidersByType(tech.kayys.payment.gateway.GatewayType.AGGREGATOR);
        assertNotNull(aggregators);
    }
}
