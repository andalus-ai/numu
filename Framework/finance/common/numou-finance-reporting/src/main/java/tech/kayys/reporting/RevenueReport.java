package tech.kayys.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import tech.kayys.billing.domain.Payment.PaymentMethod;

public class RevenueReport {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenue;
    private BigDecimal totalTax;
    private BigDecimal totalCollected;
    private int totalInvoices;
    private Map<PaymentMethod, BigDecimal> paymentMethodBreakdown;
    
    public LocalDate getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    
    public LocalDate getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    
    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }
    
    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
    
    public BigDecimal getTotalTax() {
        return totalTax;
    }
    
    public void setTotalTax(BigDecimal totalTax) {
        this.totalTax = totalTax;
    }
    
    public BigDecimal getTotalCollected() {
        return totalCollected;
    }
    
    public void setTotalCollected(BigDecimal totalCollected) {
        this.totalCollected = totalCollected;
    }
    
    public int getTotalInvoices() {
        return totalInvoices;
    }
    
    public void setTotalInvoices(int totalInvoices) {
        this.totalInvoices = totalInvoices;
    }
    
    public Map<PaymentMethod, BigDecimal> getPaymentMethodBreakdown() {
        return paymentMethodBreakdown;
    }
    
    public void setPaymentMethodBreakdown(Map<PaymentMethod, BigDecimal> paymentMethodBreakdown) {
        this.paymentMethodBreakdown = paymentMethodBreakdown;
    }
}