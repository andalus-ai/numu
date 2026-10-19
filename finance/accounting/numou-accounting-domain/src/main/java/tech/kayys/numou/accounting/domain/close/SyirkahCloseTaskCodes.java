package tech.kayys.numou.accounting.domain.close;

/** Stable task codes used to gate Syirkah period-close operations. */
public final class SyirkahCloseTaskCodes {
    public static final String RESULT = "TASK_SYIRKAH_RESULT";
    public static final String ALLOCATION = "TASK_SYIRKAH_ALLOCATION";
    public static final String DISTRIBUTION = "TASK_SYIRKAH_DISTRIBUTION";

    private SyirkahCloseTaskCodes() {
    }
}
