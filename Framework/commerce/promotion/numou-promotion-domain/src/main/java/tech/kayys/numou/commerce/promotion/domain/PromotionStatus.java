package tech.kayys.syirkah.commerce.promotion.domain;

/**
 * Lifecycle of a promotion (product05.md §3).
 *
 * <p>Draft promotions are being authored (or staged for approval) and must
 * never influence a live cart. Scheduled promotions are published but not
 * yet effective. Active promotions are published and within their validity
 * window. Paused promotions are published but temporarily suspended.
 * Expired promotions are published but past their validity window.
 * Archived promotions are retired and read-only.</p>
 */
public enum PromotionStatus {
    DRAFT,
    SCHEDULED,
    ACTIVE,
    INACTIVE,
    PAUSED,
    EXPIRED,
    ARCHIVED
}
