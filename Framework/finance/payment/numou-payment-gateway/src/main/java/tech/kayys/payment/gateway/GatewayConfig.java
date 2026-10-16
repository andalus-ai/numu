package tech.kayys.payment.gateway;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for payment gateway provider
 */
public class GatewayConfig {
    private String providerCode;
    private String apiKey;
    private String secretKey;
    private String clientId;
    private String clientSecret;
    private String merchantId;
    private String environment; // SANDBOX, PRODUCTION
    private String baseUrl;
    private String apiVersion;
    private Map<String, String> additionalConfig;
    private boolean enabled;
    private int timeout;
    private int retryAttempts;
    
    public GatewayConfig() {
        this.environment = "SANDBOX";
        this.enabled = true;
        this.timeout = 30000; // 30 seconds
        this.retryAttempts = 3;
        this.additionalConfig = new HashMap<>();
    }
    
    // Getters and Setters
    public String getProviderCode() {
        return providerCode;
    }
    
    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getSecretKey() {
        return secretKey;
    }
    
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public Map<String, String> getAdditionalConfig() {
        return additionalConfig;
    }
    
    public void setAdditionalConfig(Map<String, String> additionalConfig) {
        this.additionalConfig = additionalConfig;
    }
    
    public void addAdditionalConfig(String key, String value) {
        this.additionalConfig.put(key, value);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getRetryAttempts() {
        return retryAttempts;
    }
    
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
    
    public boolean isSandbox() {
        return "SANDBOX".equalsIgnoreCase(environment);
    }
    
    public boolean isProduction() {
        return "PRODUCTION".equalsIgnoreCase(environment);
    }
}
