package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.foundation.application.query.Query;

public record GetCustomerQuery(CustomerId customerId) implements Query {}
