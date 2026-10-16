package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;
import java.util.Optional;

/** Controlled customer read model for promotion evaluation. */
public record CustomerSnapshot(
        CustomerId id,
        Optional<String> segment
) {

    public CustomerSnapshot {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(segment, "segment cannot be null");
    }

    public static CustomerSnapshot of(String id) {
        return new CustomerSnapshot(CustomerId.of(id), Optional.empty());
    }

    public static CustomerSnapshot of(String id, String segment) {
        return new CustomerSnapshot(
                CustomerId.of(id),
                Optional.ofNullable(segment).filter(s -> !s.isBlank()));
    }
}
