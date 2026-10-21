package io.surveyhub.webhook;

import io.surveyhub.application.port.out.WebhookSender;
import io.surveyhub.application.port.out.WebhookUrlPolicy;
import io.surveyhub.domain.DomainException;
import io.surveyhub.domain.webhook.WebhookSubscription;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * One signed POST over java.net.http. Redirects are never followed (a 3xx could point at an internal address), the response
 * body is discarded, and the URL policy is re-checked immediately before connecting.
 */
public final class HttpWebhookSender implements WebhookSender {
    private final HttpClient client;
    private final WebhookUrlPolicy policy;
    private final Clock clock;
    private final Duration timeout;

    public HttpWebhookSender(WebhookUrlPolicy policy, Clock clock, Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(connectTimeout).build();
        this.policy = policy;
        this.clock = clock;
        this.timeout = requestTimeout;
    }

    @Override
    public Result send(WebhookSubscription sub, String eventType, String deliveryId, String payloadJson) {
        long started = System.nanoTime();
        try {
            policy.check(sub.url());
        } catch (DomainException e) {
            return new Result(false, -1, "blocked by URL policy: " + e.getMessage(), 0);
        }
        try {
            long ts = clock.instant().getEpochSecond();
            HttpRequest request = HttpRequest.newBuilder(URI.create(sub.url())).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SurveyHub-Webhooks/1")
                    .header("X-Survey-Event", eventType)
                    .header("X-Survey-Delivery", deliveryId)
                    .header(WebhookSignature.HEADER, WebhookSignature.sign(sub.secret(), ts, payloadJson))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 300;
            return new Result(ok, status, ok ? "" : "HTTP " + status, millis(started));
        } catch (HttpTimeoutException e) {
            return new Result(false, 0, "timeout", millis(started));
        } catch (java.io.IOException | IllegalArgumentException e) {
            return new Result(false, 0, e.getClass().getSimpleName(), millis(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, 0, "interrupted", millis(started));
        }
    }

    private static long millis(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }
}
