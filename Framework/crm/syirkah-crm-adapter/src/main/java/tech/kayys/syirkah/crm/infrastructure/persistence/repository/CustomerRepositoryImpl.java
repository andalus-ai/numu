package tech.kayys.syirkah.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.crm.domain.identifier.CustomerId;
import tech.kayys.syirkah.crm.domain.model.Customer;
import tech.kayys.syirkah.crm.domain.repository.CustomerRepository;
import tech.kayys.syirkah.crm.infrastructure.persistence.entity.CustomerEntity;
import tech.kayys.syirkah.crm.infrastructure.persistence.mapper.CustomerMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Implementation of CustomerRepository.
 */
@ApplicationScoped
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerMapper mapper;

    public CustomerRepositoryImpl(CustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Customer> save(Customer customer) {
        CustomerEntity entity = mapper.toEntity(customer);
        
        return Panache.withTransaction(() -> entity.<CustomerEntity>persist()
            .map(v -> {
                customer.clearEvents();
                return customer;
            })
        ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Customer>> findById(CustomerId id) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>findById(id.getValue())
            .map(entity -> entity == null ? Optional.<Customer>empty() : Optional.of(mapper.toDomain(entity)))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(CustomerId id) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>findById(id.getValue())
            .map(entity -> entity != null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Customer customer) {
    return Panache.withTransaction(() -> CustomerEntity.deleteById(customer.getId().getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Void> deleteById(CustomerId id) {
    return Panache.withTransaction(() -> CustomerEntity.deleteById(id.getValue())
            .map(v -> (Void) null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Customer> findByEmail(String email) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>find("email = ?1", email).firstResult()
            .map(entity -> entity != null ? mapper.toDomain(entity) : null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Customer> findByCustomerNumber(String customerNumber) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>find("customerNumber = ?1", customerNumber).firstResult()
            .map(entity -> entity != null ? mapper.toDomain(entity) : null)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findByCompanyName(String companyName) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("companyName like ?1", "%" + companyName + "%")
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findByIndustry(String industry) {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("industry = ?1", industry)
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findActiveCustomers() {
    return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("active = true")
            .map(entities -> entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList()))
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsByEmail(String email) {
    return Panache.withSession(() -> CustomerEntity.count("email = ?1", email)
            .map(count -> count > 0)
            ).subscribe()
            .asCompletionStage();
    }

    @Override
    public CompletionStage<Long> countByIndustry(String industry) {
    return Panache.withSession(() -> CustomerEntity.count("industry = ?1", industry)
            ).subscribe()
            .asCompletionStage();
    }
}