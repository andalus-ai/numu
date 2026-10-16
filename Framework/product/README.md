# Product Capability

Product is a **reusable foundation**: a generic commercial/business
offering model that Retail, POS, Ecommerce, Procurement,
Manufacturing, Service, Accounting and ERP consume — not an
"ecommerce product module"
(`Docs/Product/product00.md` … `product02.md`).

## Implemented modules (Product 1.0 + product02)

```text
product/
├── product-domain        Product, Variant, Specification, SKU,
│                         Classification + value objects + events
├── product-spi           persistence ports
├── product-application   use cases (commands + handlers)
├── product-adapter       in-memory adapters
├── product-catalog       separate Catalog context
└── product-runtime       (legacy Quarkus prototype, see below)
```

### product-domain (pure Java, no frameworks)

```text
tech.kayys.numou.product.domain
├── product/         Product, ProductId, ProductType, ProductStatus
├── identifier/      ProductIdentifier, IdentifierType
├── variant/         ProductVariant, ProductVariantId, VariantStatus, VariantAttribute
├── specification/   ProductSpecification, AttributeDefinition, AttributeType,
│                    OptionGroup, OptionDefinition
├── sku/             Sku, SkuId, SkuStatus, SkuIdentifier, SkuIdentifierType
├── classification/  ClassificationScheme, ClassificationNode, ProductClassification
├── uom/             UnitOfMeasure, UnitCategory, UnitConversion
├── packaging/       Packaging
├── bundle/          Bundle, BundleId, BundleStatus, BundleComponent
└── event/           product.* domain events (incl. classification, identifiers, bundle)
```

Seven independent consistency boundaries — **Product**, **ProductVariant**,
**ProductSpecification**, **Sku**, **ClassificationScheme**,
**ClassificationNode**, **Bundle** — referencing each other by ID only
(product02.md). `ProductClassification` is an association VO, not an
aggregate.

### Where the documented model was consolidated/improved

| Topic | Decision |
| --- | --- |
| Lifecycle | Strict, ordered `DRAFT → ACTIVE → DISCONTINUED → ARCHIVED`; every illegal transition throws. |
| Exceptions | `InvalidStateException` for state transitions, `BusinessRuleViolation` for duplicates/archived-immutability. |
| `ProductType` | 6 values (`PHYSICAL, DIGITAL, SERVICE, SUBSCRIPTION, FEE, BUNDLE`). |
| `tenantId` | Absent from the domain — belongs to adapter/platform. |
| Identifiers | `ProductIdentifier` on Product; `SkuIdentifier` on SKU; add **and remove** via application commands. |
| Classification | Separate scheme/node aggregates + association; one product may sit in multiple schemes. |
| Options | `OptionDefinition` only; `OptionGroup.withOption` / `withoutOption` keep groups immutable. |
| Spec mutation | `addOption` / `removeOption` / `removeOptionGroup` / `removeAttribute` on the aggregate. |
| Uniqueness | Product/SKU code uniqueness at application layer via `existsByCode` ports. |
| Length limits | Name ≤ 512, description ≤ 10 000 chars (absorbed from `product-core`). |

### Application layer

**Product:** `CreateProduct`, `UpdateProduct`, `ActivateProduct`,
`DiscontinueProduct`, `ArchiveProduct`, `AddProductIdentifier`,
`RemoveProductIdentifier`

**Variant:** `CreateVariant`, `ActivateVariant`, `DiscontinueVariant`,
`ArchiveVariant`

**Specification:** `CreateSpecification`, `AddSpecificationAttribute`,
`RemoveSpecificationAttribute`, `AddSpecificationOptionGroup`,
`RemoveSpecificationOptionGroup`, `AddSpecificationOption`,
`RemoveSpecificationOption`

**SKU:** `CreateSku`, `ActivateSku`, `DiscontinueSku`, `ArchiveSku`,
`AddSkuIdentifier`, `RemoveSkuIdentifier`

**Bundle:** `CreateBundle`, `ActivateBundle`, `DiscontinueBundle`,
`ArchiveBundle`, `AddBundleComponent`, `RemoveBundleComponent`

**Classification:** `CreateClassificationScheme`,
`ActivateClassificationScheme`, `ArchiveClassificationScheme`,
`RenameClassificationScheme`, `CreateClassificationNode`,
`MoveClassificationNode`, `RenameClassificationNode`,
`ArchiveClassificationNode`, `ClassifyProduct`, `DeclassifyProduct`

Handlers orchestrate reactively (`Uni<Result<T>>`), publish pending
events through `EventPublisher`, and never touch infrastructure.

### Enforced boundaries (ArchUnit)

* domain: no Quarkus/Hibernate/Kafka/Jackson, no Mutiny, no
  application/spi/adapter dependencies;
* domain: **no dependency on commerce, pricing, promotion, inventory,
  accounting, retail or ecommerce**;
* application: no infrastructure, no adapter dependency.

## Deliberately out of scope (Product module)

`price`, `promotion`, `discount`, `tax`, `inventory`, `warehouse`,
`customer`, `supplier`, `GL account`, subscription billing and order
management. Those live under `framework/commerce/*` and consume Product
IDs/events.

