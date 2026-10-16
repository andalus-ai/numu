package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.CustomerView;
import tech.kayys.numou.crm.application.api.query.GetCustomerQuery;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.Objects;

/**
 * Reads a single customer and projects it to {@link CustomerView}.
 * A missing customer surfaces as {@link IllegalArgumentException}
 * (mapped to 404 by the REST adapter).
 */
public final class GetCustomerQueryHandler implements QueryHandler<GetCustomerQuery, CustomerView> {

    private final CustomerRepository customerRepository;

    public GetCustomerQueryHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<CustomerView> handle(GetCustomerQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(customerRepository.findById(query.customerId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Customer not found: " + query.customerId())))
                .map(CustomerView::fromDomain);
    }
}
