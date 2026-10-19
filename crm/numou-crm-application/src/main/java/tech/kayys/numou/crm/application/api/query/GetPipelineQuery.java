package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;

import java.util.UUID;

/**
 * Pipeline projection query. Both criteria are optional: when
 * {@code customerId} is present the pipeline is scoped to that
 * customer, and {@code assignedTo} (a user id string) narrows it to a
 * single salesperson.
 */
public record GetPipelineQuery(String assignedTo, UUID customerId) implements Query {}
