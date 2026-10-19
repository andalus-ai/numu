package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.query.CustomerView;
import tech.kayys.numou.crm.application.api.query.SearchCustomersQuery;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Searches customers. The repository exposes single-criterion finders,
 * so the most selective supplied criterion drives the query and the
 * remaining criteria (email, city, country) plus paging are applied to
 * the result set.
 */
public final class SearchCustomersQueryHandler implements QueryHandler<SearchCustomersQuery, List<CustomerView>> {

    private final CustomerRepository customerRepository;

    public SearchCustomersQueryHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<List<CustomerView>> handle(SearchCustomersQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        return Uni.createFrom().completionStage(find(query))
                .map(customers -> customers.stream()
                        .filter(customer -> matchesEmail(customer, query.email()))
                        .filter(customer -> matchesCity(customer, query.city()))
                        .filter(customer -> matchesCountry(customer, query.country()))
                        .skip((long) Math.max(query.page(), 0) * pageSize(query.size()))
                        .limit(pageSize(query.size()))
                        .map(CustomerView::fromDomain)
                        .collect(Collectors.toList()));
    }

    private CompletionStage<List<Customer>> find(SearchCustomersQuery query) {
        if (isPresent(query.companyName())) {
            return customerRepository.findByCompanyName(query.companyName());
        }
        if (isPresent(query.industry())) {
            return customerRepository.findByIndustry(query.industry());
        }
        if (isPresent(query.email())) {
            return customerRepository.findByEmail(query.email())
                    .thenApply(customer -> customer == null ? List.<Customer>of() : List.of(customer));
        }
        return customerRepository.findActiveCustomers();
    }

    private static boolean matchesEmail(Customer customer, String email) {
        return !isPresent(email) || email.equalsIgnoreCase(customer.getEmail());
    }

    private static boolean matchesCity(Customer customer, String city) {
        return !isPresent(city) || (customer.getCity() != null && customer.getCity().equalsIgnoreCase(city));
    }

    private static boolean matchesCountry(Customer customer, String country) {
        return !isPresent(country)
                || (customer.getCountry() != null && customer.getCountry().equalsIgnoreCase(country));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static int pageSize(int size) {
        return size <= 0 ? 20 : size;
    }
}
