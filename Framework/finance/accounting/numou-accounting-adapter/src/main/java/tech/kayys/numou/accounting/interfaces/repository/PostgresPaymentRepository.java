package tech.kayys.syirkah.accounting.interfaces.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.repository.PaymentRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

@ApplicationScoped
public final class PostgresPaymentRepository implements PaymentRepository {
    @Inject DataSource dataSource;

    @Override
    public CustomerPayment save(CustomerPayment payment) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO accounting.ar_receivable_payment
                       (id, customer_id, currency, amount, received_at, status)
                     VALUES (?, ?, ?, ?, ?, ?)
                     ON CONFLICT (id) DO UPDATE SET status=EXCLUDED.status
                     """)) {
            statement.setString(1, payment.id().value());
            statement.setString(2, payment.customerId().value());
            statement.setString(3, payment.currency());
            statement.setBigDecimal(4, payment.amount());
            statement.setTimestamp(5, Timestamp.from(payment.receivedAt()));
            statement.setString(6, payment.status().name());
            statement.executeUpdate();
            return payment;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist AR payment " + payment.id().value(), exception);
        }
    }

    @Override
    public Optional<CustomerPayment> find(CustomerPaymentId id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT customer_id, currency, amount, received_at, status
                     FROM accounting.ar_receivable_payment WHERE id = ?
                     """)) {
            statement.setString(1, id.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                var payment = new CustomerPayment(id, new CustomerId(row.getString(1)),
                        row.getString(2), row.getBigDecimal(3), row.getTimestamp(4).toInstant());
                if ("APPLIED".equals(row.getString(5))) payment.markApplied();
                return Optional.of(payment);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load AR payment " + id.value(), exception);
        }
    }
}
