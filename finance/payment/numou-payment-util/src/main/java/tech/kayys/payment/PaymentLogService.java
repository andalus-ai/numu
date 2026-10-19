package tech.kayys.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.kayys.payment.domain.PaymentLog;

import java.time.LocalDateTime;

@ApplicationScoped
public class PaymentLogService {
    
    @Transactional
    public void logPaymentEvent(String transactionId, String eventType, 
                               String oldStatus, String newStatus, String description) {
        PaymentLog log = new PaymentLog();
        log.transactionId = transactionId;
        log.eventType = eventType;
        log.oldStatus = oldStatus;
        log.newStatus = newStatus;
        log.description = description;
        log.createdAt = LocalDateTime.now();
        
        log.persist();
    }
    
    @Transactional
    public void logPaymentEvent(String transactionId, String eventType, 
                               String description, String metadata) {
        PaymentLog log = new PaymentLog();
        log.transactionId = transactionId;
        log.eventType = eventType;
        log.description = description;
        log.metadata = metadata;
        log.createdAt = LocalDateTime.now();
        
        log.persist();
    }
}