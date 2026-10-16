package tech.kayys.syirkah.asset.domain.inspection;

/** Kind of inspection being performed (see ASSET-20 section 20.5). */
public enum InspectionType {
    ROUTINE,
    PRE_OPERATION,
    POST_OPERATION,
    SAFETY,
    CONDITION,
    COMPLIANCE,
    DAMAGE,
    HANDOVER,
    OTHER
}
