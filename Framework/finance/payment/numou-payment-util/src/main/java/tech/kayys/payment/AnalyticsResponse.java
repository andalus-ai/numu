package tech.kayys.payment.dto;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class AnalyticsResponse {
    public BigDecimal totalAmount;
    public Long totalTransactions;
    public BigDecimal successRate;
    public BigDecimal averageAmount;
    public List<DailySummary> dailySummaries;
    public Map<String, Long> paymentMethodDistribution;
    public Map<String, BigDecimal> revenueByPaymentMethod;
    
    public static class DailySummary {
        public LocalDate date;
        public BigDecimal amount;
        public Long transactions;
        public BigDecimal successRate;
    }
}