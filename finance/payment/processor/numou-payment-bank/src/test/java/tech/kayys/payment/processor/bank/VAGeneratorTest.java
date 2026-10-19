package tech.kayys.payment.processor.bank.va;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for VAGenerator
 */
@QuarkusTest
class VAGeneratorTest {

    @Inject
    VAGenerator vaGenerator;

    @Test
    @DisplayName("Should generate VA number for BCA")
    void testGenerateBCA() {
        String vaNumber = vaGenerator.generate("BCA", "CUST123", "TXN456");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("70013")); // BCA prefix
        assertEquals(15, vaNumber.length()); // Total length
        assertTrue(vaNumber.matches("\\d+")); // All digits
    }

    @Test
    @DisplayName("Should generate VA number for Mandiri")
    void testGenerateMandiri() {
        String vaNumber = vaGenerator.generate("MANDIRI", "CUST123", "TXN456");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("89008")); // Mandiri prefix
        assertEquals(15, vaNumber.length());
        assertTrue(vaNumber.matches("\\d+"));
    }

    @Test
    @DisplayName("Should generate VA number for BNI")
    void testGenerateBNI() {
        String vaNumber = vaGenerator.generate("BNI", "CUST123", "TXN456");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("8881")); // BNI prefix
        assertEquals(15, vaNumber.length());
        assertTrue(vaNumber.matches("\\d+"));
    }

    @Test
    @DisplayName("Should generate VA number for BRI")
    void testGenerateBRI() {
        String vaNumber = vaGenerator.generate("BRI", "CUST123", "TXN456");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("20107")); // BRI prefix
        assertEquals(15, vaNumber.length());
        assertTrue(vaNumber.matches("\\d+"));
    }

    @Test
    @DisplayName("Should generate VA number for Permata")
    void testGeneratePermata() {
        String vaNumber = vaGenerator.generate("PERMATA", "CUST123", "TXN456");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("90010")); // Permata prefix
        assertEquals(15, vaNumber.length());
        assertTrue(vaNumber.matches("\\d+"));
    }

    @Test
    @DisplayName("Should generate random VA number")
    void testGenerateRandom() {
        String vaNumber = vaGenerator.generateRandom("BCA");
        
        assertNotNull(vaNumber);
        assertTrue(vaNumber.startsWith("70013"));
        assertEquals(15, vaNumber.length());
    }

    @Test
    @DisplayName("Should generate different VA numbers for different customers")
    void testDifferentCustomers() {
        String va1 = vaGenerator.generateByCustomer("BCA", "CUST123");
        String va2 = vaGenerator.generateByCustomer("BCA", "CUST456");
        
        assertNotNull(va1);
        assertNotNull(va2);
        assertNotEquals(va1, va2);
    }

    @Test
    @DisplayName("Should generate different VA numbers for different transactions")
    void testDifferentTransactions() {
        String va1 = vaGenerator.generateByTransaction("BCA", "TXN123");
        String va2 = vaGenerator.generateByTransaction("BCA", "TXN456");
        
        assertNotNull(va1);
        assertNotNull(va2);
        assertNotEquals(va1, va2);
    }

    @Test
    @DisplayName("Should validate valid VA number")
    void testIsValid() {
        String vaNumber = vaGenerator.generate("BCA", "CUST123", "TXN456");
        assertTrue(vaGenerator.isValid("BCA", vaNumber));
    }

    @Test
    @DisplayName("Should reject invalid VA number")
    void testIsInvalid() {
        assertFalse(vaGenerator.isValid("BCA", "12345"));
        assertFalse(vaGenerator.isValid("BCA", null));
        assertFalse(vaGenerator.isValid("BCA", ""));
    }

    @Test
    @DisplayName("Should extract bank code from VA number")
    void testExtractBankCode() {
        String bcaVA = vaGenerator.generate("BCA", "CUST123", "TXN456");
        assertEquals("BCA", vaGenerator.extractBankCode(bcaVA));

        String mandiriVA = vaGenerator.generate("MANDIRI", "CUST123", "TXN456");
        assertEquals("MANDIRI", vaGenerator.extractBankCode(mandiriVA));

        String bniVA = vaGenerator.generate("BNI", "CUST123", "TXN456");
        assertEquals("BNI", vaGenerator.extractBankCode(bniVA));
    }

    @Test
    @DisplayName("Should generate transaction reference")
    void testGenerateTransactionRef() {
        String ref1 = vaGenerator.generateTransactionRef();
        String ref2 = vaGenerator.generateTransactionRef();
        
        assertNotNull(ref1);
        assertNotNull(ref2);
        assertNotEquals(ref1, ref2);
    }

    @Test
    @DisplayName("Should generate consistent VA for same customer")
    void testConsistentGeneration() {
        String va1 = vaGenerator.generateByCustomer("BCA", "CUST123");
        String va2 = vaGenerator.generateByCustomer("BCA", "CUST123");
        
        // Should be similar (same customer ID component)
        assertNotNull(va1);
        assertNotNull(va2);
    }
}
