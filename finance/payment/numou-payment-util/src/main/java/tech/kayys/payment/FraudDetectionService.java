package tech.kayys.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.payment.domain.Payment;
import tech.kayys.payment.dto.PaymentRequest;
import tech.kayys.payment.model.PaymentStatus;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@ApplicationScoped
public class FraudDetectionService {
    
    @Inject
    RedisDataSource redisDataSource;
    
    @ConfigProperty(name = "fraud.max.amount.per.day", defaultValue = "10000000")
    BigDecimal maxAmountPerDay;
    
    @ConfigProperty(name = "fraud.max.transactions.per.hour", defaultValue = "10")
    int maxTransactionsPerHour;
    
    public int calculateRiskScore(PaymentRequest request) {
        int riskScore = 0;
        
        // Check amount-based risk
        riskScore += calculateAmountRisk(request.amount);
        
        // Check frequency-based risk
        riskScore += calculateFrequencyRisk(request.customerId);
        
        // Check velocity-based risk
        riskScore += calculateVelocityRisk(request.customerId);
        
        // Check payment method risk
        riskScore += calculatePaymentMethodRisk(request.paymentMethod);
        
        // Check customer behavior risk
        riskScore += calculateCustomerBehaviorRisk(request.customerId);
        
        return Math.min(riskScore, 100);
    }
    
    private int calculateAmountRisk(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000000")) > 0) {
            return 30;
        } else if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            return 15;
        }
        return 0;
    }
    
    private int calculateFrequencyRisk(String customerId) {
        ValueCommands<String, String> commands = redisDataSource.value(String.class, String.class);
        String key = "fraud:frequency:" + customerId;
        String count = commands.get(key);
        
        int transactionCount = count != null ? Integer.parseInt(count) : 0;
        
        if (transactionCount > maxTransactionsPerHour) {
            return 40;
        } else if (transactionCount > maxTransactionsPerHour / 2) {
            return 20;
        }
        
        // Increment counter
        commands.setex(key, Duration.ofHours(1), String.valueOf(transactionCount + 1));
        
        return 0;
    }
    
    private int calculateVelocityRisk(String customerId) {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        List<Payment> recentPayments = Payment.find(
            "customerId = ?1 AND createdAt > ?2", 
            customerId, last24Hours).list();
        
        BigDecimal totalAmount = recentPayments.stream()
            .map(p -> p.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalAmount.compareTo(maxAmountPerDay) > 0) {
            return 35;
        }
        
        return 0;
    }
    
    private int calculatePaymentMethodRisk(com.payment.enums.PaymentMethod paymentMethod) {
        switch (paymentMethod) {
            case CREDIT_CARD:
            case DEBIT_CARD:
                return 10;
            case PAYLATER_KREDIVO:
            case PAYLATER_AKULAKU:
            case PAYLATER_INDODANA:
                return 20;
            default:
                return 0;
        }
    }
    
    private int calculateCustomerBehaviorRisk(String customerId) {
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<Payment> historicalPayments = Payment.find(
            "customerId = ?1 AND createdAt > ?2", 
            customerId, last30Days).list();
        
        if (historicalPayments.isEmpty()) {
            return 15; // New customer risk
        }
        
        long failedCount = historicalPayments.stream()
            .mapToLong(p -> p.status == PaymentStatus.FAILED ? 1 : 0)
            .sum();
        
        double failureRate = (double) failedCount / historicalPayments.size();
        
        if (failureRate > 0.3) {
            return 25;
        } else if (failureRate > 0.1) {
            return 10;
        }
        
        return 0;
    }
}