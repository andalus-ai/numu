package tech.kayys.syirkah.accounting.receivables.repository;

import tech.kayys.syirkah.accounting.domain.ar.*;
import java.util.Optional;

public interface PaymentRepository {
    CustomerPayment save(CustomerPayment payment);
    Optional<CustomerPayment> find(CustomerPaymentId id);
}
