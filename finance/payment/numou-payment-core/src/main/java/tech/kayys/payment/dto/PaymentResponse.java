package tech.kayys.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.Payment.PaymentStatus;

/**
 * Response DTO for payment operations
 */
public class PaymentResponse {
    
    private Long id;
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("external_order_id")
    private String externalOrderId;
    
    @JsonProperty("payment_method")
    private String paymentMethod;
    
    @JsonProperty("payment_method_display")
    private String paymentMethodDisplay;
    
    @JsonProperty("gateway_provider")
    private String gatewayProvider;
    
    @JsonProperty("gateway_transaction_id")
    private String gatewayTransactionId;
    
    private BigDecimal amount;
    
    private String currency;
    
    private String status;
    
    @JsonProperty("payment_url")
    private String paymentUrl;
    
    @JsonProperty("qr_code_url")
    private String qrCodeUrl;
    
    @JsonProperty("qr_code_string")
    private String qrCodeString;
    
    @JsonProperty("virtual_account_number")
    private String virtualAccountNumber;
    
    @JsonProperty("bank_code")
    private String bankCode;
    
    @JsonProperty("expiry_time")
    private LocalDateTime expiryTime;
    
    @JsonProperty("payment_date")
    private LocalDateTime paymentDate;
    
    @JsonProperty("customer_name")
    private String customerName;
    
    @JsonProperty("customer_email")
    private String customerEmail;
    
    @JsonProperty("customer_phone")
    private String customerPhone;
    
    private String notes;
    
    private Map<String, String> metadata;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getPaymentMethodDisplay() {
        return paymentMethodDisplay;
    }
    
    public void setPaymentMethodDisplay(String paymentMethodDisplay) {
        this.paymentMethodDisplay = paymentMethodDisplay;
    }
    
    public String getGatewayProvider() {
        return gatewayProvider;
    }
    
    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }
    
    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
    
    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getPaymentUrl() {
        return paymentUrl;
    }
    
    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }
    
    public String getQrCodeUrl() {
        return qrCodeUrl;
    }
    
    public void setQrCodeUrl(String qrCodeUrl) {
        this.qrCodeUrl = qrCodeUrl;
    }
    
    public String getQrCodeString() {
        return qrCodeString;
    }
    
    public void setQrCodeString(String qrCodeString) {
        this.qrCodeString = qrCodeString;
    }
    
    public String getVirtualAccountNumber() {
        return virtualAccountNumber;
    }
    
    public void setVirtualAccountNumber(String virtualAccountNumber) {
        this.virtualAccountNumber = virtualAccountNumber;
    }
    
    public String getBankCode() {
        return bankCode;
    }
    
    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }
    
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }
    
    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
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
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Create PaymentResponse from Payment entity
     */
    public static PaymentResponse fromEntity(tech.kayys.payment.Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.id);
        response.setTransactionId(payment.transactionId);
        response.setPaymentMethod(payment.paymentMethod != null ? payment.paymentMethod.name() : null);
        response.setPaymentMethodDisplay(payment.paymentMethod != null ? payment.paymentMethod.getDisplayName() : null);
        response.setGatewayProvider(payment.gatewayProvider);
        response.setGatewayTransactionId(payment.gatewayTransactionId);
        response.setAmount(payment.amount);
        response.setCurrency("IDR");
        response.setStatus(payment.status != null ? payment.status.name() : null);
        response.setPaymentUrl(payment.paymentUrl);
        response.setQrCodeUrl(payment.qrCodeUrl);
        response.setQrCodeString(payment.qrCodeString);
        response.setVirtualAccountNumber(payment.virtualAccountNumber);
        response.setBankCode(payment.bankCode);
        response.setExpiryTime(payment.expiryTime);
        response.setPaymentDate(payment.paymentDate);
        response.setCustomerName(payment.customerName);
        response.setCustomerEmail(payment.customerEmail);
        response.setCustomerPhone(payment.customerPhone);
        response.setNotes(payment.notes);
        response.setMetadata(payment.metadata);
        
        if (payment.invoice != null && payment.invoice.id != null) {
            response.setExternalOrderId("INV-" + payment.invoice.id);
        }
        
        return response;
    }
}
