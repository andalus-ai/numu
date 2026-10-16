package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.AddCustomerContactCommand;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;
import java.util.UUID;

/**
 * Adds a contact to an existing {@link Customer}. Marking the contact
 * primary de-flags any existing primary contact (aggregate invariant).
 */
public final class AddCustomerContactHandler implements CommandHandler<AddCustomerContactCommand, Void> {

    private final CustomerRepository customerRepository;

    public AddCustomerContactHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<Void> handle(AddCustomerContactCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(customerRepository.findById(command.customerId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Customer not found: " + command.customerId())))
                .flatMap(customer -> {
                    customer.addContact(new Customer.CustomerContact(
                            UUID.randomUUID().toString(),
                            command.firstName(),
                            command.lastName(),
                            command.email(),
                            command.phone(),
                            command.jobTitle(),
                            command.department(),
                            command.primary(),
                            true));
                    return Uni.createFrom().completionStage(customerRepository.save(customer));
                })
                .flatMap(saved -> Uni.createFrom().voidItem());
    }
}
