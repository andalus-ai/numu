package tech.kayys.payment.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GatewayProvider enumeration
 */
class GatewayProviderTest {

    @Test
    @DisplayName("Should identify Midtrans as aggregator")
    void testMidtrans() {
        GatewayProvider provider = GatewayProvider.MIDTRANS;
        assertEquals("Midtrans", provider.getShortName());
        assertEquals("Midtrans Payment Gateway", provider.getFullName());
        assertEquals(GatewayType.AGGREGATOR, provider.getType());
        assertTrue(provider.isAggregator());
        assertFalse(provider.isBank());
    }

    @Test
    @DisplayName("Should identify Xendit as aggregator")
    void testXendit() {
        GatewayProvider provider = GatewayProvider.XENDIT;
        assertEquals("Xendit", provider.getShortName());
        assertTrue(provider.isAggregator());
    }

    @Test
    @DisplayName("Should identify BCA as bank")
    void testBCA() {
        GatewayProvider provider = GatewayProvider.BCA;
        assertEquals("BCA", provider.getShortName());
        assertEquals("Bank Central Asia", provider.getFullName());
        assertEquals(GatewayType.BANK, provider.getType());
        assertTrue(provider.isBank());
        assertFalse(provider.isAggregator());
    }

    @Test
    @DisplayName("Should identify Mandiri as bank")
    void testMandiri() {
        GatewayProvider provider = GatewayProvider.MANDIRI;
        assertTrue(provider.isBank());
    }

    @Test
    @DisplayName("Should identify OVO as e-wallet")
    void testOVO() {
        GatewayProvider provider = GatewayProvider.OVO;
        assertEquals(GatewayType.E_WALLET, provider.getType());
        assertTrue(provider.isEWallet());
    }

    @Test
    @DisplayName("Should identify QRIS providers")
    void testQRISProviders() {
        assertTrue(GatewayProvider.QRIS_MIDTRANS.isQRIS());
        assertTrue(GatewayProvider.QRIS_XENDIT.isQRIS());
        assertTrue(GatewayProvider.QRIS_DOKU.isQRIS());
        assertTrue(GatewayProvider.QRIS_BI.isQRIS());
    }

    @Test
    @DisplayName("Should identify fintech providers")
    void testFintechProviders() {
        assertTrue(GatewayProvider.KREDIVO.isFintech());
        assertTrue(GatewayProvider.AKULAKU.isFintech());
        assertTrue(GatewayProvider.INDODANA.isFintech());
    }

    @Test
    @DisplayName("Should identify card processors")
    void testCardProcessors() {
        assertTrue(GatewayProvider.VISA.isCard());
        assertTrue(GatewayProvider.MASTERCARD.isCard());
        assertTrue(GatewayProvider.JCB.isCard());
        assertTrue(GatewayProvider.AMEX.isCard());
    }

    @Test
    @DisplayName("Should identify retail providers")
    void testRetailProviders() {
        assertTrue(GatewayProvider.ALFAMART.isRetail());
        assertTrue(GatewayProvider.INDOMARET.isRetail());
    }

    @Test
    @DisplayName("Should have correct type for all providers")
    void testAllProvidersHaveType() {
        for (GatewayProvider provider : GatewayProvider.values()) {
            assertNotNull(provider.getType(), "Provider " + provider.name() + " should have a type");
        }
    }

    @Test
    @DisplayName("Should have correct names for all providers")
    void testAllProvidersHaveNames() {
        for (GatewayProvider provider : GatewayProvider.values()) {
            assertNotNull(provider.getShortName(), "Provider " + provider.name() + " should have short name");
            assertNotNull(provider.getFullName(), "Provider " + provider.name() + " should have full name");
        }
    }

    @Test
    @DisplayName("Provider type methods should be mutually exclusive")
    void testTypeExclusivity() {
        GatewayProvider midtrans = GatewayProvider.MIDTRANS;
        assertTrue(midtrans.isAggregator());
        assertFalse(midtrans.isBank());
        assertFalse(midtrans.isEWallet());
        assertFalse(midtrans.isQRIS());
        assertFalse(midtrans.isFintech());
        assertFalse(midtrans.isCard());
        assertFalse(midtrans.isRetail());

        GatewayProvider bca = GatewayProvider.BCA;
        assertTrue(bca.isBank());
        assertFalse(bca.isAggregator());
    }
}
