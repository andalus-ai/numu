package tech.kayys.syirkah.asset.domain.inspection;

/**
 * Overall result of an inspection as a whole (see ASSET-20 section 20.6).
 *
 * <p>Answers "did this inspection pass?", as opposed to
 * {@link AssetCondition} which answers "what condition was observed?".</p>
 */
public enum InspectionResult {
    PASS,
    PASS_WITH_FINDINGS,
    FAIL
}
