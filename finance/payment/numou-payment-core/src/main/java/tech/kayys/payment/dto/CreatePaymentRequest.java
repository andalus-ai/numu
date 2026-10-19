package tech.kayys.payment.dto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import tech.kayys.payment.method.PaymentMethodType;

/**
 * Request DTO for creating a payment
 */
public class CreatePaymentRequest {
    
    @JsonProperty("external_order_id")
    private String externalOrderId;
    
    @JsonProperty("payment_method")
    private String paymentMethod;
    
    @JsonProperty("gateway_provider")
    private String gatewayProvider;
    
    private BigDecimal amount;
    
    private String currency = "IDR";
    
    @JsonProperty("customer_name")
    private String customerName;
    
    @JsonProperty("customer_email")
    private String customerEmail;
    
    @JsonProperty("customer_phone")
    private String customerPhone;
    
    private String description;
    
    @JsonProperty("callback_url")
    private String callbackUrl;
    
    @JsonProperty("return_url")
    private String returnUrl;
    
    private Map<String, Object> metadata = new HashMap<>();
    
    // Getters and Setters
    public String getExternalOrderId() {
        return externalOrderId;
    }
    
    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public PaymentMethodType getPaymentMethodType() {
        if (paymentMethod == null) {
            return null;
        }
        try {
            return PaymentMethodType.valueOf(paymentMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to find by code
            for (PaymentMethodType type : PaymentMethodType.values()) {
                if (type.getCode().equalsIgnoreCase(paymentMethod)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    public String getGatewayProvider() {
        return gatewayProvider;
    }
    
    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getCustomerName() {
        return customerName;
    }
    
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
    
    public String getCustomerEmail() {
        return customerEmail;
    }
    
    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }
    
    public String getCustomerPhone() {
        return customerPhone;
    }
    
    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCallbackUrl() {
        return callbackUrl;
    }
    
    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
    
    public String getReturnUrl() {
        return returnUrl;
    }
    
    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
}
