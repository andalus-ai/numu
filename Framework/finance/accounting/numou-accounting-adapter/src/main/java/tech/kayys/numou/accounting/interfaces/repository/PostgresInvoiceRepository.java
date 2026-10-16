package tech.kayys.syirkah.accounting.interfaces.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.repository.InvoiceRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@ApplicationScoped
public final class PostgresInvoiceRepository implements InvoiceRepository {
    @Inject DataSource dataSource;

    @Override
    public CustomerInvoice save(CustomerInvoice invoice) {
        String upsert = """
                INSERT INTO accounting.ar_receivable_invoice
                    (id, customer_id, customer_ref, currency, status, outstanding_balance)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET customer_id=EXCLUDED.customer_id,
                    customer_ref=EXCLUDED.customer_ref, currency=EXCLUDED.currency,
                    status=EXCLUDED.status, outstanding_balance=EXCLUDED.outstanding_balance,
                    updated_at=NOW()
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            connection.setAutoCommit(false);
            statement.setString(1, invoice.id().value());
            statement.setString(2, invoice.customerId().value());
            statement.setString(3, invoice.customerRef());
            statement.setString(4, invoice.currency());
            statement.setString(5, invoice.status().name());
            statement.setBigDecimal(6, invoice.outstandingBalance());
            statement.executeUpdate();
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM accounting.ar_receivable_invoice_line WHERE invoice_id = ?")) {
                delete.setString(1, invoice.id().value());
                delete.executeUpdate();
            }
            try (PreparedStatement line = connection.prepareStatement("""
                    INSERT INTO accounting.ar_receivable_invoice_line
                    (invoice_id, line_no, account_code, description, net_amount, tax_amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                int number = 0;
                for (var item : invoice.lines()) {
                    line.setString(1, invoice.id().value());
                    line.setInt(2, number++);
                    line.setString(3, item.revenueAccount());
                    line.setString(4, item.description());
                    line.setBigDecimal(5, item.netAmount());
                    line.setBigDecimal(6, item.taxAmount());
                    line.addBatch();
                }
                line.executeBatch();
            }
            connection.commit();
            return invoice;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist AR invoice " + invoice.id().value(), exception);
        }
    }

    @Override
    public Optional<CustomerInvoice> find(CustomerInvoiceId id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement invoiceQuery = connection.prepareStatement(
                     "SELECT customer_id, customer_ref, currency, status, outstanding_balance " +
                             "FROM accounting.ar_receivable_invoice WHERE id = ?")) {
            invoiceQuery.setString(1, id.value());
            try (ResultSet invoiceRow = invoiceQuery.executeQuery()) {
                if (!invoiceRow.next()) return Optional.empty();
                var lines = new ArrayList<CustomerInvoiceLine>();
                try (PreparedStatement lineQuery = connection.prepareStatement("""
                        SELECT account_code, description, net_amount, tax_amount
                        FROM accounting.ar_receivable_invoice_line WHERE invoice_id = ? ORDER BY line_no
                        """)) {
                    lineQuery.setString(1, id.value());
                    try (ResultSet lineRows = lineQuery.executeQuery()) {
                        while (lineRows.next()) lines.add(new CustomerInvoiceLine(
                                lineRows.getString(1), lineRows.getString(2),
                                lineRows.getBigDecimal(3), lineRows.getBigDecimal(4)));
                    }
                }
                var invoice = new CustomerInvoice(id, new CustomerId(invoiceRow.getString(1)),
                        invoiceRow.getString(2), invoiceRow.getString(3), lines);
                String status = invoiceRow.getString(4);
                invoice.restorePersistedState(CustomerInvoiceStatus.valueOf(status),
                        invoiceRow.getBigDecimal(5));
                return Optional.of(invoice);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load AR invoice " + id.value(), exception);
        }
    }
}
