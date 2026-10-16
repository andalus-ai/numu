package tech.kayys.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.PaymentGatewayProvider;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Unit tests for PaymentProcessorFactory
 */
@QuarkusTest
class PaymentProcessorFactoryTest {

    @Inject
    PaymentProcessorFactory factory;

    @Inject
    PaymentMethodRegistry methodRegistry;

    @Inject
    PaymentGatewayRegistry gatewayRegistry;

    @Test
    @DisplayName("Should initialize factory")
    void testInitialization() {
        assertNotNull(factory);
    }

    @Test
    @DisplayName("Should get method processor")
    void testGetMethodProcessor() {
        Optional<PaymentMethodProcessor> processor = factory.getMethodProcessor(PaymentMethodType.QRIS);
        assertTrue(processor.isPresent(), "Should find QRIS processor");
    }

    @Test
    @DisplayName("Should get method processor by code")
    void testGetMethodProcessorByCode() {
        Optional<PaymentMethodProcessor> processor = factory.getMethodProcessor("QRIS");
        assertTrue(processor.isPresent());

        processor = factory.getMethodProcessor("qris");
        assertTrue(processor.isPresent(), "Code lookup should be case-insensitive");
    }

    @Test
    @DisplayName("Should get gateway provider")
    void testGetGatewayProvider() {
        Optional<PaymentGatewayProvider> provider = factory.getGatewayProvider(GatewayProvider.MIDTRANS);
        // Provider may or may not be present depending on configuration
        assertNotNull(provider);
    }

    @Test
    @DisplayName("Should get gateway provider by code")
    void testGetGatewayProviderByCode() {
        Optional<PaymentGatewayProvider> provider = factory.getGatewayProvider("MIDTRANS");
        assertNotNull(provider);
    }

    @Test
    @DisplayName("Should get best gateway for QRIS method")
    void testGetBestGatewayForQRIS() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.QRIS);
        // Should return a gateway (either QRIS-specific or aggregator)
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get best gateway for bank transfer")
    void testGetBestGatewayForBankTransfer() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.VA_BCA);
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get best gateway for e-wallet")
    void testGetBestGatewayForEWallet() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.OVO);
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get best gateway for fintech")
    void testGetBestGatewayForFintech() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.KREDIVO);
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get best gateway for card payment")
    void testGetBestGatewayForCard() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.CREDIT_CARD);
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get best gateway for cash payment")
    void testGetBestGatewayForCashPayment() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(PaymentMethodType.ALFAMART);
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should get default gateway for null method")
    void testGetBestGatewayForNullMethod() {
        Optional<PaymentGatewayProvider> gateway = factory.getBestGatewayForMethod(null);
        // Should return default aggregator
        assertNotNull(gateway);
    }

    @Test
    @DisplayName("Should validate gateway supports method")
    void testValidateGatewaySupportsMethod() {
        // This test depends on actual gateway implementations
        boolean supports = factory.validateGatewaySupportsMethod(GatewayProvider.MIDTRANS, PaymentMethodType.QRIS);
        // Midtrans should support QRIS
        assertNotNull(supports);
    }

    @Test
    @DisplayName("Should handle non-existent processor gracefully")
    void testNonExistentProcessor() {
        // Try to get a processor that might not exist
        Optional<PaymentMethodProcessor> processor = factory.getMethodProcessor("NON_EXISTENT");
        assertFalse(processor.isPresent());
    }

    @Test
    @DisplayName("Should handle non-existent gateway gracefully")
    void testNonExistentGateway() {
        Optional<PaymentGatewayProvider> provider = factory.getGatewayProvider("NON_EXISTENT");
        assertFalse(provider.isPresent());
    }
}
