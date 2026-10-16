package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.context.ActorId;
import tech.kayys.numou.foundation.application.context.CorrelationId;
import tech.kayys.numou.foundation.application.context.CurrentExecutionContext;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.application.middleware.ContextMiddleware;
import tech.kayys.numou.foundation.application.middleware.ErrorHandlingQueryMiddleware;
import tech.kayys.numou.foundation.application.middleware.ValidationMiddleware;
import tech.kayys.numou.foundation.application.validation.Validatable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class QueryBusTest {

    record FindItemQuery(String itemId) implements Query {}
    record ValidatedQuery(String queryText) implements Query, Validatable {
        @Override
        public void validate() {
            if (queryText == null || queryText.isBlank()) {
                throw new IllegalArgumentException("Query text cannot be blank");
            }
        }
    }

    @Test
    void shouldDispatchQueryToRegisteredHandler() {
        var bus = DefaultQueryBus.builder()
                .register(FindItemQuery.class, q -> Uni.createFrom().item("Item: " + q.itemId()))
                .build();

        String result = bus.<FindItemQuery, String>dispatch(new FindItemQuery("item-42"))
                .await().atMost(Duration.ofSeconds(2));

        assertEquals("Item: item-42", result);
    }

    @Test
    void shouldExecuteQueryMiddlewaresInPipeline() {
        List<String> steps = new ArrayList<>();

        QueryMiddleware middlewareA = new QueryMiddleware() {
            @Override
            public <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next) {
                steps.add("pre-A");
                return next.proceed().invoke(res -> steps.add("post-A"));
            }
        };

        QueryMiddleware middlewareB = new QueryMiddleware() {
            @Override
            public <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next) {
                steps.add("pre-B");
                return next.proceed().invoke(res -> steps.add("post-B"));
            }
        };

        var bus = DefaultQueryBus.builder()
                .register(FindItemQuery.class, q -> {
                    steps.add("handler");
                    return Uni.createFrom().item("found");
                })
                .addMiddleware(middlewareA)
                .addMiddleware(middlewareB)
                .build();

        bus.dispatch(new FindItemQuery("item-1")).await().atMost(Duration.ofSeconds(2));

        assertEquals(List.of("pre-A", "pre-B", "handler", "post-B", "post-A"), steps);
    }

    @Test
    void shouldPropagateExecutionContextViaContextMiddleware() {
        AtomicBoolean contextVerified = new AtomicBoolean(false);

        var bus = DefaultQueryBus.builder()
                .register(FindItemQuery.class, q -> {
                    var current = CurrentExecutionContext.getOrEmpty();
                    if ("user-999".equals(current.actorId().value())) {
                        contextVerified.set(true);
                    }
                    return Uni.createFrom().item("result");
                })
                .addMiddleware(new ContextMiddleware())
                .build();

        var execContext = ExecutionContext.builder()
                .actorId(ActorId.of("user-999"))
                .correlationId(CorrelationId.generate())
                .build();

        var qContext = QueryContext.of(execContext, new FindItemQuery("test"));
        bus.dispatch(new FindItemQuery("test"), qContext).await().atMost(Duration.ofSeconds(2));

        assertTrue(contextVerified.get());
        assertTrue(CurrentExecutionContext.get().isEmpty());
    }

    @Test
    void shouldValidateQueryViaValidationMiddleware() {
        var bus = DefaultQueryBus.builder()
                .register(ValidatedQuery.class, q -> Uni.createFrom().item("ok"))
                .addMiddleware(new ValidationMiddleware())
                .build();

        // Valid
        String validRes = bus.<ValidatedQuery, String>dispatch(new ValidatedQuery("search"))
                .await().atMost(Duration.ofSeconds(2));
        assertEquals("ok", validRes);

        // Invalid
        assertThrows(IllegalArgumentException.class, () ->
                bus.dispatch(new ValidatedQuery("")).await().atMost(Duration.ofSeconds(2))
        );
    }

    @Test
    void shouldHandleErrorsViaErrorHandlingMiddleware() {
        var bus = DefaultQueryBus.builder()
                .register(FindItemQuery.class, q -> Uni.createFrom().failure(new RuntimeException("Database timeout")))
                .addMiddleware(new ErrorHandlingQueryMiddleware(err -> new IllegalStateException("Query failed: " + err.getMessage())))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bus.dispatch(new FindItemQuery("item-error")).await().atMost(Duration.ofSeconds(2))
        );
        assertEquals("Query failed: Database timeout", ex.getMessage());
    }
}
