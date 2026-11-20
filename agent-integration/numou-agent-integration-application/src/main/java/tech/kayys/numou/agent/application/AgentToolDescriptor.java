package tech.kayys.numou.agent.application;

import java.util.Map;
import java.util.Objects;

/**
 * Declarative, versioned description of an operation exposed to an agent.
 * This descriptor is metadata only; it does not contain executable code.
 */
public record AgentToolDescriptor(
        String toolId,
        String version,
        String capabilityId,
        String description,
        String requiredPermission,
        AgentToolOperation operation,
        boolean approvalRequired,
        Map<String, Object> inputSchema) {

    public AgentToolDescriptor {
        toolId = required(toolId, "toolId");
        version = required(version, "version");
        capabilityId = required(capabilityId, "capabilityId");
        description = required(description, "description");
        requiredPermission = required(requiredPermission, "requiredPermission");
        Objects.requireNonNull(operation, "operation");
        inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema"));
        if (inputSchema.isEmpty()) {
            throw new IllegalArgumentException("inputSchema must not be empty");
        }
        if ((operation == AgentToolOperation.WRITE || operation == AgentToolOperation.ADMINISTER)
                && !approvalRequired) {
            throw new IllegalArgumentException("Mutating and administrative tools must require approval");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}