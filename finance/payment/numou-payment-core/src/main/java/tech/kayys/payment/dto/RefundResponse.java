package tech.kayys.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for refund operations
 */
public class RefundResponse {
    
    private Long id;
    
    @JsonProperty("refund_id")
    private String refundId;
    
    @JsonProperty("payment_id")
    private Long paymentId;
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    private BigDecimal amount;
    
    private String currency = "IDR";
    
    private String reason;
    
    private String status;
    
    @JsonProperty("gateway_refund_id")
    private String gatewayRefundId;
    
    @JsonProperty("gateway_provider")
    private String gatewayProvider;
    
    @JsonProperty("refund_date")
    private LocalDateTime refundDate;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    private String notes;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getRefundId() {
        return refundId;
    }
    
    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }
    
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
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getGatewayRefundId() {
        return gatewayRefundId;
    }
    
    public void setGatewayRefundId(String gatewayRefundId) {
        this.gatewayRefundId = gatewayRefundId;
    }
    
    public String getGatewayProvider() {
        return gatewayProvider;
    }
    
    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }
    
    public LocalDateTime getRefundDate() {
        return refundDate;
    }
    
    public void setRefundDate(LocalDateTime refundDate) {
        this.refundDate = refundDate;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    /**
     * Create RefundResponse from PaymentRefund entity
     */
    public static RefundResponse fromEntity(tech.kayys.payment.PaymentRefund refund) {
        RefundResponse response = new RefundResponse();
        response.setId(refund.id);
        response.setRefundId(refund.refundId);
        response.setPaymentId(refund.payment != null ? refund.payment.id : null);
        response.setTransactionId(refund.payment != null ? refund.payment.transactionId : null);
        response.setAmount(refund.amount);
        response.setReason(refund.reason);
        response.setStatus(refund.status != null ? refund.status.name() : null);
        response.setGatewayRefundId(refund.gatewayRefundId);
        response.setGatewayProvider(refund.gatewayProvider);
        response.setRefundDate(refund.refundDate);
        response.setErrorMessage(refund.errorMessage);
        response.setNotes(refund.notes);
        return response;
    }
}
