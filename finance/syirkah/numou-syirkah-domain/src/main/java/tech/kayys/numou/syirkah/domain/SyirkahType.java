package tech.kayys.numou.numoudomain;

/**
 * The five classical forms of syirkah. Each carries the fiqh traits the domain
 * needs to validate a contract and to choose a loss rule.
 */
public enum SyirkahType {
    /**
     * Syirkah 'inan: capital and (optionally) work from each partner; loss by
     * capital.
     */
    INAN(true, false),
    /** Syirkah 'abdan: work/skill only; no monetary capital. */
    ABDAN(false, false),
    /**
     * Mudharabah: one side supplies capital, the other work; loss on capital
     * provider.
     */
    MUDHARABAH(true, true),
    /** Syirkah wujuh: partners trade on reputation/credit, no cash capital. */
    WUJUH(false, false),
    /**
     * Syirkah mufawadhah: general partnership combining the other forms (equal
     * terms).
     */
    MUFAWADHAH(true, false);

    private final boolean requiresCashCapital;
    private final boolean separatesCapitalAndWork;

    SyirkahType(boolean requiresCashCapital, boolean separatesCapitalAndWork) {
        this.requiresCashCapital = requiresCashCapital;
        this.separatesCapitalAndWork = separatesCapitalAndWork;
    }

    public boolean requiresCashCapital() {
        return requiresCashCapital;
    }

    /**
     * True for mudharabah: the capital provider and the manager are different
     * roles.
     */
    public boolean separatesCapitalAndWork() {
        return separatesCapitalAndWork;
    }
}
