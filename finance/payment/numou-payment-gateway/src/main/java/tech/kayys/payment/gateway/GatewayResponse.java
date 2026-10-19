package tech.kayys.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Response object from payment gateway operations
 */
public class GatewayResponse {
    private boolean success;
    private String transactionId;
    private String externalTransactionId;
    private String statusCode;
    private String statusMessage;
    private PaymentStatus paymentStatus;
    private String paymentUrl;
    private String qrCodeUrl;
    private String qrCodeString;
    private String virtualAccountNumber;
    private String bankCode;
    private String cardNumber;
    private String cardType;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionTime;
    private LocalDateTime expiryTime;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> metadata;
    
    public GatewayResponse() {
        this.currency = "IDR";
        this.metadata = new HashMap<>();
    }
    
    public enum PaymentStatus {
        PENDING("Pending"),
        SUCCESS("Success"),
        FAILED("Failed"),
        CANCELLED("Cancelled"),
        EXPIRED("Expired"),
        REFUNDED("Refunded"),
        PARTIALLY_REFUNDED("Partially Refunded"),
        CHALLENGE("Challenge");
        
        private final String displayName;
        
        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
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
    
    public String getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
    
    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }
    
    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
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
    
    public String getCardNumber() {
        return cardNumber;
    }
    
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    public String getCardType() {
        return cardType;
    }
    
    public void setCardType(String cardType) {
        this.cardType = cardType;
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
    
    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }
    
    public void setTransactionTime(LocalDateTime transactionTime) {
        this.transactionTime = transactionTime;
    }
    
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
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
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final GatewayResponse response = new GatewayResponse();
        
        public Builder success(boolean success) {
            response.setSuccess(success);
            return this;
        }
        
        public Builder transactionId(String transactionId) {
            response.setTransactionId(transactionId);
            return this;
        }
        
        public Builder externalTransactionId(String externalTransactionId) {
            response.setExternalTransactionId(externalTransactionId);
            return this;
        }

        public Builder currency(String currency) {
            response.setCurrency(currency);
            return this;
        }
        
        public Builder statusCode(String statusCode) {
            response.setStatusCode(statusCode);
            return this;
        }
        
        public Builder statusMessage(String statusMessage) {
            response.setStatusMessage(statusMessage);
            return this;
        }
        
        public Builder paymentStatus(PaymentStatus paymentStatus) {
            response.setPaymentStatus(paymentStatus);
            return this;
        }
        
        public Builder paymentUrl(String paymentUrl) {
            response.setPaymentUrl(paymentUrl);
            return this;
        }
        
        public Builder qrCodeUrl(String qrCodeUrl) {
            response.setQrCodeUrl(qrCodeUrl);
            return this;
        }
        
        public Builder qrCodeString(String qrCodeString) {
            response.setQrCodeString(qrCodeString);
            return this;
        }
        
        public Builder virtualAccountNumber(String vaNumber) {
            response.setVirtualAccountNumber(vaNumber);
            return this;
        }
        
        public Builder bankCode(String bankCode) {
            response.setBankCode(bankCode);
            return this;
        }
        
        public Builder amount(BigDecimal amount) {
            response.setAmount(amount);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            response.setErrorMessage(errorMessage);
            return this;
        }
        
        public Builder errorCode(String errorCode) {
            response.setErrorCode(errorCode);
            return this;
        }
        
        public Builder expiryTime(LocalDateTime expiryTime) {
            response.setExpiryTime(expiryTime);
            return this;
        }
        
        public GatewayResponse build() {
            return response;
        }
    }
}
