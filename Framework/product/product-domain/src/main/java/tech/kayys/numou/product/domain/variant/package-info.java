/**
 * Product variants: independently identifiable versions of a
 * product (e.g. Coffee Small/Medium/Large, T-Shirt Red/M).
 *
 * The variant references its product by {@link ProductId} only - it
 * is its own consistency boundary, never embedded in Product.
 */
package tech.kayys.syirkah.product.domain.variant;