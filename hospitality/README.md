# Hospitality capability

This reactor starts the accommodation-specific domain extension for staycations, hotels, villas, resorts, guesthouses, apartments, and homestays.

## Current domain slice

- Provider/tenant references and a guarded property-listing lifecycle.
- Property-local time zone and check-in/check-out rules.
- Accommodation unit capacity and inventory descriptors.
- Immutable stay quote snapshots with exclusive checkout dates, per-night pricing, fees, taxes, cancellation-policy version, and expiry.

## Explicit boundaries

- Listing identifiers and provider/tenant references are not authorization. Application use cases must enforce object-level authorization for every operation.
- AccommodationUnit.inventoryCount is descriptive capacity only. It does not reserve inventory or prevent overselling.
- The generic Reservation capability remains the owner of atomic holds, conflict prevention, and reservation lifecycle. Integrate through its public contract in a subsequent application/SPI slice.
- Existing Payment and Finance capabilities remain owners of provider payment integrations and financial ledger/settlement behavior. No payment data or new ledger is introduced here.
- Quote tax and fee calculation is intentionally delegated to explicit application policies; the domain validates and freezes the resulting amounts rather than guessing jurisdictional rules.
- This module is deployment-neutral and has no HTTP, CDI, persistence, or vendor dependencies.

## Build

From the repository root, use the module reactor explicitly:

    mvn -f hospitality/pom.xml test

This command has not yet been run in the current implementation environment.
