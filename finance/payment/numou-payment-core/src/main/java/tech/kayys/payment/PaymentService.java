package tech.kayys.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import tech.kayys.payment.dto.CreatePaymentRequest;
import tech.kayys.payment.dto.PaymentResponse;
import tech.kayys.payment.dto.RefundRequest;
import tech.kayys.payment.dto.RefundResponse;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.GatewayResponse;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodProcessor;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.service.PaymentGatewayRegistry;
import tech.kayys.payment.service.PaymentMethodRegistry;
import tech.kayys.payment.service.PaymentProcessorFactory;

/**
 * Payment Service - Orchestrates payment processing using strategy pattern
 */
@ApplicationScoped
public class PaymentService {
    
    @Inject
    PaymentRepository paymentRepository;
    
    @Inject
    PaymentMethodRegistry methodRegistry;
    
    @Inject
    PaymentGatewayRegistry gatewayRegistry;
    
    @Inject
    PaymentProcessorFactory processorFactory;
    
    /**
     * Create a new payment
     */
    @Transactional
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.externalOrderId = request.getExternalOrderId();
        payment.paymentMethod = request.getPaymentMethodType();
        payment.gatewayProvider = request.getGatewayProvider();
        payment.amount = request.getAmount();
        payment.customerName = request.getCustomerName();
        payment.customerEmail = request.getCustomerEmail();
        payment.customerPhone = request.getCustomerPhone();
        payment.notes = request.getDescription();
        
