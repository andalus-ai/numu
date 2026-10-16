package tech.kayys.syirkah.ecosystem.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Coarse grouping of capabilities, aligned with the phased capability
 * layers in {@code Docs/Plan/base01.md} (P2..P11).
 */
public enum CapabilityCategory implements ValueObject {

    /** P2 - product, catalog, pricing, order, fulfilment, POS, kiosk. */
    COMMERCE,

    /** P3 - stock, warehouse, reservation, receiving, movement. */
    INVENTORY,

    /** P4 - supplier, sourcing, replenishment, procurement. */
    SUPPLY,

    /** P5 - shipment, transportation, fleet, dispatch, route, POD, 3PL. */
    LOGISTICS,

    /** P6 - accounting, invoicing, receivables, payables, payment, tax. */
    FINANCE,

    /** P7 - people, employee, worker, driver, attendance, payroll input. */
    WORKFORCE,

    /** P8 - BOM, work order, production, quality. */
    MANUFACTURING,

    /** P9 - asset, equipment, maintenance, facilities. */
    ASSET,

    /** P10 - project, service management, field operations. */
    PROJECT,

    /** P11 - document, workflow, approval, signature, notification. */
    PROCESS,

    /** Platform-level: identity, tenant, event, integration, security. */
    PLATFORM
}
