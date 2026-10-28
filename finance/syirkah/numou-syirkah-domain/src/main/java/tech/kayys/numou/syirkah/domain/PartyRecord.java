package tech.kayys.numou.numoudomain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What the written akad records about one party (Bab XVIII, section D.1 and D.2): identity data that lets the
 * capacity (ahliyyah) be assessed, the assessment itself, and the party's own ijab and qabul wording.
 *
 * @param soundMind               the party is of sound mind (berakal)
 * @param authorityOverCapital    the party has authority over the property they contribute
 * @param freeConsent             the party consents without coercion (ikrah)
 * @param ijabQabulStatement      the party's written ijab and qabul wording
 */
public record PartyRecord(PartnerId partner, LocalDate dateOfBirth, String residence, boolean soundMind,
                          boolean authorityOverCapital, boolean freeConsent, String ijabQabulStatement)
        implements Serializable {
    public PartyRecord {
        Objects.requireNonNull(partner, "partner");
    }
}
