package tech.kayys.payment;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Invoice reference entity for payment associations
 */
@Entity
@Table(name = "payment_invoices")
public class Invoice extends PanacheEntity {
    public String invoiceNumber;
    public String customerId;
}
