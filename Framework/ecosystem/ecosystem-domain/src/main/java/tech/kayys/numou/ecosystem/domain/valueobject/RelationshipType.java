package tech.kayys.syirkah.ecosystem.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Direction and meaning of a participant relationship.
 *
 * <p>Relationships are directed: {@code A CUSTOMER_OF B} is not the same
 * fact as {@code B SUPPLIER_OF A}, and both may exist independently with
 * their own validity periods.
 */
public enum RelationshipType implements ValueObject {

    /** Source buys goods/services from target. */
    CUSTOMER_OF,

    /** Source sells goods/services to target. */
    SUPPLIER_OF,

    /** Source supplies a capability to target. */
    PROVIDER_OF,

    /** Peer collaboration without a buy/sell direction. */
    PARTNER_OF,

    /** Source belongs to a group operated by target (holding/branch). */
    MEMBER_OF,

    /** Source transports on behalf of target. */
    CARRIER_FOR
}
