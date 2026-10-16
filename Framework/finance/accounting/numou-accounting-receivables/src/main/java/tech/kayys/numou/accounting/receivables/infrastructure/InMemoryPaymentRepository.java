package tech.kayys.syirkah.accounting.receivables.infrastructure;

import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.repository.PaymentRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<CustomerPaymentId, CustomerPayment> payments = new ConcurrentHashMap<>();
    @Override public CustomerPayment save(CustomerPayment payment) { payments.put(payment.id(), payment); return payment; }
    @Override public Optional<CustomerPayment> find(CustomerPaymentId id) { return Optional.ofNullable(payments.get(id)); }
}
