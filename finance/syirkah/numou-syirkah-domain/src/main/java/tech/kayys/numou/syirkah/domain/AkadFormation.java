package tech.kayys.numou.numoudomain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The facts about how an akad was formed and written, kept apart from {@link SyirkahContract} so that the
 * contract's canonical form (and every hash and stored JSON built on it) is unchanged.
 *
 * @param parties                one record per partner
 * @param businessActivity       what the syirkah will trade or do (the object, ma'qud 'alaihi)
 * @param lawfulnessReview       the recorded review that the activity is lawful; software cannot decide this
 * @param combinedAkad           names of other akad combined into this one (multi-akad); empty when none
 * @param unresolvedAmbiguities  terms the draft leaves unclear (majhul or multi-meaning wording); empty when none
 * @param sameSession            ijab and qabul took place in one session (kesatuan majelis)
 * @param ijabWithdrawn          the ijab was withdrawn before qabul
 * @param sectionsPresent        sections the written akad contains
 * @param signedPlace            place of signing
 * @param signedOn               date of signing
 * @param witnesses              witnesses who signed
 */
public record AkadFormation(List<PartyRecord> parties, String businessActivity, LawfulnessReview lawfulnessReview,
                            Set<String> combinedAkad, List<String> unresolvedAmbiguities, boolean sameSession,
                            boolean ijabWithdrawn, Set<AkadSection> sectionsPresent, String signedPlace,
                            LocalDate signedOn, List<Witness> witnesses) implements Serializable {

    public AkadFormation {
        parties = List.copyOf(parties == null ? List.of() : parties);
        combinedAkad = Set.copyOf(combinedAkad == null ? Set.of() : combinedAkad);
        unresolvedAmbiguities = List.copyOf(unresolvedAmbiguities == null ? List.of() : unresolvedAmbiguities);
        sectionsPresent = sectionsPresent == null || sectionsPresent.isEmpty()
                ? Set.of()
                : Set.copyOf(sectionsPresent);
        witnesses = List.copyOf(witnesses == null ? List.of() : witnesses);
    }

    /**
     * A recorded determination that the business activity is lawful.
     *
     * @param reviewedBy who reviewed it (a person or body, for example the Sharia adviser)
     * @param reviewedOn the date of the review
     * @param lawful     the outcome
     */
    public record LawfulnessReview(String reviewedBy, LocalDate reviewedOn, boolean lawful) implements Serializable {
    }
}
