package tech.kayys.syirkah.commerce.pricing.domain;

/**
 * Pure-domain price resolution port.
 *
 * Mirrors the blueprint's {@code PricingEngine.calculate(context)}.
 * Implementations (flat list lookup, tiered, option adjustments) live
 * behind this interface so pricing rules stay swappable per offering —
 * e.g. OAT_MILK = +7,000 at Coffee Shop A but +5,000 at Coffee Shop B.
 */
public interface PricingEngine {

    PriceResult calculate(PricingContext context);
}
