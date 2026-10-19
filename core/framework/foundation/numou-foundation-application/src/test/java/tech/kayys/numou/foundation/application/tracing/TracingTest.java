package tech.kayys.numou.foundation.application.tracing;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.DefaultCommandBus;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.application.query.DefaultQueryBus;
import tech.kayys.numou.foundation.application.query.Query;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TracingTest {

    record RunJobCommand(String jobName) implements Command {}
    record CheckJobQuery(String jobId) implements Query {}

    static class MockTracing implements Tracing {
        final List<String> events = new ArrayList<>();

        @Override
        public TraceSpan startSpan(String name, SpanKind kind, ExecutionContext context) {
            events.add("start:" + name + ":" + kind);
            return new TraceSpan() {
                @Override
                public void success() {
                    events.add("success:" + name);
                }

                @Override
                public void failure(Throwable error) {
                    events.add("failure:" + name + ":" + error.getMessage());
                }

                @Override
                public void attribute(String key, String value) {
                    events.add("attr:" + key + "=" + value);
                }

                @Override
                public void close() {
                    events.add("close:" + name);
                }
            };
        }
    }

    @Test
    void shouldTraceSuccessfulCommand() {
        var tracing = new MockTracing();
        var bus = DefaultCommandBus.builder()
                .register(RunJobCommand.class, cmd -> Uni.createFrom().item("done"))
                .addMiddleware(new CommandTracingMiddleware(tracing))
                .build();

        bus.dispatch(new RunJobCommand("backup")).await().atMost(Duration.ofSeconds(2));

        assertEquals(
                List.of(
                        "start:command.RunJobCommand:INTERNAL",
                        "success:command.RunJobCommand",
                        "close:command.RunJobCommand"
                ),
                tracing.events
        );
    }

    @Test
    void shouldTraceFailedCommand() {
        var tracing = new MockTracing();
        var bus = DefaultCommandBus.builder()
                .register(RunJobCommand.class, cmd -> Uni.createFrom().failure(new IllegalStateException("boom")))
                .addMiddleware(new CommandTracingMiddleware(tracing))
                .build();

        assertThrows(IllegalStateException.class, () ->
                bus.dispatch(new RunJobCommand("failing")).await().atMost(Duration.ofSeconds(2))
        );

        assertEquals(
                List.of(
                        "start:command.RunJobCommand:INTERNAL",
                        "failure:command.RunJobCommand:boom",
                        "close:command.RunJobCommand"
                ),
                tracing.events
        );
    }

    @Test
    void shouldTraceSuccessfulQuery() {
        var tracing = new MockTracing();
        var bus = DefaultQueryBus.builder()
                .register(CheckJobQuery.class, q -> Uni.createFrom().item("RUNNING"))
                .addMiddleware(new QueryTracingMiddleware(tracing))
                .build();

        bus.dispatch(new CheckJobQuery("123")).await().atMost(Duration.ofSeconds(2));

        assertEquals(
                List.of(
                        "start:query.CheckJobQuery:INTERNAL",
                        "success:query.CheckJobQuery",
                        "close:query.CheckJobQuery"
                ),
                tracing.events
        );
    }

    @Test
    void shouldSafelyHandleNoopTracing() {
        var noop = Tracing.noop();
        var span = noop.startSpan("test", SpanKind.CLIENT, ExecutionContext.empty());
        assertDoesNotThrow(() -> {
            span.attribute("k", "v");
            span.success();
            span.close();
        });
    }
}
