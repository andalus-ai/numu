package tech.kayys.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CustomerAnalysisReport {
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalCustomers;
    private BigDecimal totalRevenue;
    private BigDecimal averageRevenuePerCustomer;
    private List<CustomerRevenueDTO> topCustomers;
    
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
    
    public int getTotalCustomers() {
        return totalCustomers;
    }
    
    public void setTotalCustomers(int totalCustomers) {
        this.totalCustomers = totalCustomers;
    }
    
    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }
    
    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
    
    public BigDecimal getAverageRevenuePerCustomer() {
        return averageRevenuePerCustomer;
    }
    
    public void setAverageRevenuePerCustomer(BigDecimal averageRevenuePerCustomer) {
        this.averageRevenuePerCustomer = averageRevenuePerCustomer;
    }
    
    public List<CustomerRevenueDTO> getTopCustomers() {
        return topCustomers;
    }
    
    public void setTopCustomers(List<CustomerRevenueDTO> topCustomers) {
        this.topCustomers = topCustomers;
    }
}
