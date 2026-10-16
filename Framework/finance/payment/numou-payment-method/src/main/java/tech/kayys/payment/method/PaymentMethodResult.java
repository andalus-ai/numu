package tech.kayys.payment.method;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Result object from payment method processing
 */
public class PaymentMethodResult {
    private boolean success;
    private String transactionId;
    private String externalTransactionId;
    private PaymentStatus status;
    private String paymentUrl;
    private String qrCodeUrl;
    private String qrCodeString;
    private String virtualAccountNumber;
    private String bankCode;
    private BigDecimal amount;
    private String currency;
    private String errorMessage;
    private String errorCode;
    private LocalDateTime expiryTime;
    private Map<String, Object> metadata;
    
    public PaymentMethodResult() {
        this.currency = "IDR";
        this.metadata = new HashMap<>();
    }
    
    public enum PaymentStatus {
        PENDING,
        SUCCESS,
        FAILED,
        CANCELLED,
        EXPIRED,
        REFUNDED,
        PARTIALLY_REFUNDED
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public String getExternalTransactionId() {
        return externalTransactionId;
    }
    
    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }
    
    public PaymentStatus getStatus() {
        return status;
    }
    
    public void setStatus(PaymentStatus status) {
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
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
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
    
    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final PaymentMethodResult result = new PaymentMethodResult();
        
        public Builder success(boolean success) {
            result.setSuccess(success);
            return this;
        }
        
        public Builder transactionId(String transactionId) {
            result.setTransactionId(transactionId);
            return this;
        }
        
        public Builder externalTransactionId(String externalTransactionId) {
            result.setExternalTransactionId(externalTransactionId);
            return this;
        }
        
        public Builder status(PaymentStatus status) {
            result.setStatus(status);
            return this;
        }
        
        public Builder paymentUrl(String paymentUrl) {
            result.setPaymentUrl(paymentUrl);
            return this;
        }
        
        public Builder qrCodeUrl(String qrCodeUrl) {
            result.setQrCodeUrl(qrCodeUrl);
            return this;
        }
        
        public Builder qrCodeString(String qrCodeString) {
            result.setQrCodeString(qrCodeString);
            return this;
        }
        
        public Builder virtualAccountNumber(String vaNumber) {
            result.setVirtualAccountNumber(vaNumber);
            return this;
        }
        
        public Builder bankCode(String bankCode) {
            result.setBankCode(bankCode);
            return this;
        }
        
        public Builder amount(BigDecimal amount) {
            result.setAmount(amount);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            result.setErrorMessage(errorMessage);
            return this;
        }
        
        public Builder errorCode(String errorCode) {
            result.setErrorCode(errorCode);
            return this;
        }
        
        public Builder expiryTime(LocalDateTime expiryTime) {
            result.setExpiryTime(expiryTime);
            return this;
        }
        
        public Builder currency(String currency) {
            result.setCurrency(currency);
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            result.setMetadata(metadata != null ? metadata : new HashMap<>());
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            result.addMetadata(key, value);
            return this;
        }
        
        public PaymentMethodResult build() {
            return result;
        }
    }
}
