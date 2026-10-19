package tech.kayys.payment.service;

import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import tech.kayys.payment.domain.Payment;
import tech.kayys.payment.domain.Refund;
import tech.kayys.payment.dto.AnalyticsResponse;
import tech.kayys.payment.dto.PaymentRequest;
import tech.kayys.payment.dto.PaymentResponse;
import tech.kayys.payment.dto.RefundRequest;
import tech.kayys.client.NotificationClient;
import tech.kayys.gateway.PaymentGatewayFactory;
import tech.kayys.payment.model.PaymentEvent;
import tech.kayys.payment.model.PaymentEventType;
import tech.kayys.payment.model.PaymentGateway;
import tech.kayys.payment.model.PaymentMethod;
import tech.kayys.payment.model.PaymentStatus;
import tech.kayys.payment.model.PaymentSummary;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.emitter.Emitter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class PaymentUtilService {

    @Inject
    PaymentGatewayFactory gatewayFactory;

    @Inject
    NotificationClient notificationService;

    @Inject
    FraudDetectionService fraudDetectionService;

    @Inject
    FeeCalculationService feeCalculationService;

    @Inject
    PaymentLogService paymentLogService;

    @ConfigProperty(name = "payment.expiry.minutes", defaultValue = "1440")
    int paymentExpiryMinutes;

    @ConfigProperty(name = "payment.retry.attempts", defaultValue = "3")
    int retryAttempts;

    @Transactional
    @Counted(name = "payment_created_total", description = "Total number of payments created")
    @Timed(name = "payment_creation_time", description = "Time taken to create payment")
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 5000)
    @Retry(maxRetries = 3, delay = 1000)
    @Timeout(value = 30, unit = java.time.temporal.ChronoUnit.SECONDS)
    public PaymentResponse createPayment(PaymentRequest request) {
        // Validate request
        validatePaymentRequest(request);

        // Fraud detection
        int riskScore = fraudDetectionService.calculateRiskScore(request);
        if (riskScore > 80) {
            throw new RuntimeException("Transaction blocked due to high risk score");
        }

        // Calculate fee
        BigDecimal fee = feeCalculationService.calculateFee(request.amount, request.paymentMethod);

        // Create payment entity
        Payment payment = new Payment();
        payment.transactionId = generateTransactionId();
        payment.merchantId = request.merchantId;
        payment.customerId = request.customerId;
        payment.amount = request.amount;
        payment.fee = fee;
        payment.currency = request.currency;
        payment.paymentMethod = request.paymentMethod;
        payment.description = request.description;
        payment.callbackUrl = request.callbackUrl;
        payment.redirectUrl = request.redirectUrl;
        payment.referenceId = request.referenceId;
        payment.installmentTerm = request.installmentTerm;
        payment.expiredAt = LocalDateTime.now().plusMinutes(paymentExpiryMinutes);
        payment.riskScore = riskScore;
        payment.customerInfo = request.customerInfo != null ? convertToJson(request.customerInfo) : null;
        payment.metadata = request.metadata != null ? convertToJson(request.metadata) : null;

        // Get appropriate payment gateway
        PaymentGateway gateway = gatewayFactory.getGateway(request.paymentMethod);

        // Process payment with gateway
        PaymentResponse response = gateway.processPayment(payment, request);

        // Update payment with gateway response
        payment.paymentGatewayResponse = response.toString();
        payment.virtualAccountNumber = response.virtualAccountNumber;
        payment.paymentCode = response.paymentCode;
        payment.qrCode = response.qrCode;
        payment.deepLink = response.deepLink;

        // Persist payment
        payment.persist();

        // Log payment creation
        paymentLogService.logPaymentEvent(payment.transactionId, "PAYMENT_CREATED",
                null, payment.status.name(), "Payment created successfully");

        return response;
    }

    @Transactional
    @CacheResult(cacheName = "payment-status")
    public PaymentResponse getPaymentStatus(String transactionId) {
        Payment payment = Payment.find("transactionId", transactionId).firstResult();
        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        return mapToResponse(payment);
    }

    @Transactional
    @CacheInvalidate(cacheName = "payment-status")
    public void handleCallback(String transactionId, PaymentStatus status, String gatewayResponse) {
        Payment payment = Payment.find("transactionId", transactionId).firstResult();
        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        PaymentStatus oldStatus = payment.status;
        payment.status = status;
        payment.paymentGatewayResponse = gatewayResponse;

        if (status == PaymentStatus.SUCCESS) {
            payment.paidAt = LocalDateTime.now();
        }

        payment.persist();

        // Log status change
        paymentLogService.logPaymentEvent(transactionId, "STATUS_CHANGE",
                oldStatus.name(), status.name(), "Payment status updated via callback");

        // Send notifications
        if (oldStatus != status) {
            notificationService.sendPaymentStatusNotification(payment);
        }
    }

    @Transactional
    public PaymentResponse refundPayment(RefundRequest request) {
        Payment payment = Payment.find("transactionId", request.transactionId).firstResult();
        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        if (payment.status != PaymentStatus.SUCCESS) {
            throw new RuntimeException("Only successful payments can be refunded");
        }

        // Create refund entity
        Refund refund = new Refund();
        refund.refundId = generateRefundId();
        refund.transactionId = request.transactionId;
        refund.merchantId = payment.merchantId;
        refund.amount = request.amount;
        refund.reason = request.reason;

        // Process refund with gateway
        PaymentGateway gateway = gatewayFactory.getGateway(payment.paymentMethod);
        // Implementation would call gateway.processRefund(refund)

        refund.persist();

        // Update payment status if full refund
        if (request.amount.compareTo(payment.amount) == 0) {
            payment.status = PaymentStatus.REFUNDED;
        } else {
            payment.status = PaymentStatus.PARTIAL_REFUNDED;
        }
        payment.persist();

        // Log refund
        paymentLogService.logPaymentEvent(request.transactionId, "REFUND_PROCESSED",
                null, refund.status.name(), "Refund processed: " + request.amount);

        return mapToResponse(payment);
    }

    @Transactional
    public void cancelPayment(String transactionId) {
        Payment payment = Payment.find("transactionId", transactionId).firstResult();
        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        if (payment.status == PaymentStatus.PENDING || payment.status == PaymentStatus.PROCESSING) {
            PaymentStatus oldStatus = payment.status;
            payment.status = PaymentStatus.CANCELLED;
            payment.persist();

            paymentLogService.logPaymentEvent(transactionId, "PAYMENT_CANCELLED",
                    oldStatus.name(), PaymentStatus.CANCELLED.name(), "Payment cancelled by user");

            notificationService.sendPaymentStatusNotification(payment);
        }
    }

    @Scheduled(every = "10m")
    @Transactional
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> expiredPayments = Payment.find("expiredAt < ?1 AND status IN (?2, ?3)",
                now, PaymentStatus.PENDING, PaymentStatus.PROCESSING).list();

        for (Payment payment : expiredPayments) {
            PaymentStatus oldStatus = payment.status;
            payment.status = PaymentStatus.EXPIRED;
            payment.persist();

            paymentLogService.logPaymentEvent(payment.transactionId, "PAYMENT_EXPIRED",
                    oldStatus.name(), PaymentStatus.EXPIRED.name(), "Payment expired automatically");
        }
    }

    @Scheduled(every = "1h")
    @Transactional
    public void processSettlements() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        List<Payment> settleablePayments = Payment.find(
                "status = ?1 AND paidAt < ?2 AND settlementAt IS NULL",
                PaymentStatus.SUCCESS, cutoffTime).list();

        for (Payment payment : settleablePayments) {
            payment.status = PaymentStatus.SETTLEMENT;
            payment.settlementAt = LocalDateTime.now();
            payment.persist();

            paymentLogService.logPaymentEvent(payment.transactionId, "SETTLEMENT_PROCESSED",
                    PaymentStatus.SUCCESS.name(), PaymentStatus.SETTLEMENT.name(),
                    "Payment settled to merchant");
        }
    }

    public AnalyticsResponse getPaymentAnalytics(String merchantId, LocalDateTime startDate,
            LocalDateTime endDate) {
        // Implementation for analytics calculation
        List<Payment> payments = Payment.find(
                "merchantId = ?1 AND createdAt BETWEEN ?2 AND ?3",
                merchantId, startDate, endDate).list();

        AnalyticsResponse analytics = new AnalyticsResponse();
        analytics.totalAmount = payments.stream()
                .filter(p -> p.status == PaymentStatus.SUCCESS)
                .map(p -> p.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        analytics.totalTransactions = (long) payments.size();

        long successCount = payments.stream()
                .mapToLong(p -> p.status == PaymentStatus.SUCCESS ? 1 : 0)
                .sum();

        analytics.successRate = analytics.totalTransactions > 0
                ? BigDecimal.valueOf(successCount * 100.0 / analytics.totalTransactions)
                : BigDecimal.ZERO;

        return analytics;
    }

    private String generateRefundId() {
        return "REF-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String convertToJson(Object object) {
        // Implementation for JSON serialization
        return object.toString();
    }

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    OrderService orderService;

    @Inject
    @Channel("payments")
    Emitter<PaymentEvent> paymentEventEmitter;

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    public Uni<Payment> processPayment(PaymentRequest paymentRequest) {
        return validatePaymentRequest(paymentRequest)
                .chain(() -> orderService.getOrderById(paymentRequest.orderId()))
                .chain(order -> validateOrderForPayment(order))
                .chain(order -> createPaymentEntity(paymentRequest, order))
                .chain(payment -> sessionFactory.withTransaction((session, tx) -> session.persist(payment)))
                .chain(payment -> processPaymentWithGateway(payment))
                .chain(payment -> updateOrderPaymentStatus(payment))
                .invoke(payment -> {
                    paymentEventEmitter.send(new PaymentEvent(
                            payment.id,
                            PaymentEventType.PROCESSED,
                            payment));
                    LOG.info("Payment processed: " + payment.transactionId);
                })
                .onFailure()
                .transform(throwable -> new BusinessException("Failed to process payment: " + throwable.getMessage()));
    }

    public Uni<Payment> refundPayment(Integer paymentId, RefundRequest refundRequest) {
        return getPaymentById(paymentId)
                .chain(payment -> validatePaymentForRefund(payment))
                .chain(payment -> processRefundWithGateway(payment, refundRequest))
                .chain(payment -> {
                    payment.status = PaymentStatus.REFUNDED;
                    payment.notes = (payment.notes != null ? payment.notes + "\n" : "") +
                            "Refund: " + refundRequest.reason();
                    return payment.persist();
                })
                .invoke(payment -> {
                    paymentEventEmitter.send(new PaymentEvent(
                            payment.id,
                            PaymentEventType.REFUNDED,
                            Map.of("refundAmount", payment.amount, "reason", refundRequest.reason())));
                })
                .onFailure()
                .transform(throwable -> new BusinessException("Failed to process refund: " + throwable.getMessage()));
    }

    public Uni<Payment> capturePayment(Integer paymentId) {
        return getPaymentById(paymentId)
                .chain(payment -> {
                    if (payment.status != PaymentStatus.PENDING) {
                        return Uni.createFrom().failure(
                                new BusinessException("Payment is not in pending status"));
                    }

                    // Simulate payment capture
                    payment.status = PaymentStatus.COMPLETED;
                    payment.paymentDate = LocalDateTime.now();
                    payment.transactionId = generateTransactionId();

                    return payment.persist()
                            .chain(updatedPayment -> updateOrderPaymentStatus(updatedPayment))
                            .invoke(updatedPayment -> {
                                paymentEventEmitter.send(new PaymentEvent(
                                        updatedPayment.id,
                                        PaymentEventType.COMPLETED,
                                        updatedPayment));
                            });
                });
    }

    public Uni<List<Payment>> getPaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "paymentDate between ?1 and ?2 order by paymentDate desc",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)).list();
    }

    public Uni<PaymentSummary> getPaymentSummary(LocalDate startDate, LocalDate endDate) {
        return Uni.combine().all().unis(
                getTotalRevenueByDateRange(startDate, endDate),
                getRevenueByPaymentMethod(startDate, endDate),
                getPaymentCountByStatus(startDate, endDate),
                getAverageTransactionValue(startDate, endDate)).asTuple().onItem()
                .transform(tuple -> new PaymentSummary(
                        tuple.getItem1(),
                        tuple.getItem2(),
                        tuple.getItem3(),
                        tuple.getItem4()));
    }

    public Uni<Payment> voidPayment(Integer paymentId, String reason) {
        return getPaymentById(paymentId)
                .chain(payment -> {
                    if (payment.status != PaymentStatus.PENDING) {
                        return Uni.createFrom().failure(
                                new BusinessException("Only pending payments can be voided"));
                    }

                    payment.status = PaymentStatus.FAILED;
                    payment.notes = (payment.notes != null ? payment.notes + "\n" : "") +
                            "Voided: " + reason;

                    return payment.persist()
                            .invoke(updatedPayment -> {
                                paymentEventEmitter.send(new PaymentEvent(
                                        updatedPayment.id,
                                        PaymentEventType.VOIDED,
                                        Map.of("reason", reason)));
                            });
                });
    }

    public Uni<Map<LocalDate, BigDecimal>> getDailyRevenueTrend(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "select date(p.paymentDate), sum(p.amount) from Payment p " +
                        "where p.paymentDate between ?1 and ?2 and p.status = ?3 " +
                        "group by date(p.paymentDate) order by date(p.paymentDate)",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX), PaymentStatus.COMPLETED).list()
                .map(results -> {
                    Map<LocalDate, BigDecimal> dailyRevenue = new TreeMap<>();
                    for (Object[] result : results) {
                        dailyRevenue.put(((java.sql.Date) result[0]).toLocalDate(), (BigDecimal) result[1]);
                    }
                    return dailyRevenue;
                });
    }

    private Uni<Void> validatePaymentRequest(PaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return Uni.createFrom().failure(new ValidationException("Payment amount must be positive"));
        }

        if (request.method() == null) {
            return Uni.createFrom().failure(new ValidationException("Payment method is required"));
        }

        if (request.amount.compareTo(new BigDecimal("1000")) < 0) {
            throw new IllegalArgumentException("Minimum amount is IDR 1,000");
        }

        if (request.amount.compareTo(new BigDecimal("50000000")) > 0) {
            throw new IllegalArgumentException("Maximum amount is IDR 50,000,000");
        }

        if (request.installmentTerm != null && request.installmentTerm > 36) {
            throw new IllegalArgumentException("Maximum installment term is 36 months");
        }

        return Uni.createFrom().voidItem();
    }

    private Uni<Order> validateOrderForPayment(Order order) {
        if (order.status == OrderStatus.CANCELLED) {
            return Uni.createFrom().failure(new BusinessException("Cannot process payment for cancelled order"));
        }

        if (order.payment != null && order.payment.status == PaymentStatus.COMPLETED) {
            return Uni.createFrom().failure(new BusinessException("Order already has a completed payment"));
        }

        return Uni.createFrom().item(order);
    }

    private Uni<Payment> createPaymentEntity(PaymentRequest request, Order order) {
        Payment payment = new Payment();
        payment.amount = request.amount();
        payment.method = request.method();
        payment.status = PaymentStatus.PENDING;
        payment.notes = request.notes();
        payment.order = order;

        return Uni.createFrom().item(payment);
    }

    private Uni<Payment> processPaymentWithGateway(Payment payment) {
        // Simulate payment gateway processing
        return Uni.createFrom().item(payment)
                .onItem().delayIt().by(Duration.ofSeconds(2)) // Simulate processing time
                .onItem().transform(processedPayment -> {
                    // Simulate successful payment 95% of the time
                    if (Math.random() > 0.05) {
                        processedPayment.status = PaymentStatus.COMPLETED;
                        processedPayment.paymentDate = LocalDateTime.now();
                        processedPayment.transactionId = generateTransactionId();
                    } else {
                        processedPayment.status = PaymentStatus.FAILED;
                        processedPayment.notes = (processedPayment.notes != null ? processedPayment.notes + "\n" : "") +
                                "Payment gateway declined";
                    }
                    return processedPayment;
                })
                .chain(processedPayment -> processedPayment.persist());
    }

    private Uni<Payment> processRefundWithGateway(Payment payment, RefundRequest refundRequest) {
        // Simulate refund processing
        return Uni.createFrom().item(payment)
                .onItem().delayIt().by(Duration.ofSeconds(1))
                .onItem().transform(refundedPayment -> {
                    refundedPayment.transactionId = refundedPayment.transactionId + "-REFUND";
                    return refundedPayment;
                });
    }

    private Uni<Payment> updateOrderPaymentStatus(Payment payment) {
        if (payment.status == PaymentStatus.COMPLETED && payment.order != null) {
            return orderService.updateOrderStatus(payment.order.id, OrderStatus.COMPLETED)
                    .replaceWith(payment);
        }
        return Uni.createFrom().item(payment);
    }

    private Uni<Void> validatePaymentForRefund(Payment payment) {
        if (payment.status != PaymentStatus.COMPLETED) {
            return Uni.createFrom().failure(new BusinessException("Only completed payments can be refunded"));
        }

        if (payment.paymentDate.isBefore(LocalDateTime.now().minusDays(30))) {
            return Uni.createFrom().failure(new BusinessException("Payments older than 30 days cannot be refunded"));
        }

        return Uni.createFrom().voidItem();
    }

    private Uni<BigDecimal> getTotalRevenueByDateRange(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "select sum(amount) from Payment where paymentDate between ?1 and ?2 and status = ?3",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX), PaymentStatus.COMPLETED).firstResult()
                .onItem().ifNotNull().transform(result -> (BigDecimal) result)
                .onItem().ifNull().continueWith(BigDecimal.ZERO);
    }

    private Uni<Map<PaymentMethod, BigDecimal>> getRevenueByPaymentMethod(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "select p.method, sum(p.amount) from Payment p " +
                        "where p.paymentDate between ?1 and ?2 and p.status = ?3 " +
                        "group by p.method",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX), PaymentStatus.COMPLETED).list()
                .map(results -> {
                    Map<PaymentMethod, BigDecimal> revenueByMethod = new HashMap<>();
                    for (Object[] result : results) {
                        revenueByMethod.put((PaymentMethod) result[0], (BigDecimal) result[1]);
                    }
                    return revenueByMethod;
                });
    }

    private Uni<Map<PaymentStatus, Long>> getPaymentCountByStatus(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "select p.status, count(p) from Payment p " +
                        "where p.paymentDate between ?1 and ?2 " +
                        "group by p.status",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)).list().map(results -> {
                    Map<PaymentStatus, Long> countByStatus = new HashMap<>();
                    for (Object[] result : results) {
                        countByStatus.put((PaymentStatus) result[0], (Long) result[1]);
                    }
                    return countByStatus;
                });
    }

    private Uni<BigDecimal> getAverageTransactionValue(LocalDate startDate, LocalDate endDate) {
        return Payment.find(
                "select avg(amount) from Payment where paymentDate between ?1 and ?2 and status = ?3",
                startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX), PaymentStatus.COMPLETED).firstResult()
                .onItem().ifNotNull().transform(result -> (BigDecimal) result)
                .onItem().ifNull().continueWith(BigDecimal.ZERO);
    }

    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

}