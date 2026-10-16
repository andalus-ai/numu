package tech.kayys.syirkah.product.domain.specification;

/**
 * The value domain of an attribute definition.
 *
 * This is what makes the specification system extensible across
 * industries: fashion uses ENUM color/size, laptops use NUMBER ram,
 * SaaS uses NUMBER users - all without domain changes.
 */
public enum AttributeType {

    TEXT,

    NUMBER,

    BOOLEAN,

    DATE,

    ENUM,

    MONEY,

    MEASUREMENT,

    REFERENCE
}