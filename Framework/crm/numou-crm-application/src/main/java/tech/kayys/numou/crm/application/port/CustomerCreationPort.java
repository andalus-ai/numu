package tech.kayys.numou.crm.application.port;

import tech.kayys.numou.crm.application.api.command.ConvertLeadCommand;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.domain.model.Lead;

/**
 * Port for creating customers from leads.
 */
public interface CustomerCreationPort {

    /**
     * Creates a customer from a lead.
     */
    Customer createCustomerFromLead(Lead lead, ConvertLeadCommand command);
}