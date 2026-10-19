package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.context.ActorId;
import tech.kayys.numou.foundation.application.context.CorrelationId;
import tech.kayys.numou.foundation.application.context.CurrentExecutionContext;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.application.middleware.ContextMiddleware;
import tech.kayys.numou.foundation.application.middleware.ErrorHandlingCommandMiddleware;
import tech.kayys.numou.foundation.application.middleware.ValidationMiddleware;
import tech.kayys.numou.foundation.application.validation.Validatable;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CommandBusTest {

    record PingCommand(String message) implements Command {}
    record ValidatedCommand(String value) implements Command, Validatable {
        @Override
        public void validate() {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Value cannot be blank");
            }
        }
    }

    @Test
    void shouldDispatchCommandToRegisteredHandler() {
        var bus = DefaultCommandBus.builder()
                .register(PingCommand.class, cmd -> Uni.createFrom().item("Pong: " + cmd.message()))
                .build();

        String result = bus.<PingCommand, String>dispatch(new PingCommand("Hello"))
                .await().atMost(Duration.ofSeconds(2));

        assertEquals("Pong: Hello", result);
    }

    @Test
    void shouldExecuteMiddlewaresInPipeline() {
        List<String> auditTrail = new ArrayList<>();

        CommandMiddleware middlewareA = new CommandMiddleware() {
            @Override
            public <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next) {
                auditTrail.add("pre-A");
                return next.proceed().invoke(res -> auditTrail.add("post-A"));
            }
        };

        CommandMiddleware middlewareB = new CommandMiddleware() {
            @Override
            public <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next) {
                auditTrail.add("pre-B");
                return next.proceed().invoke(res -> auditTrail.add("post-B"));
            }
        };

        var bus = DefaultCommandBus.builder()
                .register(PingCommand.class, cmd -> {
                    auditTrail.add("handler");
                    return Uni.createFrom().item("ok");
                })
                .addMiddleware(middlewareA)
                .addMiddleware(middlewareB)
                .build();

        bus.dispatch(new PingCommand("test")).await().atMost(Duration.ofSeconds(2));

        assertEquals(List.of("pre-A", "pre-B", "handler", "post-B", "post-A"), auditTrail);
    }

    @Test
    void shouldPropagateExecutionContextViaContextMiddleware() {
        AtomicBoolean contextVerified = new AtomicBoolean(false);

        var bus = DefaultCommandBus.builder()
                .register(PingCommand.class, cmd -> {
                    var current = CurrentExecutionContext.getOrEmpty();
                    if ("actor-123".equals(current.actorId().value())) {
                        contextVerified.set(true);
                    }
                    return Uni.createFrom().item("done");
                })
                .addMiddleware(new ContextMiddleware())
                .build();

        var execContext = ExecutionContext.builder()
                .actorId(ActorId.of("actor-123"))
                .correlationId(CorrelationId.generate())
                .build();

        var cmdContext = CommandContext.of(execContext, new PingCommand("hi"));
        bus.dispatch(new PingCommand("hi"), cmdContext).await().atMost(Duration.ofSeconds(2));

        assertTrue(contextVerified.get());
        assertTrue(CurrentExecutionContext.get().isEmpty(), "CurrentExecutionContext should be cleared after execution");
    }

    @Test
    void shouldValidateCommandViaValidationMiddleware() {
        var bus = DefaultCommandBus.builder()
                .register(ValidatedCommand.class, cmd -> Uni.createFrom().item("valid!"))
                .addMiddleware(new ValidationMiddleware())
                .build();

        // Valid
        String validRes = bus.<ValidatedCommand, String>dispatch(new ValidatedCommand("ok"))
                .await().atMost(Duration.ofSeconds(2));
        assertEquals("valid!", validRes);

        // Invalid
        assertThrows(IllegalArgumentException.class, () ->
                bus.dispatch(new ValidatedCommand("")).await().atMost(Duration.ofSeconds(2))
        );
    }

    @Test
    void shouldHandleErrorsViaErrorHandlingMiddleware() {
        var bus = DefaultCommandBus.builder()
                .register(PingCommand.class, cmd -> Uni.createFrom().failure(new BusinessRuleViolation("Rule violated")))
                .addMiddleware(new ErrorHandlingCommandMiddleware(err -> new IllegalStateException("Mapped: " + err.getMessage())))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bus.dispatch(new PingCommand("fail")).await().atMost(Duration.ofSeconds(2))
        );
        assertEquals("Mapped: Rule violated", ex.getMessage());
    }
}
