package tech.kayys.syirkah.commerce.promotion.domain.capability;

/**
 * Declared value type of a capability parameter (product03.md §62).
 *
 * <p>Kept deliberately small — this is metadata for validation, docs and admin
 * UIs, not a full type system.</p>
 */
public enum PromotionValueType {

    STRING,

    NUMBER,

    BOOLEAN,

    MONEY,

    LIST
}