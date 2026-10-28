package tech.kayys.numou.numoudomain;

/**
 * How a formation finding affects the akad, following Hukum Muamalah Bab X (section C, "akad tidak sah") and
 * Bab XVIII (section D, "penyimpangan dalam akad").
 *
 * <p>This is an implementation trace of the book, not a ruling. Findings the book does not classify are
 * {@link #UNCLASSIFIED}; they block activation like the others and wait for a qualified Sharia adviser.
 */
public enum AkadDefect {
    /**
     * Batil: a defect in a rukun or in one of the seven in'iqad conditions. The akad is treated as never having
     * happened and cannot be repaired by amendment; it must be made again.
     */
    BATIL,
    /**
     * Fasad: a defect in an attribute outside the rukun (unclear terms, gharar, coercion). The akad is not valid
     * as it stands but the parties may remove the defect or cancel it.
     */
    FASAD,
    /**
     * The written akad is incomplete against Bab XVIII (missing party data, ijab and qabul wording, sections,
     * witnesses). The book lists these as deviations to avoid; whether they make the akad batil is not stated, so
     * they are not classified as batil here.
     */
    INCOMPLETE,
    /** The book does not classify this violation here; it awaits the Sharia adviser. */
    UNCLASSIFIED
}
