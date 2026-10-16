/**
 * Domain events raised by the product aggregates.
 *
 * Event types are stable, machine-readable and outbox-friendly
 * ({@code product.*}). Downstream capabilities (POS, ecommerce,
 * search, inventory, accounting) react to these instead of Product
 * calling them directly.
 */
package tech.kayys.syirkah.product.domain.event;