package tech.kayys.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.billing.domain.BillingAccount;
import tech.kayys.billing.domain.Invoice;
import tech.kayys.billing.domain.Payment;
import tech.kayys.billing.domain.Payment.PaymentMethod;

@ApplicationScoped
public class ReportingService {
    
    public RevenueReport generateRevenueReport(LocalDate startDate, LocalDate endDate) {
        RevenueReport report = new RevenueReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        
        // Get all invoices within the date range
        List<Invoice> invoices = Invoice.list(
            "invoiceDate >= ?1 and invoiceDate <= ?2", 
            startDate, endDate);
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        
        for (Invoice invoice : invoices) {
            BigDecimal subtotal = invoice.subtotal != null ? invoice.subtotal : BigDecimal.ZERO;
            BigDecimal tax = invoice.taxAmount != null ? invoice.taxAmount : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(subtotal);
            totalTax = totalTax.add(tax);
        }
        
        report.setTotalRevenue(totalRevenue);
        report.setTotalTax(totalTax);
        report.setTotalInvoices(invoices.size());
        
        // Get all payments within the date range
        List<Payment> payments = Payment.list(
            "paymentDate >= ?1 and paymentDate <= ?2 and status = ?3", 
            startDate, endDate, Payment.PaymentStatus.COMPLETED);
        
        BigDecimal totalCollected = BigDecimal.ZERO;
        Map<PaymentMethod, BigDecimal> paymentMethodBreakdown = new HashMap<>();
        
        for (Payment payment : payments) {
            BigDecimal amount = payment.amount != null ? payment.amount : BigDecimal.ZERO;
            totalCollected = totalCollected.add(amount);
            
            // Accumulate by payment method
            PaymentMethod method = payment.paymentMethod != null ? payment.paymentMethod : PaymentMethod.CREDIT_CARD;
            BigDecimal currentAmount = paymentMethodBreakdown.getOrDefault(method, BigDecimal.ZERO);
            paymentMethodBreakdown.put(method, currentAmount.add(amount));
        }
        
        report.setTotalCollected(totalCollected);
        report.setPaymentMethodBreakdown(paymentMethodBreakdown);
        
        return report;
    }
    
    public AccountsReceivableReport generateAccountsReceivableReport() {
        AccountsReceivableReport report = new AccountsReceivableReport();
        
        // Get all unpaid invoices
        List<Invoice> unpaidInvoices = Invoice.list(
            "status in (?1, ?2, ?3)", 
            Invoice.InvoiceStatus.SENT, 
            Invoice.InvoiceStatus.PARTIAL, 
            Invoice.InvoiceStatus.OVERDUE);
        
        BigDecimal totalReceivable = BigDecimal.ZERO;
        Map<Integer, BigDecimal> agingBuckets = new HashMap<>();
        
        // Initialize aging buckets (0-30 days, 31-60 days, 61-90 days, 90+ days)
        agingBuckets.put(30, BigDecimal.ZERO);
        agingBuckets.put(60, BigDecimal.ZERO);
        agingBuckets.put(90, BigDecimal.ZERO);
        agingBuckets.put(Integer.MAX_VALUE, BigDecimal.ZERO);
        
        LocalDate today = LocalDate.now();
        
        for (Invoice invoice : unpaidInvoices) {
            BigDecimal balance = invoice.balanceDue != null ? invoice.balanceDue : 
                (invoice.totalAmount != null ? invoice.totalAmount : BigDecimal.ZERO);
            totalReceivable = totalReceivable.add(balance);
            
            // Calculate days outstanding
            LocalDate invDate = invoice.invoiceDate != null ? invoice.invoiceDate : today;
            long daysOutstanding = java.time.temporal.ChronoUnit.DAYS.between(invDate, today);
            
            // Add to the appropriate aging bucket
            if (daysOutstanding <= 30) {
                agingBuckets.put(30, agingBuckets.get(30).add(balance));
            } else if (daysOutstanding <= 60) {
                agingBuckets.put(60, agingBuckets.get(60).add(balance));
            } else if (daysOutstanding <= 90) {
                agingBuckets.put(90, agingBuckets.get(90).add(balance));
            } else {
                agingBuckets.put(Integer.MAX_VALUE, agingBuckets.get(Integer.MAX_VALUE).add(balance));
            }
        }
        
        report.setTotalReceivable(totalReceivable);
        report.setAgingBuckets(agingBuckets);
        report.setTotalOverdueInvoices(
            unpaidInvoices.stream()
                .filter(i -> i.status == Invoice.InvoiceStatus.OVERDUE)
                .count()
        );
        
        return report;
    }
    
    public CustomerAnalysisReport generateCustomerAnalysisReport(LocalDate startDate, LocalDate endDate) {
        CustomerAnalysisReport report = new CustomerAnalysisReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        
        // Get all billing accounts
        List<BillingAccount> activeAccounts = BillingAccount.listAll();
        report.setTotalCustomers(activeAccounts.size());
        
        // Get all invoices for the period
        List<Invoice> periodInvoices = Invoice.list(
            "invoiceDate >= ?1 and invoiceDate <= ?2", 
            startDate, endDate);
        
        // Map to track invoices by billing account ID
        Map<Long, List<Invoice>> invoicesByAccount = periodInvoices.stream()
            .filter(i -> i.billingAccount != null && i.billingAccount.id != null)
            .collect(Collectors.groupingBy(i -> i.billingAccount.id));
        
        // Calculate metrics
        BigDecimal totalRevenue = periodInvoices.stream()
            .map(i -> i.totalAmount != null ? i.totalAmount : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        report.setTotalRevenue(totalRevenue);
        
        if (!activeAccounts.isEmpty()) {
            report.setAverageRevenuePerCustomer(
                totalRevenue.divide(BigDecimal.valueOf(activeAccounts.size()), 2, RoundingMode.HALF_UP)
            );
        } else {
            report.setAverageRevenuePerCustomer(BigDecimal.ZERO);
        }
        
        // Find top customers by revenue
        List<CustomerRevenueDTO> topCustomersByRevenue = new ArrayList<>();
        
        for (Map.Entry<Long, List<Invoice>> entry : invoicesByAccount.entrySet()) {
            Long accountId = entry.getKey();
            List<Invoice> customerInvoices = entry.getValue();
            
            BigDecimal customerRevenue = customerInvoices.stream()
                .map(i -> i.totalAmount != null ? i.totalAmount : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BillingAccount account = BillingAccount.findById(accountId);
            String name = account != null && account.customerName != null ? account.customerName : "Account #" + accountId;
            
            topCustomersByRevenue.add(new CustomerRevenueDTO(
                accountId, name, customerRevenue
            ));
        }
        
        // Sort by revenue descending and take top 10
        topCustomersByRevenue.sort(Comparator.comparing(CustomerRevenueDTO::getRevenue).reversed());
        report.setTopCustomers(topCustomersByRevenue.stream().limit(10).collect(Collectors.toList()));
        
        return report;
    }
}