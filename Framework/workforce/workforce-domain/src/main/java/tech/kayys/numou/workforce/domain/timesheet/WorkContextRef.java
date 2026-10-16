package tech.kayys.numou.workforce.domain.timesheet;

import java.util.Objects;

/**
 * Generic reference to an external business context (Project, Customer, Work Order, Cost Center).
 * Decouples Timesheet from external operational domains.
 */
public record WorkContextRef(String type, String id) {
    public WorkContextRef {
        Objects.requireNonNull(type, "Context type must not be null");
        Objects.requireNonNull(id, "Context id must not be null");
    }
    public static WorkContextRef project(String projectId) { return new WorkContextRef("PROJECT", projectId); }
    public static WorkContextRef workOrder(String workOrderId) { return new WorkContextRef("WORK_ORDER", workOrderId); }
    public static WorkContextRef customer(String customerId) { return new WorkContextRef("CUSTOMER", customerId); }
    public static WorkContextRef general(String id) { return new WorkContextRef("GENERAL", id); }
}
