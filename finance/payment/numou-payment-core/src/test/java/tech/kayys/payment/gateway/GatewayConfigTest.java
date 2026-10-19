package tech.kayys.payment.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GatewayConfig
 */
class GatewayConfigTest {

    private GatewayConfig config;

    @BeforeEach
    void setUp() {
        config = new GatewayConfig();
    }

    @Test
    @DisplayName("Should create config with default values")
    void testDefaultValues() {
        assertNotNull(config);
        assertEquals("SANDBOX", config.getEnvironment());
        assertTrue(config.isEnabled());
        assertEquals(30000, config.getTimeout());
        assertEquals(3, config.getRetryAttempts());
        assertNotNull(config.getAdditionalConfig());
    }

    @Test
    @DisplayName("Should set provider code")
    void testProviderCode() {
        config.setProviderCode("MIDTRANS");
        assertEquals("MIDTRANS", config.getProviderCode());
    }

    @Test
    @DisplayName("Should set API credentials")
    void testApiCredentials() {
        config.setApiKey("server-key-123");
        config.setSecretKey("secret-key-456");
        config.setClientId("client-id-789");
        config.setClientSecret("client-secret-012");

        assertEquals("server-key-123", config.getApiKey());
        assertEquals("secret-key-456", config.getSecretKey());
        assertEquals("client-id-789", config.getClientId());
        assertEquals("client-secret-012", config.getClientSecret());
    }

    @Test
    @DisplayName("Should set merchant ID")
    void testMerchantId() {
        config.setMerchantId("MERCHANT-12345");
        assertEquals("MERCHANT-12345", config.getMerchantId());
    }

    @Test
    @DisplayName("Should set environment")
    void testEnvironment() {
        config.setEnvironment("PRODUCTION");
        assertEquals("PRODUCTION", config.getEnvironment());
        assertTrue(config.isProduction());
        assertFalse(config.isSandbox());

        config.setEnvironment("SANDBOX");
        assertEquals("SANDBOX", config.getEnvironment());
        assertTrue(config.isSandbox());
        assertFalse(config.isProduction());
    }

    @Test
    @DisplayName("Should set base URL and API version")
    void testUrlAndVersion() {
        config.setBaseUrl("https://api.midtrans.com");
        config.setApiVersion("v2");

        assertEquals("https://api.midtrans.com", config.getBaseUrl());
        assertEquals("v2", config.getApiVersion());
    }

    @Test
    @DisplayName("Should add additional configuration")
    void testAdditionalConfig() {
        config.addAdditionalConfig("custom_key", "custom_value");
        config.addAdditionalConfig("timeout", "60000");

        assertEquals("custom_value", config.getAdditionalConfig().get("custom_key"));
        assertEquals("60000", config.getAdditionalConfig().get("timeout"));
    }

    @Test
    @DisplayName("Should set additional config map")
    void testSetAdditionalConfig() {
        Map<String, String> additionalConfig = new HashMap<>();
        additionalConfig.put("key1", "value1");
        additionalConfig.put("key2", "value2");

        config.setAdditionalConfig(additionalConfig);

        assertEquals(additionalConfig, config.getAdditionalConfig());
    }

    @Test
    @DisplayName("Should enable and disable config")
    void testEnabled() {
        config.setEnabled(false);
        assertFalse(config.isEnabled());

        config.setEnabled(true);
        assertTrue(config.isEnabled());
    }

    @Test
    @DisplayName("Should set timeout and retry attempts")
    void testTimeoutAndRetry() {
        config.setTimeout(60000);
        config.setRetryAttempts(5);

        assertEquals(60000, config.getTimeout());
        assertEquals(5, config.getRetryAttempts());
    }

    @Test
    @DisplayName("Should build complete config")
    void testCompleteConfig() {
        config.setProviderCode("XENDIT");
        config.setApiKey("xnd_key_123");
        config.setSecretKey("xnd_secret_456");
        config.setMerchantId("MERCHANT-001");
        config.setEnvironment("PRODUCTION");
        config.setBaseUrl("https://api.xendit.co");
        config.setApiVersion("v1");
        config.setEnabled(true);
        config.setTimeout(45000);
        config.setRetryAttempts(2);
        config.addAdditionalConfig("feature_flag", "enabled");

        assertEquals("XENDIT", config.getProviderCode());
        assertEquals("xnd_key_123", config.getApiKey());
        assertEquals("PRODUCTION", config.getEnvironment());
        assertTrue(config.isProduction());
        assertEquals(45000, config.getTimeout());
        assertEquals("enabled", config.getAdditionalConfig().get("feature_flag"));
    }
}
