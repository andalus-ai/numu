package tech.kayys.numou.numoudomain;

/** The sections a written akad must contain, per Hukum Muamalah Bab XVIII, section C.4 (items a to j). */
public enum AkadSection {
    /** a. Data on the contracting parties (al muta'aqidain). */
    PARTIES,
    /** b. Clauses on the object (ma'qud 'alaihi) and its counter-value (muqabil). */
    OBJECT_AND_COUNTERVALUE,
    /** c. Written ijab and qabul wording of the parties. */
    IJAB_QABUL,
    /** d. Rights and duties of the parties. */
    RIGHTS_AND_DUTIES,
    /** e. Ja'li conditions (conditions the parties set themselves); the section may state that there are none. */
    JALI_CONDITIONS,
    /** f. Sanctions for breach of duties. */
    SANCTIONS,
    /** g. Dispute settlement. */
    DISPUTE_RESOLUTION,
    /** h. Place and date of signing. */
    SIGNING_PLACE_AND_DATE,
    /** i. Signatures of the parties. */
    PARTY_SIGNATURES,
    /** j. Signatures of the witnesses. */
    WITNESS_SIGNATURES
}
