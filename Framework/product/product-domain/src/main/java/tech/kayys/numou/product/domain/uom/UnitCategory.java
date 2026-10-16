package tech.kayys.syirkah.product.domain.uom;

/**
 * The physical/business dimension a unit belongs to.
 *
 * <pre>
 * PCS, BOX, CARTON   -&gt; COUNT
 * GRAM, KG           -&gt; WEIGHT
 * ML, LITER          -&gt; VOLUME
 * HOUR               -&gt; TIME
 * USER, LICENSE      -&gt; DIGITAL
 * </pre>
 */
public enum UnitCategory {

    COUNT,

    WEIGHT,

    VOLUME,

    LENGTH,

    AREA,

    TIME,

    DIGITAL
}