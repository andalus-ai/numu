package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.foundation.domain.repository.Repository;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface CustomerRepository extends Repository<Customer, CustomerId> {
    CompletionStage<Customer> findByEmail(String email);
    CompletionStage<Customer> findByCustomerNumber(String customerNumber);
    CompletionStage<List<Customer>> findByCompanyName(String companyName);
    CompletionStage<List<Customer>> findByIndustry(String industry);
    CompletionStage<List<Customer>> findActiveCustomers();
    CompletionStage<Boolean> existsByEmail(String email);
    CompletionStage<Long> countByIndustry(String industry);
}
