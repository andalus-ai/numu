package tech.kayys.bill.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tech.kayys.transaction.domain.TransactionItem;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_split_items")
public class BillSplitItem extends PanacheEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_split_id", nullable = false)
    public BillSplit billSplit;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_item_id", nullable = false)
    public TransactionItem transactionItem;
    
    @Column(nullable = false)
    public Integer quantity;
    
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    public BigDecimal unitPrice;
    
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal;
}