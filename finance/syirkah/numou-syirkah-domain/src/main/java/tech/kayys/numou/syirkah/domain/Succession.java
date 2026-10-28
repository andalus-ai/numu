package tech.kayys.numou.numoudomain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What an heir decided when a partner died: take over the deceased's place in the syirkah, or not. A partner's
 * death ends the akad (Bab X); the heir may continue it only through a new akad, after the books are closed.
 *
 * @param deceased   the partner who died
 * @param heir       the heir, who becomes a partner in the new akad when {@code continues}
 * @param continues  true = the heir takes the deceased's place; false = the heir takes the share out
 * @param decidedOn  date of the decision
 * @param evidence   reference to the signed decision and to the proof of heirship
 */
public record Succession(PartnerId deceased, PartnerId heir, boolean continues, LocalDate decidedOn,
                         String evidence) {
    public Succession {
        Objects.requireNonNull(deceased, "deceased");
        Objects.requireNonNull(heir, "heir");
        Objects.requireNonNull(decidedOn, "decidedOn");
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("succession evidence is required");
        }
    }
}
