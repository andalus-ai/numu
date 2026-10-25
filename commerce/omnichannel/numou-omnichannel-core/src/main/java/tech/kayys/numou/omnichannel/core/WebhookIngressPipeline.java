package tech.kayys.numou.omnichannel.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Enforces the shared ingress ordering for transport adapters:
 * validate parsed headers, validate a declared length, read a bounded body,
 * verify framing, construct a request, then invoke ingestion.
 *
 * This is transport-neutral; HTTP servers and proxies must still enforce
 * limits before they parse or buffer network headers.
 */
public final class WebhookIngressPipeline {
    private final WebhookIngressLimits limits;

    public WebhookIngressPipeline() {
        this(new WebhookIngressLimits());
    }

    public WebhookIngressPipeline(WebhookIngressLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    /**
     * Processes a webhook stream only after ingress limits pass.
     *
     * @param headers parsed request headers; duplicate case variants are rejected
     * @param input request body stream; ownership remains with the caller
     * @param declaredContentLength advertised length, or -1 when unknown
     * @param requestFactory builds the transport-neutral request from validated
     *                       headers and the exact bounded body bytes
     * @param ingestion typically {@code InboundWebhookIngestionService::ingest}
     * @throws IOException when reading fails, framing is inconsistent, or the body exceeds the configured limit
     * @throws IllegalArgumentException when the factory changes headers or body
     */
    public InboundIngestionResult ingest(
            Map<String, String> headers,
            InputStream input,
            long declaredContentLength,
            BiFunction<Map<String, String>, byte[], InboundWebhookRequest> requestFactory,
            Function<InboundWebhookRequest, InboundIngestionResult> ingestion) throws IOException {
        Map<String, String> validatedHeaders = WebhookHeaders.validatedCopy(headers);
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(requestFactory, "requestFactory must not be null");
        Objects.requireNonNull(ingestion, "ingestion must not be null");
        limits.validateDeclaredContentLength(declaredContentLength);

        byte[] body = limits.readBounded(input);
        if (declaredContentLength >= 0 && declaredContentLength != body.length) {
            throw new ContentLengthMismatchException(declaredContentLength, body.length);
        }

        InboundWebhookRequest request = Objects.requireNonNull(
                requestFactory.apply(validatedHeaders, body.clone()),
                "requestFactory result must not be null");
        if (!request.headers().equals(validatedHeaders)) {
            throw new IllegalArgumentException("request factory must preserve validated headers");
        }
        if (!Arrays.equals(request.rawBody(), body)) {
            throw new IllegalArgumentException("request factory must preserve the bounded request body");
        }
        return Objects.requireNonNull(ingestion.apply(request), "ingestion result must not be null");
    }

    /** Signals a mismatch between transport-declared and actually-read body length. */
    public static final class ContentLengthMismatchException extends IOException {
        private final long declaredLength;
        private final long actualLength;

        public ContentLengthMismatchException(long declaredLength, long actualLength) {
            super("webhook content length does not match bytes received");
            if (declaredLength < 0 || actualLength < 0) {
                throw new IllegalArgumentException("lengths must be non-negative");
            }
            this.declaredLength = declaredLength;
            this.actualLength = actualLength;
        }

        public long declaredLength() {
            return declaredLength;
        }

        public long actualLength() {
            return actualLength;
        }
    }
}
