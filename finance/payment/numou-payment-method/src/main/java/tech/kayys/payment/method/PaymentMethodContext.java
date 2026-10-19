package tech.kayys.payment.method;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object containing payment processing information
 */
public class PaymentMethodContext {
    private String transactionId;
    private String externalOrderId;
    private BigDecimal amount;
    private String currency;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private PaymentMethodType paymentMethod;
    private String gatewayProvider;
    private Map<String, Object> metadata;
    private String callbackUrl;
    private String returnUrl;
    
    public PaymentMethodContext() {
        this.currency = "IDR";
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public String getExternalOrderId() {
        return externalOrderId;
    }
    
    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
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
    
    public PaymentMethodType getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(PaymentMethodType paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getGatewayProvider() {
        return gatewayProvider;
    }
    
    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
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
}
