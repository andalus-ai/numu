package tech.kayys.numou.support.application.email;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record InboundEmail(
        String messageId,
        EmailAddress from,
        List<EmailAddress> to,
        List<EmailAddress> cc,
        String subject,
        String textBody,
        Instant receivedAt,
        Map<String, String> headers
) {
    public InboundEmail {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId cannot be blank");
        }
        if (from == null) {
            throw new IllegalArgumentException("from cannot be null");
        }
        to = to == null ? List.of() : List.copyOf(to);
        if (to.isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        cc = cc == null ? List.of() : List.copyOf(cc);
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be blank");
        }
        if (textBody == null || textBody.isBlank()) {
            throw new IllegalArgumentException("textBody cannot be blank");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt cannot be null");
        }
        headers = headers == null ? Map.of() : headers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }

    public boolean isSupportOutboundMessage() {
        return "true".equalsIgnoreCase(headers.get("x-numou-support"));
    }
}
