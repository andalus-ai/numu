package tech.kayys.bill.domain;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.transaction.Transaction;
import tech.kayys.bill.model.BillSplitStatus;
import tech.kayys.bill.model.BillSplitType;
import tech.kayys.payment.model.PaymentMethod;
import tech.kayys.sy.tenant.TenantBaseEntity;

@Entity
@Table(name = "bill_splits")
public class BillSplit extends TenantBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    public Transaction transaction;
    
    @Column(name = "split_number", nullable = false)
    public Integer splitNumber;
    
    @Column(name = "split_type", nullable = false)
    @Enumerated(EnumType.STRING)
    public BillSplitType splitType;
    
    @OneToMany(mappedBy = "billSplit", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<BillSplitItem> items = new ArrayList<>();
    
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal;
    
    @Column(name = "ppn_amount", precision = 19, scale = 2)
    public BigDecimal ppnAmount;
    
    @Column(name = "service_charge", precision = 19, scale = 2)
    public BigDecimal serviceCharge;
    
    @Column(precision = 19, scale = 2)
    public BigDecimal discount;
    
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    public PaymentMethod paymentMethod;
    
    @Column(precision = 19, scale = 2)
    public BigDecimal paid;
    
    @Column(name = "change_amount", precision = 19, scale = 2)
    public BigDecimal changeAmount;
    
    @Enumerated(EnumType.STRING)
    public BillSplitStatus status;
    
    @Column(name = "customer_name")
    public String customerName;
    
    @Column(name = "paid_at")
    public java.time.LocalDateTime paidAt;
}