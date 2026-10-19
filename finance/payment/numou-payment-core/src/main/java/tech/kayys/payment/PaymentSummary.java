package tech.kayys.payment;

import java.math.BigDecimal;
import java.util.Map;
import tech.kayys.payment.Payment.PaymentStatus;
import tech.kayys.payment.method.PaymentMethodType;

public record PaymentSummary(
        BigDecimal totalRevenue,
        Map<PaymentMethodType, BigDecimal> revenueByMethod,
        Map<PaymentStatus, Long> paymentCountByStatus,
        BigDecimal averageTransactionValue
) {}