        // Store metadata
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> 
                payment.addMetadata(key, value.toString()));
        }
        
        paymentRepository.persist(payment);
        return payment;
    }
    
    /**
     * Process payment using the appropriate payment method processor
     */
    @Transactional
    public Payment processPayment(Payment payment) {
        // Get payment method processor
        Optional<PaymentMethodProcessor> processorOpt = 
            processorFactory.getMethodProcessor(payment.paymentMethod);
        
        if (processorOpt.isEmpty()) {
            payment.status = Payment.PaymentStatus.FAILED;
            payment.notes = "Payment method not supported: " + payment.paymentMethod;
            paymentRepository.persist(payment);
            return payment;
        }
        
        PaymentMethodProcessor processor = processorOpt.get();
        
        // Determine gateway provider
        String gatewayCode = payment.gatewayProvider;
        if (gatewayCode == null) {
            // Auto-select best gateway for the payment method
            Optional<tech.kayys.payment.gateway.PaymentGatewayProvider> bestGateway = 
                processorFactory.getBestGatewayForMethod(payment.paymentMethod);
            if (bestGateway.isPresent()) {
                gatewayCode = bestGateway.get().getProvider().name();
                payment.gatewayProvider = gatewayCode;
            }
        }
        
        // Build payment context
        PaymentMethodContext context = buildPaymentContext(payment, gatewayCode);
        
        // Initiate payment
        PaymentMethodResult result = processor.initiate(context);
        
        // Update payment with result
        updatePaymentFromResult(payment, result);
        
        paymentRepository.persist(payment);
        
        return payment;
    }
    
    /**
     * Process payment from request (create and process in one step)
     */
    @Transactional
    public PaymentResponse createAndProcessPayment(CreatePaymentRequest request) {
        Payment payment = createPayment(request);
        Payment processedPayment = processPayment(payment);
        return PaymentResponse.fromEntity(processedPayment);
    }
    
    /**
     * Verify payment status
     */
    @Transactional
    public Payment verifyPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw new NotFoundException("Payment not found");
        }
        
        if (payment.status.isFinal()) {
            return payment;
        }
        
        // Verify with gateway
        Optional<PaymentMethodProcessor> processorOpt = 
            processorFactory.getMethodProcessor(payment.paymentMethod);
        
        if (processorOpt.isPresent()) {
            PaymentMethodResult result = processorOpt.get().verify(payment.gatewayTransactionId);
            updatePaymentFromResult(payment, result);
            paymentRepository.persist(payment);
        }
        
        return payment;
    }
    
    /**
     * Cancel payment
     */
    @Transactional
    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw new NotFoundException("Payment not found");
        }
        
        if (payment.status.isFinal()) {
            throw new IllegalStateException("Cannot cancel payment in final state: " + payment.status);
        }
        
        Optional<PaymentMethodProcessor> processorOpt = 
            processorFactory.getMethodProcessor(payment.paymentMethod);
        
        if (processorOpt.isPresent()) {
            PaymentMethodResult result = processorOpt.get().cancel(
                payment.gatewayTransactionId, reason);
            
            if (result.isSuccess()) {
                payment.status = Payment.PaymentStatus.CANCELLED;
                payment.notes = "Cancelled: " + reason;
            } else {
                payment.notes = "Cancel failed: " + result.getErrorMessage();
            }
            
            paymentRepository.persist(payment);
        }
        
        return payment;
    }
    
    /**
     * Refund payment
     */
    @Transactional
    public RefundResponse refundPayment(Long paymentId, RefundRequest refundRequest) {
        Payment payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw new NotFoundException("Payment not found");
        }
        
        if (!payment.canBeRefunded()) {
            throw new IllegalStateException("Payment cannot be refunded. Status: " + payment.status);
        }
        
        // Create refund record
        PaymentRefund refund = new PaymentRefund();
        refund.payment = payment;
        refund.amount = refundRequest.getAmount() != null ? refundRequest.getAmount() : payment.amount;
        refund.reason = refundRequest.getReason();
        refund.gatewayProvider = payment.gatewayProvider;
        refund.notes = refundRequest.getNotes();
        
        // Process refund through gateway
        Optional<PaymentMethodProcessor> processorOpt = 
            processorFactory.getMethodProcessor(payment.paymentMethod);
        
        if (processorOpt.isPresent()) {
            PaymentMethodResult result = processorOpt.get().refund(
                payment.gatewayTransactionId, 
                refund.amount, 
                refundRequest.getReason());
            
            if (result.isSuccess()) {
                refund.status = PaymentRefund.RefundStatus.COMPLETED;
                refund.gatewayRefundId = result.getExternalTransactionId();
                
                // Update payment status
                if (refundRequest.isFullRefund()) {
                    payment.status = Payment.PaymentStatus.REFUNDED;
                } else {
                    payment.status = Payment.PaymentStatus.PARTIALLY_REFUNDED;
                }
            } else {
                refund.status = PaymentRefund.RefundStatus.FAILED;
                refund.errorMessage = result.getErrorMessage();
            }
        } else {
            refund.status = PaymentRefund.RefundStatus.FAILED;
            refund.errorMessage = "Payment method processor not found";
        }
        
        // Persist refund
        if (refund.id == null) {
            paymentRepository.getEntityManager().persist(refund);
        }
        paymentRepository.persist(payment);
        
        return RefundResponse.fromEntity(refund);
    }
    
    /**
     * Get payment by ID
     */
    public Payment getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id);
        if (payment == null) {
            throw new NotFoundException("Payment not found");
        }
        return payment;
    }
    
    /**
     * Get payment by transaction ID
     */
    public Payment getPaymentByTransactionId(String transactionId) {
        Optional<Payment> payment = paymentRepository.findByTransactionId(transactionId);
        if (payment.isEmpty()) {
            throw new NotFoundException("Payment not found");
        }
        return payment.get();
    }
    
    /**
     * Get payments by invoice ID
     */
    public List<Payment> getPaymentsByInvoiceId(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }
    
    /**
     * Get payments by status
     */
    public List<Payment> getPaymentsByStatus(Payment.PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }
    
    /**
     * Get pending payments
     */
    public List<Payment> getPendingPayments() {
        return paymentRepository.findPending();
    }
    
    /**
     * Build payment context from payment entity
     */
    private PaymentMethodContext buildPaymentContext(Payment payment, String gatewayCode) {
        PaymentMethodContext context = new PaymentMethodContext();
        context.setTransactionId(payment.transactionId);
        context.setExternalOrderId(payment.externalOrderId != null ? payment.externalOrderId : "PAY-" + payment.id);
        context.setAmount(payment.amount);
        context.setCurrency("IDR");
        context.setPaymentMethod(payment.paymentMethod);
        context.setGatewayProvider(gatewayCode);
        context.setCustomerName(payment.customerName);
        context.setCustomerEmail(payment.customerEmail);
        context.setCustomerPhone(payment.customerPhone);
        
        // Convert metadata
        if (payment.metadata != null) {
            payment.metadata.forEach(context::addMetadata);
        }
        
        return context;
    }
    
    /**
     * Update payment entity from payment method result
     */
    private void updatePaymentFromResult(Payment payment, PaymentMethodResult result) {
        if (result.getTransactionId() != null) {
            payment.gatewayTransactionId = result.getTransactionId();
        }
        
        if (result.getExternalTransactionId() != null) {
            payment.externalReference = result.getExternalTransactionId();
        }
        
        // Update status
        payment.status = convertResultStatus(result.getStatus());
        
        // Update payment method specific fields
        if (result.getPaymentUrl() != null) {
            payment.paymentUrl = result.getPaymentUrl();
        }
        if (result.getQrCodeUrl() != null) {
            payment.qrCodeUrl = result.getQrCodeUrl();
        }
        if (result.getQrCodeString() != null) {
            payment.qrCodeString = result.getQrCodeString();
        }
        if (result.getVirtualAccountNumber() != null) {
            payment.virtualAccountNumber = result.getVirtualAccountNumber();
        }
        if (result.getBankCode() != null) {
            payment.bankCode = result.getBankCode();
        }
        if (result.getExpiryTime() != null) {
            payment.expiryTime = result.getExpiryTime();
        }
        
        // Update notes with error message if failed
        if (!result.isSuccess() && result.getErrorMessage() != null) {
            payment.notes = result.getErrorMessage();
        }
        
        // Store result metadata
        if (result.getMetadata() != null) {
            result.getMetadata().forEach((key, value) -> 
                payment.addMetadata("result_" + key, value.toString()));
        }
    }
    
    /**
     * Convert payment method result status to payment status
     */
    private Payment.PaymentStatus convertResultStatus(PaymentMethodResult.PaymentStatus resultStatus) {
        if (resultStatus == null) {
            return Payment.PaymentStatus.PENDING;
        }
        
        switch (resultStatus) {
            case SUCCESS:
                return Payment.PaymentStatus.SUCCESS;
            case PENDING:
                return Payment.PaymentStatus.PENDING;
            case FAILED:
                return Payment.PaymentStatus.FAILED;
            case CANCELLED:
                return Payment.PaymentStatus.CANCELLED;
            case EXPIRED:
                return Payment.PaymentStatus.EXPIRED;
            case REFUNDED:
                return Payment.PaymentStatus.REFUNDED;
            case PARTIALLY_REFUNDED:
                return Payment.PaymentStatus.PARTIALLY_REFUNDED;
            default:
                return Payment.PaymentStatus.PENDING;
        }
    }
}