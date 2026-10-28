package tech.kayys.numou.numouapplication;

/**
 * Whether {@link SyirkahApplicationService#open} may open a syirkah without the akad formation checks.
 *
 * <p>{@link #OFF} is the default so existing callers keep working. A deployment that must enforce Hukum Muamalah
 * Bab X, XI and XVIII (roadmap gate B) sets {@link #REQUIRED}, after which only
 * {@link SyirkahApplicationService#openWithFormation} can open a syirkah.
 */
public enum FormationEnforcement {
    OFF, REQUIRED
}
