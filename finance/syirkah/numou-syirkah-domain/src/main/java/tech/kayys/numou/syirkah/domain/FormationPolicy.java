package tech.kayys.numou.numoudomain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Choices the book leaves to the adviser or the deployment.
 *
 * @param witnessRule     how witnesses are counted
 * @param blockMultiAkad  when true, an akad that combines other akad in one contract is batil (the book's
 *                        preferred view in Bab X and XI; the matter is khilafiyah, roadmap open decision 8)
 * @param minimumAgeYears optional minimum age at signing; the book requires mumayyiz and does not give a number,
 *                        so there is no default; null skips the check
 */
public record FormationPolicy(WitnessRule witnessRule, boolean blockMultiAkad, Integer minimumAgeYears)
        implements Serializable {

    public FormationPolicy {
        Objects.requireNonNull(witnessRule, "witnessRule");
        if (minimumAgeYears != null && minimumAgeYears < 0) {
            throw new IllegalArgumentException("minimumAgeYears must not be negative");
        }
    }

    /** The book's own positions: nishab witnesses, multi-akad blocked, no invented age limit. */
    public static FormationPolicy bookDefault() {
        return new FormationPolicy(WitnessRule.NISHAB, true, null);
    }
}
