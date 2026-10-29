package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.CustomerEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.CustomerMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Customer persistence adapter. Every read and mutation is scoped by a tenant
 * derived from the trusted runtime, never from a customer command.
 */
@ApplicationScoped
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public CustomerRepositoryImpl(CustomerMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        TenantScope scope = tenantScopeProvider.requireTenantScope();
        return scope.tenantId().value();
    }

    @Override
    public CompletionStage<Customer> save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("customer is required");
        }
        UUID tenant = tenantId();
        CustomerEntity mapped = mapper.toEntity(customer);
        mapped.tenantId = tenant;

        return Panache.withTransaction(() -> {
            if (mapped.id == null) {
                return mapped.<CustomerEntity>persist().map(saved -> {
                    customer.clearEvents();
                    return customer;
                });
            }
            return CustomerEntity.<CustomerEntity>find("id = ?1 and tenantId = ?2", mapped.id, tenant)
                .firstResult()
                .flatMap(existing -> {
                    if (existing == null) {
                        // Persisting a foreign ID can only fail on the global PK; it can never overwrite it.
                        return mapped.<CustomerEntity>persist().map(saved -> {
                            customer.clearEvents();
                            return customer;
                        });
                    }
                    copyCustomerFields(mapped, existing);
                    return Uni.createFrom().item(existing).map(saved -> {
                        customer.clearEvents();
                        return customer;
                    });
                });
        }).subscribe().asCompletionStage();
    }

    private static void copyCustomerFields(CustomerEntity source, CustomerEntity target) {
        target.customerNumber = source.customerNumber;
        target.companyName = source.companyName;
        target.firstName = source.firstName;
        target.lastName = source.lastName;
        target.email = source.email;
        target.phone = source.phone;
        target.address = source.address;
        target.city = source.city;
        target.state = source.state;
        target.postalCode = source.postalCode;
        target.country = source.country;
        target.industry = source.industry;
        target.website = source.website;
        target.taxId = source.taxId;
        target.currencyCode = source.currencyCode;
        target.paymentTerms = source.paymentTerms;
        target.creditLimit = source.creditLimit;
        target.accountStatus = source.accountStatus;
        target.notes = source.notes;
        target.active = source.active;
        target.updatedAt = source.updatedAt;
        target.contacts = source.contacts;
        target.addresses = source.addresses;
    }

    @Override
    public CompletionStage<Optional<Customer>> findById(CustomerId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>find("id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .firstResult()
            .map(entity -> entity == null ? Optional.<Customer>empty() : Optional.of(mapper.toDomain(entity))))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(CustomerId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.count("id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .map(count -> count > 0))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Customer customer) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        return deleteById(customer.getId());
    }

    @Override
    public CompletionStage<Void> deleteById(CustomerId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> CustomerEntity.delete("id = ?1 and tenantId = ?2", id.getValue(), tenant)
            .map(deleted -> (Void) null))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Customer> findByEmail(String email) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>find("tenantId = ?1 and email = ?2", tenant, email)
            .firstResult().map(entity -> entity == null ? null : mapper.toDomain(entity)))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Customer> findByCustomerNumber(String customerNumber) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>find("tenantId = ?1 and customerNumber = ?2", tenant, customerNumber)
            .firstResult().map(entity -> entity == null ? null : mapper.toDomain(entity)))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findByCompanyName(String companyName) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("tenantId = ?1 and companyName like ?2", tenant, "%" + companyName + "%")
            .map(entities -> entities.stream().map(mapper::toDomain).collect(Collectors.toList())))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findByIndustry(String industry) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("tenantId = ?1 and industry = ?2", tenant, industry)
            .map(entities -> entities.stream().map(mapper::toDomain).collect(Collectors.toList())))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<Customer>> findActiveCustomers() {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.<CustomerEntity>list("tenantId = ?1 and active = true", tenant)
            .map(entities -> entities.stream().map(mapper::toDomain).collect(Collectors.toList())))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsByEmail(String email) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.count("tenantId = ?1 and email = ?2", tenant, email)
            .map(count -> count > 0))
            .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Long> countByIndustry(String industry) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> CustomerEntity.count("tenantId = ?1 and industry = ?2", tenant, industry))
            .subscribe().asCompletionStage();
    }
}