Configuration (customer selection) is **commerce/configuration**, not
product — product02.md places it there deliberately.

## Consolidated: `product-core` (removed)

`product-core` carried a **second, flatter Product domain** in the *same*
package prefix as `product-domain` (`tech.kayys.numou.product.domain.*`).
It has been consolidated into Product 1.0 and the module deleted.

## Bounded-context naming (consolidation round 2)

The Product foundation is a reusable capability consumed by separate
bounded contexts. Those contexts must **never** reuse the bare canonical
names (`Product`, `ProductStatus`, `ProductType`), otherwise imports
become ambiguous across contexts. They keep context-local names and
reference the foundation by `ProductId` and by consuming Product events.

| Context | Was | Now | Relationship to Product foundation |
| --- | --- | --- | --- |
| Catalog (`product-catalog`) | `catalog.domain.model.Product` | `catalog.domain.model.CatalogProduct` | References `product.domain.product.ProductId`; adds price / stock level / catalog status |
| Catalog (`product-catalog`) | `catalog.domain.valueobject.ProductStatus` | `catalog.domain.valueobject.CatalogProductStatus` | Own reversible lifecycle (DRAFT/ACTIVE/INACTIVE/DISCONTINUED); deliberately decoupled from the Product foundation one-way lifecycle |
| POS / Groceries (`commerce/pos/numou-pos-domain`) | `groceries.domain.model.Product` | `groceries.domain.model.PosProduct` | References the Catalog / Product product by `catalogProductId`; carries POS-specific attributes (shelf life, batch lots, allergens, weight, temperature) |
| POS / Groceries | `groceries.domain.identifier.ProductId` | kept as-is, `@Deprecated` | Context-local reference identity; must not be replaced by `product.domain.product.ProductId` |

Guards:
* `ProductDomainArchitectureTest` — exactly one `Product` / `ProductId` /
  `ProductStatus` / `ProductType` inside the Product domain package, and
  the domain must not depend on `com.saas.product..`, `catalog.domain..`
  or `groceries.domain..`.
* `CatalogDomainArchitectureTest` — Catalog exposes `CatalogProduct` and
  `CatalogProductStatus`, and must not depend on the `Product` aggregate.
* `GroceriesDomainArchitectureTest` — POS exposes `PosProduct` and keeps
  its own context-local `ProductId`.

## Legacy module: `product-runtime` (deprecated, not in reactor)

`product-runtime` (`com.saas.product.*`) is the pre-1.0 Quarkus prototype.
It is **excluded from the Maven reactor** and carries a duplicate Product
model. It is retained only as a reference implementation of the
extension-map / JSONB / CQRS patterns; new code must implement those
patterns against `product-domain`. Its core types
(`ProductCore`, `ProductId`, `ProductType`, `ProductStatus`,
`ProductAggregate`, `ProductRepository`, `ProductQuery`, `ProductBehavior`,
`ProductExtension`, `ProductValidator`) are marked `@Deprecated` with a
migration note. See that module's README for the full mapping.

| `product-core` type | Resolution |
| --- | --- |
| `Product` aggregate | Deleted. `product.domain.product.Product` is canonical (events, identifiers, ordered lifecycle). |
| `ProductId` | Already deleted before this consolidation — the module had been switched to the canonical `product.domain.product.ProductId`. Nothing to migrate. |
| `ProductName` (1–512 chars) | Invariant absorbed into `Product.MAX_NAME_LENGTH` + `requireName`. |
| `ProductDescription` (≤10 000 chars) | Invariant absorbed into `Product.MAX_DESCRIPTION_LENGTH` + `normalizeDescription`. |
| `ProductSku` (1–128 chars) | Deleted. `sku.Sku` / `SkuId` is a separate aggregate with its own code, enforced via `SkuRepository.existsByCode`. |
| `ProductStatus` (`+INACTIVE`) | Deleted. See lifecycle note below. |
| `ProductType` | Deleted. Product 1.0 superset wins (6 values). |
| `ProductRepository` | Deleted. Persistence ports live in `product.spi.port`. |

**Lifecycle note — why `INACTIVE` was not carried over.**
`product-core` modelled `deactivate()` as a *reversible* transition to
`INACTIVE` (editable again afterwards). Product 1.0 is deliberately
one-way: `DRAFT → ACTIVE → DISCONTINUED → ARCHIVED`, every illegal
transition throwing `InvalidStateException`. Reintroducing `INACTIVE`
would have weakened a documented domain invariant, so the predecessor of
`DISCONTINUED` was kept.

`ProductDomainArchitectureTest` now guards against regression: exactly one
`Product`, `ProductId`, `ProductStatus` and `ProductType` in the domain, and
no repository port declared inside the domain package.

## Legacy modules

* **`product-catalog`** — separate Catalog context.
* **`product-runtime`** — pre-existing Quarkus prototype (`com.saas.product.*`);
  not in the reactor until migrated. It has no source dependency on
  `product-domain` — its model is entirely self-contained.
