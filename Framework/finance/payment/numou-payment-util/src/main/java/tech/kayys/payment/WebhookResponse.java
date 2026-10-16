package tech.kayys.payment.dto;

import java.time.LocalDateTime;

import tech.kayys.payment.model.PaymentStatus;

public class WebhookResponse {
    public String transactionId;
    public PaymentStatus status;
    public PaymentStatus previousStatus;
    public String merchantId;
    public LocalDateTime timestamp;
    public String signature;
    public String eventType;
}