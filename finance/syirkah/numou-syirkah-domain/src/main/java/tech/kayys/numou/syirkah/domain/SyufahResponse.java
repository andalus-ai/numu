package tech.kayys.numou.numoudomain;

import java.time.LocalDate;
import java.util.Objects;

/** A remaining partner's answer to the first-refusal offer (hak syuf'ah) when a share is sold to an outsider. */
public record SyufahResponse(PartnerId partner, boolean exercised, LocalDate answeredOn) {
    public SyufahResponse {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(answeredOn, "answeredOn");
    }
}
