package tech.kayys.numou.crm.application.lead;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.ConvertLeadCommand;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;

/**
 * Converts a qualified {@link Lead} into a {@link Customer} and marks
 * the lead CONVERTED. The lead's own status-transition rules are
 * enforced by the aggregate: converting a lead that is not QUALIFIED /
 * NURTURING raises {@link IllegalStateException}, which the REST
 * adapter maps to 409.
 */
public final class ConvertLeadHandler implements CommandHandler<ConvertLeadCommand, CustomerId> {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;

    public ConvertLeadHandler(LeadRepository leadRepository, CustomerRepository customerRepository) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository cannot be null");
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
    }

    @Override
    public Uni<CustomerId> handle(ConvertLeadCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(leadRepository.findById(command.leadId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Lead not found: " + command.leadId())))
                .flatMap(lead -> {
                    Customer customer = toCustomer(lead, command);
                    lead.changeStatus(LeadStatus.CONVERTED);

                    return Uni.createFrom().completionStage(customerRepository.save(customer))
                            .flatMap(saved -> Uni.createFrom()
                                    .completionStage(leadRepository.save(lead))
                                    .replaceWith(saved.getId()));
                });
    }

    private static Customer toCustomer(Lead lead, ConvertLeadCommand command) {
        CustomerId customerId = CustomerId.generate();
        String companyName = lead.getCompany() != null && !lead.getCompany().isBlank()
                ? lead.getCompany()
                : lead.getFullName();

        Customer customer = Customer.create(
                customerId,
                customerNumber(customerId),
                companyName,
                lead.getEmail(),
                command.currencyCode());
        customer.setFirstName(lead.getFirstName());
        customer.setLastName(lead.getLastName());
        customer.setPhone(lead.getPhone());
        customer.setIndustry(lead.getIndustry());
        customer.setPaymentTerms(command.paymentTerms());
        customer.setCreditLimit(command.creditLimit());
        return customer;
    }

    private static String customerNumber(CustomerId customerId) {
        return "CUST-" + customerId.getValue().toString().substring(0, 8).toUpperCase();
    }
}
