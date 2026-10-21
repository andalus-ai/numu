package io.surveyhub.webhook;

import io.surveyhub.application.port.out.*;
import io.surveyhub.application.port.out.WebhookDeliveryLog.Delivery;
import io.surveyhub.application.webhook.WebhookPayloads;
import io.surveyhub.domain.event.DomainEvent;
import io.surveyhub.domain.webhook.WebhookEvents;
import io.surveyhub.domain.webhook.WebhookSubscription;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delivers domain events to matching webhooks off the request thread: publishing returns immediately, each delivery runs on a
 * small pool with exponential backoff retries (network errors, 408, 429, 5xx; never other 4xx). Every attempt is logged.
 * This is at-least-once: receivers must de-duplicate on the delivery id. For guaranteed delivery across restarts, put an
 * outbox table in front of this class instead of delivering directly.
 */
public final class WebhookEventPublisher implements EventPublisher, AutoCloseable {
    private final WebhookRepository repo;
    private final WebhookSender sender;
    private final WebhookDeliveryLog log;
    private final IdGenerator ids;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final ExecutorService pool;
    private final AtomicInteger pending = new AtomicInteger();

    public WebhookEventPublisher(WebhookRepository repo, WebhookSender sender, WebhookDeliveryLog log, IdGenerator ids,
                                 Clock clock, int maxAttempts, Duration baseBackoff, int threads) {
        this.repo = repo;
        this.sender = sender;
        this.log = log;
        this.ids = ids;
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = baseBackoff;
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads), r -> {
            Thread t = new Thread(r, "webhook-delivery");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            String type = WebhookEvents.typeOf(event);
            for (WebhookSubscription sub : repo.findActive(event.tenantId(), type)) {
                String deliveryId = ids.token();
                String payload = WebhookPayloads.of(event, deliveryId);
                pending.incrementAndGet();
                pool.execute(() -> {
                    try { deliver(sub, type, deliveryId, payload); } finally { pending.decrementAndGet(); }
                });
            }
        }
    }

    private void deliver(WebhookSubscription sub, String type, String deliveryId, String payload) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WebhookSender.Result r = sender.send(sub, type, deliveryId, payload);
            log.record(new Delivery(deliveryId, sub.tenantId(), sub.id(), type, attempt, clock.instant(), r.success(), r.status(), r.error(), r.millis()));
            if (r.success() || !r.retryable() || attempt == maxAttempts) return;
            try {
                Thread.sleep(baseBackoff.toMillis() * (1L << (attempt - 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** For tests and graceful shutdown: waits until all queued deliveries finished. */
    public boolean awaitIdle(Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() > end) return false;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }

    @Override
    public void close() {
        pool.shutdown();
        try { pool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
