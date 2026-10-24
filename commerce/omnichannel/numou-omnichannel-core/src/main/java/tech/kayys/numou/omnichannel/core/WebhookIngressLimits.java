package tech.kayys.numou.omnichannel.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Transport-adapter helper for enforcing the body limit before constructing
 * an InboundWebhookRequest. The adapter must call validateDeclaredContentLength
 * before reading when the transport exposes a content length, and must always
 * use readBounded because the declared length may be absent or untrustworthy.
 */
public final class WebhookIngressLimits {
    public static final int DEFAULT_MAX_BODY_BYTES = 1_048_576;
    private static final int READ_BUFFER_BYTES = 8_192;

    private final int maxBodyBytes;

    public WebhookIngressLimits() {
        this(DEFAULT_MAX_BODY_BYTES);
    }

    public WebhookIngressLimits(int maxBodyBytes) {
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    public int maxBodyBytes() {
        return maxBodyBytes;
    }

    /**
     * Rejects an advertised body size before reading. A value of -1 means the
     * transport did not provide a length; in that case readBounded remains
     * mandatory. Other negative values are invalid.
     */
    public void validateDeclaredContentLength(long contentLength) throws PayloadTooLargeException {
        if (contentLength < -1) {
            throw new IllegalArgumentException("contentLength must be -1 or non-negative");
        }
        if (contentLength > maxBodyBytes) {
            throw new PayloadTooLargeException(maxBodyBytes);
        }
    }

    /**
     * Reads no more than maxBodyBytes plus one detection byte. The extra byte
     * distinguishes an exactly-at-limit stream from an oversized stream
     * without buffering the complete oversized payload.
     *
     * The caller retains ownership of the stream; this method does not close it.
     */
    public byte[] readBounded(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input must not be null");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBodyBytes, READ_BUFFER_BYTES));
        byte[] buffer = new byte[Math.min(READ_BUFFER_BYTES, maxBodyBytes)];
        int total = 0;
        while (total <= maxBodyBytes) {
            int remainingIncludingProbe = maxBodyBytes - total + 1;
            int requested = Math.min(buffer.length, remainingIncludingProbe);
            int count = input.read(buffer, 0, requested);
            if (count < 0) {
                return output.toByteArray();
            }
            if (count == 0) {
                int single = input.read();
                if (single < 0) {
                    return output.toByteArray();
                }
                if (total == maxBodyBytes) {
                    throw new PayloadTooLargeException(maxBodyBytes);
                }
                output.write(single);
                total++;
                continue;
            }
            if (count > maxBodyBytes - total) {
                throw new PayloadTooLargeException(maxBodyBytes);
            }
            output.write(buffer, 0, count);
            total += count;
        }
        throw new PayloadTooLargeException(maxBodyBytes);
    }

    public static final class PayloadTooLargeException extends IOException {
        private final int maximumBytes;

        public PayloadTooLargeException(int maximumBytes) {
            super("webhook payload exceeds configured maximum of " + maximumBytes + " bytes");
            this.maximumBytes = maximumBytes;
        }

        public int maximumBytes() {
            return maximumBytes;
        }
    }
}
