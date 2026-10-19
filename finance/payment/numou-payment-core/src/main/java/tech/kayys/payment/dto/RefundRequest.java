package tech.kayys.payment.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for refund operations
 */
public class RefundRequest {
    
    @JsonProperty("payment_id")
    private Long paymentId;
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    private BigDecimal amount;
    
    private String reason;
    
    private String notes;
    
    // Getters and Setters
    public Long getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    /**
     * Check if this is a full refund
     */
    public boolean isFullRefund() {
        return amount == null;
    }
}
