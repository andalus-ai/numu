package tech.kayys.numou.foundation.application.command;

import tech.kayys.numou.foundation.application.context.ExecutionContext;

import java.time.Instant;
import java.util.*;

/**
 * Execution context metadata for a specific command dispatch invocation.
 */
public final class CommandContext {

    private final ExecutionContext executionContext;
    private final Command command;
    private final Instant createdAt;
    private final Map<String, Object> attributes;

    public CommandContext(ExecutionContext executionContext, Command command) {
        this(executionContext, command, Map.of());
    }

    public CommandContext(ExecutionContext executionContext, Command command, Map<String, Object> attributes) {
        this.executionContext = executionContext != null ? executionContext : ExecutionContext.empty();
        this.command = Objects.requireNonNull(command, "command cannot be null");
        this.createdAt = Instant.now();
        this.attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Map.of();
    }

    public static CommandContext of(Command command) {
        return new CommandContext(ExecutionContext.empty(), command);
    }

    public static CommandContext of(ExecutionContext executionContext, Command command) {
        return new CommandContext(executionContext, command);
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    public Command command() {
        return command;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }

    public CommandContext withAttribute(String key, Object value) {
        var map = new LinkedHashMap<>(this.attributes);
        map.put(key, value);
        return new CommandContext(this.executionContext, this.command, map);
    }

    public CommandContext withExecutionContext(ExecutionContext context) {
        return new CommandContext(context, this.command, this.attributes);
    }
}
