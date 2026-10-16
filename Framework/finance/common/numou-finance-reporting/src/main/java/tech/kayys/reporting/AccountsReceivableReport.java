package tech.kayys.reporting;

import java.math.BigDecimal;
import java.util.Map;

public class AccountsReceivableReport {
    private BigDecimal totalReceivable;
    private Map<Integer, BigDecimal> agingBuckets;
    private long totalOverdueInvoices;
    
    public BigDecimal getTotalReceivable() {
        return totalReceivable;
    }
    
    public void setTotalReceivable(BigDecimal totalReceivable) {
        this.totalReceivable = totalReceivable;
    }
    
    public Map<Integer, BigDecimal> getAgingBuckets() {
        return agingBuckets;
    }
    
    public void setAgingBuckets(Map<Integer, BigDecimal> agingBuckets) {
        this.agingBuckets = agingBuckets;
    }
    
    public long getTotalOverdueInvoices() {
        return totalOverdueInvoices;
    }
    
    public void setTotalOverdueInvoices(long totalOverdueInvoices) {
        this.totalOverdueInvoices = totalOverdueInvoices;
    }
}
