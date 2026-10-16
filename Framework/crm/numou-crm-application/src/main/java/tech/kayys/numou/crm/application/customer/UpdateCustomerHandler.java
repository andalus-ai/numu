package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.UpdateCustomerCommand;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;

/**
 * Applies a full update to an existing {@link Customer}. A missing
 * customer surfaces as {@link IllegalArgumentException} (404).
 */
public final class UpdateCustomerHandler implements CommandHandler<UpdateCustomerCommand, Void> {

    private final CustomerRepository customerRepository;

    public UpdateCustomerHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<Void> handle(UpdateCustomerCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(customerRepository.findById(command.customerId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Customer not found: " + command.customerId())))
                .flatMap(customer -> {
                    apply(customer, command);
                    return Uni.createFrom().completionStage(customerRepository.save(customer));
                })
                .flatMap(saved -> Uni.createFrom().voidItem());
    }

    private static void apply(Customer customer, UpdateCustomerCommand command) {
        customer.setCompanyName(command.companyName());
        customer.setFirstName(command.firstName());
        customer.setLastName(command.lastName());
        customer.setEmail(command.email());
        customer.setPhone(command.phone());
        customer.setAddress(command.address());
        customer.setCity(command.city());
        customer.setState(command.state());
        customer.setPostalCode(command.postalCode());
        customer.setCountry(command.country());
        customer.setIndustry(command.industry());
        customer.setWebsite(command.website());
        customer.setTaxId(command.taxId());
        customer.setPaymentTerms(command.paymentTerms());
        customer.setCreditLimit(command.creditLimit());
        customer.setAccountStatus(command.accountStatus());
        customer.setNotes(command.notes());
    }
}
