package tech.kayys.numou.courier.domain;

/** Operational role of a hub in a logistics network. */
public enum HubType {
    BRANCH_DEPOT,
    ORIGIN_HUB,
    TRANSIT_HUB,
    DESTINATION_DEPOT,
    SORTING_CENTER,
    CROSS_DOCK,
    FULFILLMENT_CENTER,
    LOCKER_SITE,
    PARTNER_FACILITY
}
