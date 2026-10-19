package tech.kayys.numou.accounting.receivables.repository;

import tech.kayys.numou.accounting.domain.ar.*;
import java.util.Optional;

public interface PaymentRepository {
    CustomerPayment save(CustomerPayment payment);
    Optional<CustomerPayment> find(CustomerPaymentId id);
}
