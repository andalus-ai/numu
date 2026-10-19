package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.CreateCustomerCommand;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;
import java.util.UUID;

/**
 * Creates a {@link Customer} from the full creation payload, including
 * any nested contacts and addresses.
 */
public final class CreateCustomerHandler implements CommandHandler<CreateCustomerCommand, CustomerId> {

    private final CustomerRepository customerRepository;

    public CreateCustomerHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<CustomerId> handle(CreateCustomerCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Customer customer = Customer.create(
                command.customerId(),
                command.customerNumber(),
                command.companyName(),
                command.email(),
                command.currencyCode());
        customer.setFirstName(command.firstName());
        customer.setLastName(command.lastName());
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

        if (command.contacts() != null) {
            command.contacts().forEach(contact -> customer.addContact(new Customer.CustomerContact(
                    UUID.randomUUID().toString(),
                    contact.firstName(),
                    contact.lastName(),
                    contact.email(),
                    contact.phone(),
                    contact.jobTitle(),
                    contact.department(),
                    contact.primary(),
                    true)));
        }
        if (command.addresses() != null) {
            command.addresses().forEach(address -> customer.addAddress(new Customer.CustomerAddress(
                    UUID.randomUUID().toString(),
                    address.type(),
                    address.address(),
                    address.city(),
                    address.state(),
                    address.postalCode(),
                    address.country())));
        }

        return Uni.createFrom().completionStage(customerRepository.save(customer))
                .replaceWith(customer.getId());
    }
}
