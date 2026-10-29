# POS Platform M1: Sale lifecycle and item snapshots

**Status:** Initial domain foundation proposed for review  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Included

- A neutral sale aggregate with tenant, branch, register, shift, channel, and explicit currency.
- A provider-neutral channel enum suitable for POS, web, mobile, marketplace, social commerce, B2B, kiosk, call center, and partner API.
- Immutable sale-line snapshots preserving an external item reference, display name, optional SKU, unit of measure, quantity, and unit-price snapshot.
- Open, completed, and voided lifecycle transitions with basic time, currency, line, and state invariants.
- Subtotal calculation using decimal arithmetic without hidden rounding; currency is established when the sale opens, including before its first line.

## Ownership boundaries

Catalog and pricing remain the source of product and price truth. A sale line is a transaction snapshot, not a second catalog aggregate. Tax and discount policy are not embedded here. Inventory movements, payment authorization/capture/refunds, accounting postings, receipt issuance, and reconciliation must be orchestrated through their owning capability contracts by application services. A completed sale must not imply those side effects have succeeded.

## Deliberate limitations

- The aggregate does not yet implement persistence, optimistic concurrency, command idempotency, audit events, or application ports.
- Returns and exchanges require a separate explicit model; completed sales cannot be voided as a shortcut.
- No Maven tests or CI are claimed as passing in this change.
