# Commerce Promotion

Implements product01 Phase F + product03.md execution model under
`framework/commerce/promotion`.

```text
EvaluationContext → Resolver → Evaluator → Stacking → Composer → PromotionResult
```

## Modules

| Module | Role |
| --- | --- |
| `numou-promotion-domain` | Promotion, multi-rule, context, stacking, benefits, compiler registries |
| `numou-promotion-spi` | `PromotionRepository`, `PromotionCandidatePort`, `CompiledPromotionProvider` |
| `numou-promotion-application` | Create / Activate / Suspend / AddRule / UpdateRule / Apply / Calculate |
| `numou-promotion-adapter` | In-memory repository + compiled cache |

## Runtime (product03)

* `PromotionEvaluationContext` + snapshots (`Customer` / `Cart` / `Line`)
* `PromotionResolver` / `PromotionEvaluator` / `DefaultPromotionEvaluator`
* Multi-rule `Promotion` (`rules`, `addRule`, `replaceRule`)
* Stacking split: `StackPromotionPolicy`, `ExclusivePromotionPolicy`,
  `BestResultPromotionPolicy` → `ConfigurablePromotionStackingPolicy`
* Benefit compatibility policy on STACK mode
* Compiler registries: condition / effect / target factories
  (`channel`, `minimum_quantity`, `percentage_discount`, `fixed_discount`, …)

`PromotionEngine.evaluate(candidates, context)` is the primary API;
`evaluate(candidates, cart, price)` and `apply(...)` remain as v1 adapters.

## Application commands

* `CreatePromotion` — draft definition
* `AddRule` / `UpdateRule` — draft multi-rule edits
* `ActivatePromotion` — compile → activate → cache
* `SuspendPromotion` — deactivate + invalidate cache
* `ApplyPromotions` / `CalculatePromotion` — cart evaluation queries

## P5 Promotion Resolver (product04.md)

```text
EvaluationContext
  → PromotionResolver           (candidate version ids)
  → PromotionRepository.loadAll  (bulk load definitions)
  → PromotionEngine.evaluate     (P6 eligibility)
```

* `PromotionResolveRequest` — narrow lookup request (tenant, effectiveAt,
  channel, optional branch/customer/segment, product/SKU/category sets,
  optional coupon). Deliberately excludes the cart.
* `PromotionCandidate` / `PromotionVersionId` — versioned identity so
  evaluation binds to an immutable version.
* `PromotionResolver` / `DefaultPromotionResolver` — delegates candidate
  discovery to `PromotionCandidateRepository`; performs no business
  eligibility.
* `PromotionScope` — explicit scope with "empty set = all" semantics.
* `PromotionCandidateRepository` — discovery boundary; PostgreSQL /
  Elasticsearch / Redis live behind it.
* `PromotionRepository.loadAll(List<PromotionVersionId>)` — bulk load the
  full aggregates after the resolver returns version ids.
* `PromotionCandidatePort` — async SPI with both legacy
  `findActiveOn(LocalDate)` and typed `findCandidates(PromotionResolveRequest)`.

## Deliberately deferred (product04+)

Full JSON tenant DSL persistence, coupons/quotas, campaign lifecycle,
channel-specific resolvers beyond the registry factories.
