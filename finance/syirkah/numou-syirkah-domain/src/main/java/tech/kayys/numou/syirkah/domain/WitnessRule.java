package tech.kayys.numou.numoudomain;

/** How the witness requirement is applied. Which one a deployment uses is open decision 9 of the roadmap. */
public enum WitnessRule {
    /** At least {@link SyirkahContract#MIN_WITNESSES} named witnesses, whoever they are. */
    COUNT_ONLY,
    /**
     * The book's nishab (Bab XVIII): two men, or one man and two women, or four women.
     */
    NISHAB
}
