package tech.kayys.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Unit tests for PaymentMethodRegistry
 */
@QuarkusTest
class PaymentMethodRegistryTest {

    @Inject
    PaymentMethodRegistry registry;

    @Test
    @DisplayName("Should initialize with registered processors")
    void testInitialization() {
        assertNotNull(registry);
    }

    @Test
    @DisplayName("Should get processor by payment method type")
    void testGetProcessorByType() {
        var processor = registry.getProcessor(PaymentMethodType.BANK_TRANSFER);
        assertTrue(processor.isPresent(), "Bank transfer processor should be registered");
    }

    @Test
    @DisplayName("Should get QRIS processor")
    void testGetQRISProcessor() {
        var processor = registry.getProcessor(PaymentMethodType.QRIS);
        assertTrue(processor.isPresent(), "QRIS processor should be registered");
    }

    @Test
    @DisplayName("Should get e-wallet processor")
    void testGetEWalletProcessor() {
        var processor = registry.getProcessor(PaymentMethodType.E_WALLET);
        assertTrue(processor.isPresent(), "E-wallet processor should be registered");
    }

    @Test
    @DisplayName("Should get fintech processor")
    void testGetFintechProcessor() {
        var processor = registry.getProcessor(PaymentMethodType.FINTECH);
        assertTrue(processor.isPresent(), "Fintech processor should be registered");
    }

    @Test
    @DisplayName("Should get processor by code")
    void testGetProcessorByCode() {
        var processor = registry.getProcessor("QRIS");
        assertTrue(processor.isPresent());

        processor = registry.getProcessor("qris");
        assertTrue(processor.isPresent(), "Code lookup should be case-insensitive");
    }

    @Test
    @DisplayName("Should check if processor exists")
    void testHasProcessor() {
        assertTrue(registry.hasProcessor(PaymentMethodType.BANK_TRANSFER));
        assertTrue(registry.hasProcessor("QRIS"));
    }

    @Test
    @DisplayName("Should get all processors")
    void testGetAllProcessors() {
        List<PaymentMethodProcessor> processors = registry.getAllProcessors();
        assertNotNull(processors);
        assertFalse(processors.isEmpty(), "Should have at least one processor registered");
    }

    @Test
    @DisplayName("Should get bank transfer processors")
    void testGetBankTransferProcessors() {
        List<PaymentMethodProcessor> processors = registry.getBankTransferProcessors();
        assertNotNull(processors);
    }

    @Test
    @DisplayName("Should get QRIS processors")
    void testGetQRISProcessors() {
        List<PaymentMethodProcessor> processors = registry.getQRISProcessors();
        assertNotNull(processors);
    }

    @Test
    @DisplayName("Should get e-wallet processors")
    void testGetEWalletProcessors() {
        List<PaymentMethodProcessor> processors = registry.getEWalletProcessors();
        assertNotNull(processors);
    }

    @Test
    @DisplayName("Should get fintech processors")
    void testGetFintechProcessors() {
        List<PaymentMethodProcessor> processors = registry.getFintechProcessors();
        assertNotNull(processors);
    }

    @Test
    @DisplayName("Should get card processors")
    void testGetCardProcessors() {
        List<PaymentMethodProcessor> processors = registry.getCardProcessors();
        assertNotNull(processors);
    }

    @Test
    @DisplayName("Should get available payment methods")
    void testGetAvailablePaymentMethods() {
        List<PaymentMethodType> methods = registry.getAvailablePaymentMethods();
        assertNotNull(methods);
        assertFalse(methods.isEmpty(), "Should have at least one payment method available");
    }

    @Test
    @DisplayName("Processor should support correct payment method type")
    void testProcessorSupportsType() {
        var processor = registry.getProcessor(PaymentMethodType.BANK_TRANSFER);
        processor.ifPresent(p -> {
            assertTrue(p.supports(PaymentMethodType.BANK_TRANSFER));
            assertFalse(p.supports(PaymentMethodType.QRIS));
        });
    }
}
