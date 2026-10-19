package tech.kayys.payment.processor.bank;

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
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.method.processor.AbstractPaymentMethodProcessor;
import tech.kayys.payment.processor.bank.config.BankTransferConfig;
import tech.kayys.payment.processor.bank.domain.BankTransferPayment;
import tech.kayys.payment.processor.bank.repository.BankTransferPaymentRepository;

/**
 * Processor for Bank Transfer payments including Virtual Accounts.
 * 
 * Supports multiple Indonesian banks:
 * - BCA (Virtual Account)
 * - Mandiri (Virtual Account)
 * - BNI (Virtual Account)
 * - BRI (Virtual Account)
 * - Permata (Virtual Account)
 * - Standard Bank Transfer
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class BankTransferProcessor extends AbstractPaymentMethodProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(BankTransferProcessor.class);

    @Inject
    BankTransferConfig bankTransferConfig;

    @Inject
    BankTransferPaymentRepository repository;

    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.BANK_TRANSFER;
    }

    @Override
    public boolean supports(PaymentMethodType methodType) {
        if (methodType == null) return false;
        return methodType == PaymentMethodType.BANK_TRANSFER ||
               methodType == PaymentMethodType.VA_BCA ||
               methodType == PaymentMethodType.VA_MANDIRI ||
               methodType == PaymentMethodType.VA_BNI ||
               methodType == PaymentMethodType.VA_BRI ||
               methodType == PaymentMethodType.VA_PERMATA;
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
        request.setDescription("Bank Transfer Payment - " + context.getExternalOrderId());

        // Build customer info
        CustomerInfo customer = new CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);

        // Add bank-specific metadata
        request.addMetadata("bank_channel", context.getPaymentMethod().name());
        
        if (context.getPaymentMethod().isBankTransfer()) {
            request.addMetadata("channel", context.getPaymentMethod().name());
            
            // Add bank code for VA payments
            String bankCode = getBankCode(context.getPaymentMethod());
            if (bankCode != null) {
                request.addMetadata("bank_code", bankCode);
            }
        }

        // Copy additional metadata from context
        if (context.getMetadata() != null) {
            context.getMetadata().forEach(request::addMetadata);
        }

        LOG.info("Built Bank Transfer gateway request for transaction: {}", request.getTransactionId());
        return request;
    }

    @Override
    @Transactional
    public PaymentMethodResult initiate(PaymentMethodContext context) {
        LOG.info("Initiating Bank Transfer payment for transaction: {}, method: {}", 
                context.getTransactionId(), context.getPaymentMethod());

        // Validate amount
        if (context.getAmount() == null || context.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Invalid amount for Bank Transfer payment")
                .errorCode("INVALID_AMOUNT")
                .build();
        }

        // Validate bank transfer constraints
        PaymentMethodResult validation = validateBankTransferConstraints(context);
        if (!validation.isSuccess()) {
            return validation;
        }

        // Create bank transfer payment record
        BankTransferPayment bankPayment = createBankTransferPaymentRecord(context);
        repository.persist(bankPayment);

        // Process through gateway
        PaymentMethodResult result = super.initiate(context);

        if (result.isSuccess()) {
            bankPayment.setTransactionId(result.getTransactionId());
            bankPayment.setExternalTransactionId(result.getExternalTransactionId());
            bankPayment.setVirtualAccountNumber(result.getVirtualAccountNumber());
            bankPayment.setBankCode(result.getBankCode());
            bankPayment.setStatus(BankTransferPayment.Status.PENDING);
            bankPayment.setExpiryTime(result.getExpiryTime());
            repository.persist(bankPayment);

            LOG.info("Bank Transfer payment initiated successfully: {}, VA Number: {}", 
                    result.getTransactionId(), result.getVirtualAccountNumber());
        } else {
            bankPayment.setStatus(BankTransferPayment.Status.FAILED);
            bankPayment.setErrorMessage(result.getErrorMessage());
            repository.persist(bankPayment);

            LOG.warn("Bank Transfer payment initiation failed: {}", result.getErrorMessage());
        }

        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult verify(String transactionId) {
        LOG.info("Verifying Bank Transfer payment: {}", transactionId);

        // First check local repository
        Optional<BankTransferPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            BankTransferPayment bankPayment = localPayment.get();
            
            // If already success, return cached result
            if (bankPayment.getStatus() == BankTransferPayment.Status.SUCCESS) {
                return buildVerifyResult(bankPayment, true);
            }
        }

        // Verify through gateway
        PaymentMethodResult result = super.verify(transactionId);

        // Update local record
        if (localPayment.isPresent()) {
            BankTransferPayment bankPayment = localPayment.get();
            updateBankTransferPaymentStatus(bankPayment, result);
            repository.persist(bankPayment);
        }

        LOG.info("Bank Transfer payment verification completed: {}, status: {}", 
                transactionId, result.isSuccess() ? "SUCCESS" : "FAILED");

        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult cancel(String transactionId, String reason) {
        LOG.info("Cancelling Bank Transfer payment: {}, reason: {}", transactionId, reason);

        // Update local record first
        Optional<BankTransferPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            BankTransferPayment bankPayment = localPayment.get();
            if (bankPayment.getStatus() == BankTransferPayment.Status.SUCCESS) {
                return PaymentMethodResult.builder()
                    .success(false)
                    .errorMessage("Cannot cancel completed Bank Transfer payment")
                    .errorCode("CANNOT_CANCEL_COMPLETED")
                    .build();
            }
            bankPayment.setStatus(BankTransferPayment.Status.CANCELLED);
            bankPayment.setCancelReason(reason);
            bankPayment.setCancelledAt(LocalDateTime.now());
            repository.persist(bankPayment);
        }

        PaymentMethodResult result = super.cancel(transactionId, reason);

        LOG.info("Bank Transfer payment cancellation completed: {}", transactionId);
        return result;
    }

    @Override
    @Transactional
    public PaymentMethodResult refund(String transactionId, BigDecimal amount, String reason) {
        LOG.info("Refunding Bank Transfer payment: {}, amount: {}, reason: {}", 
                transactionId, amount, reason);

        // Validate refund amount
        Optional<BankTransferPayment> localPayment = repository.findByTransactionId(transactionId);
        if (localPayment.isPresent()) {
            BankTransferPayment bankPayment = localPayment.get();
            
            if (bankPayment.getStatus() != BankTransferPayment.Status.SUCCESS) {
                return PaymentMethodResult.builder()
                    .success(false)
                    .errorMessage("Cannot refund non-completed payment")
                    .errorCode("CANNOT_REFUND_INCOMPLETE")
                    .build();
            }

            if (amount != null && amount.compareTo(bankPayment.getAmount()) > 0) {
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
            BankTransferPayment bankPayment = localPayment.get();
            if (amount == null || amount.equals(bankPayment.getAmount())) {
                bankPayment.setStatus(BankTransferPayment.Status.REFUNDED);
            } else {
                bankPayment.setStatus(BankTransferPayment.Status.PARTIALLY_REFUNDED);
            }
            bankPayment.setRefundReason(reason);
            bankPayment.setRefundedAt(LocalDateTime.now());
            repository.persist(bankPayment);
        }

        LOG.info("Bank Transfer payment refund completed: {}", transactionId);
        return result;
    }

    /**
     * Get Bank Transfer payment details by transaction ID
     */
    public Optional<BankTransferPayment> getBankPayment(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    /**
     * Get Bank Transfer payment details by external order ID
     */
    public Optional<BankTransferPayment> getBankPaymentByOrderId(String externalOrderId) {
        return repository.findByExternalOrderId(externalOrderId);
    }

    /**
     * Get Bank Transfer payment by virtual account number
     */
    public Optional<BankTransferPayment> getBankPaymentByVANumber(String vaNumber) {
        return repository.findByVirtualAccountNumber(vaNumber);
    }

    /**
     * Get bank code from payment method
     */
    private String getBankCode(PaymentMethodType methodType) {
        switch (methodType) {
            case VA_BCA:
                return "BCA";
            case VA_MANDIRI:
                return "MANDIRI";
            case VA_BNI:
                return "BNI";
            case VA_BRI:
                return "BRI";
            case VA_PERMATA:
                return "PERMATA";
            default:
                return bankTransferConfig.getDefaultBank();
        }
    }

    private PaymentMethodResult validateBankTransferConstraints(PaymentMethodContext context) {
        // Check minimum amount
        BigDecimal minAmount = bankTransferConfig.getMinAmount();
        if (context.getAmount().compareTo(minAmount) < 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Amount below minimum Bank Transfer limit: " + minAmount)
                .errorCode("AMOUNT_BELOW_MINIMUM")
                .build();
        }

        // Check maximum amount
        BigDecimal maxAmount = bankTransferConfig.getMaxAmount();
        if (context.getAmount().compareTo(maxAmount) > 0) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Amount exceeds maximum Bank Transfer limit: " + maxAmount)
                .errorCode("AMOUNT_EXCEEDS_MAXIMUM")
                .build();
        }

        // Check if payment method is Bank Transfer variant
        if (!context.getPaymentMethod().isBankTransfer()) {
            return PaymentMethodResult.builder()
                .success(false)
                .errorMessage("Invalid payment method for Bank Transfer processor: " + context.getPaymentMethod())
                .errorCode("INVALID_PAYMENT_METHOD")
                .build();
        }

        return PaymentMethodResult.builder().success(true).build();
    }

    private BankTransferPayment createBankTransferPaymentRecord(PaymentMethodContext context) {
        BankTransferPayment payment = new BankTransferPayment();
        payment.setTransactionId(context.getTransactionId());
        payment.setExternalOrderId(context.getExternalOrderId());
        payment.setAmount(context.getAmount());
        payment.setCurrency(context.getCurrency());
        payment.setCustomerName(context.getCustomerName());
        payment.setCustomerEmail(context.getCustomerEmail());
        payment.setCustomerPhone(context.getCustomerPhone());
        payment.setPaymentMethod(context.getPaymentMethod());
        payment.setGatewayProvider(context.getGatewayProvider());
        payment.setStatus(BankTransferPayment.Status.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }

    private void updateBankTransferPaymentStatus(BankTransferPayment payment, PaymentMethodResult result) {
        if (result.isSuccess()) {
            switch (result.getStatus()) {
                case SUCCESS:
                    payment.setStatus(BankTransferPayment.Status.SUCCESS);
                    payment.setPaidAt(LocalDateTime.now());
                    break;
                case PENDING:
                    payment.setStatus(BankTransferPayment.Status.PENDING);
                    break;
                case FAILED:
                    payment.setStatus(BankTransferPayment.Status.FAILED);
                    break;
                case CANCELLED:
                    payment.setStatus(BankTransferPayment.Status.CANCELLED);
                    break;
                case EXPIRED:
                    payment.setStatus(BankTransferPayment.Status.EXPIRED);
                    break;
                case REFUNDED:
                    payment.setStatus(BankTransferPayment.Status.REFUNDED);
                    break;
                case PARTIALLY_REFUNDED:
                    payment.setStatus(BankTransferPayment.Status.PARTIALLY_REFUNDED);
                    break;
            }
        }
        payment.setLastUpdatedAt(LocalDateTime.now());
    }

    private PaymentMethodResult buildVerifyResult(BankTransferPayment payment, boolean fromCache) {
        PaymentMethodResult.Builder builder = PaymentMethodResult.builder()
            .success(payment.getStatus() == BankTransferPayment.Status.SUCCESS)
            .transactionId(payment.getTransactionId())
            .externalTransactionId(payment.getExternalTransactionId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .virtualAccountNumber(payment.getVirtualAccountNumber())
            .bankCode(payment.getBankCode())
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
