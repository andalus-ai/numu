package tech.kayys.syirkah.restaurant.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.restaurant.kernel.spi.DomainEventSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process event bus for the monolith/dev profile and for tests: records every event and synchronously routes it
 * to subscribed policies. In production the sink is the transactional outbox and policies run as Kafka consumers
 * behind the inbox (at-least-once, deduplicated); the policies themselves are the same code and are idempotent.
 *
 * <p>A failing policy never breaks the publisher (the publisher's transaction already decided the business
 * outcome); failures are kept in {@link #failures()} so tests and dev logs surface them.
 */
public final class DispatchingEventSink implements DomainEventSink, tech.kayys.syirkah.reservation.spi.DomainEventSink {

    @FunctionalInterface
    public interface Policy<E extends DomainEvent> {
        Uni<Void> handle(UUID tenantId, E event);
    }

    public record Published(UUID tenantId, DomainEvent event) { }

    private final Map<Class<?>, List<Policy<DomainEvent>>> policies = new ConcurrentHashMap<>();
    private final List<Published> published = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final boolean record;
    private final boolean propagateFailures;

    /** Dev/test mode: records everything, swallows policy failures into {@link #failures()}. */
    public DispatchingEventSink() {
        this(true, false);
    }

    /**
     * @param record            keep every event in memory (tests only - unbounded growth otherwise)
     * @param propagateFailures fail the publish when a policy fails (production consumers: lets the inbox leave the
     *                          event unhandled so the broker redelivers it)
     */
    public DispatchingEventSink(boolean record, boolean propagateFailures) {
        this.record = record;
        this.propagateFailures = propagateFailures;
    }

    @SuppressWarnings("unchecked")
    public <E extends DomainEvent> DispatchingEventSink on(Class<E> type, Policy<E> policy) {
        policies.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add((Policy<DomainEvent>) (Policy<?>) policy);
        return this;
    }

    @Override
    public Uni<Void> publish(UUID tenantId, List<DomainEvent> events) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (final var e : new ArrayList<>(events)) {
            if (record) {
                published.add(new Published(tenantId, e));
            }
            for (final var p : policies.getOrDefault(e.getClass(), List.of())) {
                chain = chain.flatMap(x -> Uni.createFrom().<Void>deferred(() -> p.handle(tenantId, e))
                        .onFailure().recoverWithUni(t -> {
                            if (propagateFailures) {
                                return Uni.<Void>createFrom().failure(t);
                            }
                            failures.add(t);
                            return Uni.createFrom().voidItem();
                        }));
            }
        }
        return chain;
    }

    public List<Published> all() {
        return List.copyOf(published);
    }

    public List<String> types() {
        return published.stream().map(p -> p.event().eventType()).toList();
    }

    public List<Throwable> failures() {
        return List.copyOf(failures);
    }
}
