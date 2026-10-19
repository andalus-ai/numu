package tech.kayys.payment.processor.qris;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayRequest.CustomerInfo;
import tech.kayys.payment.gateway.PaymentGatewayProvider;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.method.processor.AbstractPaymentMethodProcessor;
import tech.kayys.payment.processor.qris.config.QRISConfig;
import tech.kayys.payment.processor.qris.domain.QRISPayment;
import tech.kayys.payment.processor.qris.repository.QRISPaymentRepository;

/**
 * Processor for QRIS (Quick Response Code Indonesian Standard) payments.
 * 
 * QRIS is a standardized QR code payment system in Indonesia that supports
 * multiple e-wallets and banking apps including GoPay, OVO, DANA, LinkAja,
 * and various mobile banking applications.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class QRISProcessor extends AbstractPaymentMethodProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(QRISProcessor.class);

    @Inject
    QRISConfig qrisConfig;

    @Inject
    QRISPaymentRepository repository;

    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.QRIS;
    }

    @Override
    public boolean supports(PaymentMethodType methodType) {
        if (methodType == null) return false;
        return methodType == PaymentMethodType.QRIS ||
               methodType == PaymentMethodType.QRIS_DANA ||
               methodType == PaymentMethodType.QRIS_OVO ||
               methodType == PaymentMethodType.QRIS_GOPAY;
    }

    @Override
    protected GatewayRequest buildGatewayRequest(PaymentMethodContext context) {
        GatewayRequest request = new GatewayRequest();
        request.setTransactionId(context.getTransactionId());
        request.setExternalOrderId(context.getExternalOrderId());
        request.setAmount(context.getAmount());
        request.setCurrency(context.getCurrency());
        request.setPaymentMethod(context.getPaymentMethod().getCode());
        request.setCallbackUrl(context.getCallbackUrl());
        request.setReturnUrl(context.getReturnUrl());
        request.setDescription("QRIS Payment - " + context.getExternalOrderId());

        // Build customer info
        CustomerInfo customer = new CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);

        // Add QRIS-specific metadata
        request.addMetadata("qris_channel", context.getPaymentMethod().name());
        request.addMetadata("qris_acquirer", qrisConfig.getDefaultAcquirer());
        
        if (context.getPaymentMethod().isQRIS()) {
            request.addMetadata("channel", context.getPaymentMethod().name());
        }

        // Copy additional metadata from context
        if (context.getMetadata() != null) {
            context.getMetadata().forEach(request::addMetadata);
        }

        LOG.info("Built QRIS gateway request for transaction: {}", request.getTransactionId());
        return request;
    }

    @Override
    @Transactional
    public PaymentMethodResult initiate(PaymentMethodContext context) {
        LOG.info("Initiating QRIS payment for transaction: {}, method: {}", 
                context.getTransactionId(), context.getPaymentMethod());

        // Validate amount
        if (context.getAmount() == null || context.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Invalid amount for QRIS payment")
                .errorCode("INVALID_AMOUNT")
                .build();
        }

        // Validate QRIS-specific constraints
        PaymentMethodResult validation = validateQRISConstraints(context);
        if (!validation.isSuccess()) {
            return validation;
        }

        // Create QRIS payment record
        QRISPayment qrisPayment = createQRISPaymentRecord(context);
        repository.persist(qrisPayment);

        // Process through gateway
        PaymentMethodResult result = super.initiate(context);

        if (result.isSuccess()) {
            qrisPayment.setTransactionId(result.getTransactionId());
            qrisPayment.setExternalTransactionId(result.getExternalTransactionId());
            qrisPayment.setQrCodeString(result.getQrCodeString());
            qrisPayment.setQrCodeUrl(result.getQrCodeUrl());
            qrisPayment.setStatus(QRISPayment.Status.PENDING);
            qrisPayment.setExpiryTime(result.getExpiryTime());
            repository.persist(qrisPayment);

            LOG.info("QRIS payment initiated successfully: {}, QR Code generated", 
                    result.getTransactionId());
        } else {
            qrisPayment.setStatus(QRISPayment.Status.FAILED);
            qrisPayment.setErrorMessage(result.getErrorMessage());
            repository.persist(qrisPayment);

            LOG.warn("QRIS payment initiation failed: {}", result.getErrorMessage());
        }

        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult verify(String transactionId) {
        LOG.info("Verifying QRIS payment: {}", transactionId);

        // First check local repository
        Optional<QRISPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            QRISPayment qrisPayment = localPayment.get();
            
            // If already success, return cached result
            if (qrisPayment.getStatus() == QRISPayment.Status.SUCCESS) {
                return buildVerifyResult(qrisPayment, true);
            }
        }

        // Verify through gateway
        PaymentMethodResult result = super.verify(transactionId);

        // Update local record
        if (localPayment.isPresent()) {
            QRISPayment qrisPayment = localPayment.get();
            updateQRISPaymentStatus(qrisPayment, result);
            repository.persist(qrisPayment);
        }

        LOG.info("QRIS payment verification completed: {}, status: {}", 
                transactionId, result.isSuccess() ? "SUCCESS" : "FAILED");

        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult cancel(String transactionId, String reason) {
        LOG.info("Cancelling QRIS payment: {}, reason: {}", transactionId, reason);

        // Update local record first
        Optional<QRISPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            QRISPayment qrisPayment = localPayment.get();
            if (qrisPayment.getStatus() == QRISPayment.Status.SUCCESS) {
                return PaymentMethodResult.builder()
                    .success(false)
                    .errorMessage("Cannot cancel completed QRIS payment")
                    .errorCode("CANNOT_CANCEL_COMPLETED")
                    .build();
            }
            qrisPayment.setStatus(QRISPayment.Status.CANCELLED);
            qrisPayment.setCancelReason(reason);
            qrisPayment.setCancelledAt(LocalDateTime.now());
            repository.persist(qrisPayment);
        }

        PaymentMethodResult result = super.cancel(transactionId, reason);

        LOG.info("QRIS payment cancellation completed: {}", transactionId);
        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult refund(String transactionId, BigDecimal amount, String reason) {
        LOG.info("Refunding QRIS payment: {}, amount: {}, reason: {}", 
                transactionId, amount, reason);

        // Validate refund amount
        Optional<QRISPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            QRISPayment qrisPayment = localPayment.get();
            
            if (qrisPayment.getStatus() != QRISPayment.Status.SUCCESS) {
                return PaymentMethodResult.builder()
                    .success(false)
                    .errorMessage("Cannot refund non-completed payment")
                    .errorCode("CANNOT_REFUND_INCOMPLETE")
                    .build();
            }

            if (amount != null && amount.compareTo(qrisPayment.getAmount()) > 0) {
                return PaymentMethodResult.builder()
                    .success(false)
                    .errorMessage("Refund amount exceeds original payment")
                    .errorCode("REFUND_AMOUNT_EXCEEDS")
                    .build();
            }
        }

        PaymentMethodResult result = super.refund(transactionId, amount, reason);

        // Update local record
        if (localPayment.isPresent() && result.isSuccess()) {
            QRISPayment qrisPayment = localPayment.get();
            if (amount == null || amount.equals(qrisPayment.getAmount())) {
                qrisPayment.setStatus(QRISPayment.Status.REFUNDED);
            } else {
                qrisPayment.setStatus(QRISPayment.Status.PARTIALLY_REFUNDED);
            }
            qrisPayment.setRefundReason(reason);
            qrisPayment.setRefundedAt(LocalDateTime.now());
            repository.persist(qrisPayment);
        }

        LOG.info("QRIS payment refund completed: {}", transactionId);
        return result;
    }

    /**
     * Get QRIS payment details by transaction ID
     */
    public Optional<QRISPayment> getQRISPayment(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    /**
     * Get QRIS payment details by external order ID
     */
    public Optional<QRISPayment> getQRISPaymentByOrderId(String externalOrderId) {
        return repository.findByExternalOrderId(externalOrderId);
    }

    /**
     * Generate QR code image from QR code string
     */
    public byte[] generateQRCodeImage(String qrCodeString, int width, int height) {
        return QRISCodeGenerator.generateQRCode(qrCodeString, width, height);
    }

    private PaymentMethodResult validateQRISConstraints(PaymentMethodContext context) {
        // Check minimum amount
        BigDecimal minAmount = qrisConfig.getMinAmount();
        if (context.getAmount().compareTo(minAmount) < 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Amount below minimum QRIS limit: " + minAmount)
                .errorCode("AMOUNT_BELOW_MINIMUM")
                .build();
        }

        // Check maximum amount
        BigDecimal maxAmount = qrisConfig.getMaxAmount();
        if (context.getAmount().compareTo(maxAmount) > 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Amount exceeds maximum QRIS limit: " + maxAmount)
                .errorCode("AMOUNT_EXCEEDS_MAXIMUM")
                .build();
        }

        // Check if payment method is QRIS variant
        if (!context.getPaymentMethod().isQRIS()) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Invalid payment method for QRIS processor: " + context.getPaymentMethod())
                .errorCode("INVALID_PAYMENT_METHOD")
                .build();
        }

        return PaymentMethodResult.builder().success(true).build();
    }

    private QRISPayment createQRISPaymentRecord(PaymentMethodContext context) {
        QRISPayment payment = new QRISPayment();
        payment.setTransactionId(context.getTransactionId());
        payment.setExternalOrderId(context.getExternalOrderId());
        payment.setAmount(context.getAmount());
        payment.setCurrency(context.getCurrency());
        payment.setCustomerName(context.getCustomerName());
        payment.setCustomerEmail(context.getCustomerEmail());
        payment.setCustomerPhone(context.getCustomerPhone());
        payment.setPaymentMethod(context.getPaymentMethod());
        payment.setGatewayProvider(context.getGatewayProvider());
        payment.setStatus(QRISPayment.Status.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }

    private void updateQRISPaymentStatus(QRISPayment payment, PaymentMethodResult result) {
        if (result.isSuccess()) {
            switch (result.getStatus()) {
                case SUCCESS:
                    payment.setStatus(QRISPayment.Status.SUCCESS);
                    payment.setPaidAt(LocalDateTime.now());
                    break;
                case PENDING:
                    payment.setStatus(QRISPayment.Status.PENDING);
                    break;
                case FAILED:
                    payment.setStatus(QRISPayment.Status.FAILED);
                    break;
                case CANCELLED:
                    payment.setStatus(QRISPayment.Status.CANCELLED);
                    break;
                case EXPIRED:
                    payment.setStatus(QRISPayment.Status.EXPIRED);
                    break;
                case REFUNDED:
                    payment.setStatus(QRISPayment.Status.REFUNDED);
                    break;
                case PARTIALLY_REFUNDED:
                    payment.setStatus(QRISPayment.Status.PARTIALLY_REFUNDED);
                    break;
            }
        }
        payment.setLastUpdatedAt(LocalDateTime.now());
    }

    private PaymentMethodResult buildVerifyResult(QRISPayment payment, boolean fromCache) {
        PaymentMethodResult.Builder builder = PaymentMethodResult.builder()
            .success(payment.getStatus() == QRISPayment.Status.SUCCESS)
            .transactionId(payment.getTransactionId())
            .externalTransactionId(payment.getExternalTransactionId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .qrCodeString(payment.getQrCodeString())
            .qrCodeUrl(payment.getQrCodeUrl())
            .expiryTime(payment.getExpiryTime());

        switch (payment.getStatus()) {
            case SUCCESS:
                builder.status(PaymentMethodResult.PaymentStatus.SUCCESS);
                break;
            case PENDING:
                builder.status(PaymentMethodResult.PaymentStatus.PENDING);
                break;
            case FAILED:
                builder.status(PaymentMethodResult.PaymentStatus.FAILED);
                builder.errorMessage(payment.getErrorMessage());
                break;
            case CANCELLED:
                builder.status(PaymentMethodResult.PaymentStatus.CANCELLED);
                break;
            case EXPIRED:
                builder.status(PaymentMethodResult.PaymentStatus.EXPIRED);
                break;
            case REFUNDED:
                builder.status(PaymentMethodResult.PaymentStatus.REFUNDED);
                break;
            case PARTIALLY_REFUNDED:
                builder.status(PaymentMethodResult.PaymentStatus.PARTIALLY_REFUNDED);
                break;
        }

        if (fromCache) {
            builder.addMetadata("cached", true);
        }

        return builder.build();
    }
}
