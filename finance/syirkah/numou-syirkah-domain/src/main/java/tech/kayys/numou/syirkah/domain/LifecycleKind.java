package tech.kayys.numou.numoudomain;

/** The lifecycle changes of a running syirkah that Hukum Muamalah says need a closed book and a new akad. */
public enum LifecycleKind {
    /** New capital enters (Bab XXIV). */
    NEW_CAPITAL,
    /** A partner is replaced, i.e. "shares" are sold (Bab XXV), with hak syuf'ah. */
    PARTNER_REPLACEMENT,
    /** A partner dies; the akad is fasakh and may only continue as a new akad (Bab X). */
    PARTNER_DEATH
}
