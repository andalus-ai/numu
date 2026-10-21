package tech.kayys.numou.numoudomain;

/** What happens to distributable profit at a close. */
public enum ProfitDisposition {
    /** Profit becomes payable to partners. */
    DISTRIBUTE,
    /** Profit is retained and becomes additional capital of each partner. */
    RETAIN
}
