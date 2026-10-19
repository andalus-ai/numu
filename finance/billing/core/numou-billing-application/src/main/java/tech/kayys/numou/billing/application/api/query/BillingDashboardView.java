package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Billing dashboard analytics view.
 */
public record BillingDashboardView(
        // Revenue Metrics
        Money totalRevenue,
        Money monthlyRecurringRevenue,
        Money annualRecurringRevenue,
        Money averageRevenuePerCustomer,
        
        // Growth Metrics
        double monthOverMonthGrowth,
        double yearOverYearGrowth,
        double revenueGrowthRate,
        
        // Customer Metrics
        long totalCustomers,
        long activeCustomers,
        long churnedCustomers,
        double customerChurnRate,
        double customerRetentionRate,
        
        // Billing Metrics
        long totalInvoices,
        long overdueInvoices,
        long upcomingInvoices,
        double invoicePaymentRate,
        double averageInvoiceAmount,
        
        // Revenue Breakdown
        Map<String, Money> revenueByPlan,
        Map<String, Money> revenueByRegion,
        Map<String, Money> revenueByChannel,
        
        // Trends
        List<RevenueTrend> revenueTrends,
        List<CustomerGrowth> customerGrowth,
        
        // Dunning Metrics
        long dunningEmailsSent,
        long dunningSmsSent,
        long successfulRetries,
        long failedPayments,
        double paymentSuccessRate,
        
        // Period
        Instant periodStart,
        Instant periodEnd,
        String currencyCode,
        Instant generatedAt
) {

    /**
     * Revenue trend record.
     */
    public record RevenueTrend(
            String period,
            Money revenue,
            Money previousPeriodRevenue,
            double growthRate
    ) {}

    /**
     * Customer growth record.
     */
    public record CustomerGrowth(
            String period,
            long newCustomers,
            long activeCustomers,
            long churnedCustomers
    ) {}
}