# Product Module — DEPRECATED, DO NOT USE

This is the pre-1.0 Quarkus prototype. It is NOT the Product foundation.

## Status: DEPRECATED — MIGRATE AWAY

`product-runtime` (artifact `numou-product-runtime`) is **excluded from
the Maven reactor** and carries a **duplicate Product model**
(`com.saas.product.*`) that predates Product 1.0. It must **not** be used
as the canonical Product model. The canonical model lives in
`numou-product-domain` (`tech.kayys.numou.product.domain.*`) and is
consumed by `numou-product-application`, `numou-product-adapter`,
`numou-product-spi`.

This module is retained only as a **reference implementation** of the
extension-map / JSONB / CQRS patterns described below. New code must
implement those patterns against `product-domain`, not against this
module.

### Duplicate model — what must NOT be copied here

| This module (`com.saas.product.*`) | Canonical (`tech.kayys.numou.product.domain.*`) |
| --- | --- |
| `core/model/ProductId` (String-backed) | `product.domain.product.ProductId` (`record(UUID)` implements `DomainId<UUID>`) |
| `core/model/ProductCore` | `product.domain.product.Product` (aggregate root, events, identifiers) |
| `core/model/ProductType` (8 values incl. `VARIANT_PARENT`, `VARIANT`) | `product.domain.product.ProductType` (6 values: PHYSICAL, DIGITAL, SERVICE, SUBSCRIPTION, FEE, BUNDLE) |
| `core/lifecycle/ProductStatus` (DRAFT/ACTIVE/SUSPENDED/ARCHIVED, reversible) | `product.domain.product.ProductStatus` (DRAFT → ACTIVE → DISCONTINUED → ARCHIVED, one-way) |
| `core/ProductAggregate` | `product.domain.product.Product` |
| `spi/ProductRepository` / `spi/ProductQuery` | `product.spi.port.*Repository` (application-layer ports) |

### Migration path

1. Re-implement the extension-map pattern using `ProductExtension`
   against `product.domain.product.Product`.
2. Re-implement JSONB storage against `product.domain.sku.Sku`,
   `variant.ProductVariant`, `specification.ProductSpecification`.
3. Re-implement CQRS write/read split against
   `product.spi.port.*Repository` ports.
4. Delete this module once migration is complete.

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                         REST API Layer                           │
│   ProductResource  ·  DTOs  ·  DtoMapper  ·  ExceptionMapper   │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      Application Services                        │
│          ProductService  ·  ProductQueryService                 │
└──────┬──────────────────────┬───────────────────────────────────┘
       │                      │
┌──────▼──────┐  ┌────────────▼──────────────────────────────────┐
│   Domain    │  │            Infrastructure                       │
│  Aggregate  │  │  PanacheProductRepository  ·  ExtensionSerial. │
│  Extensions │  │  ProductAggregateMapper  ·  Migrations (SQL)   │
│  Behaviors  │  └───────────────────────────────────────────────┘
│  Validators │
│   Events    │
└─────────────┘
```

---

## Key Design Decisions

### 1. Extension Map Pattern
Every domain attaches its own `ProductExtension` to the aggregate.
Core fields (SKU, name, type, status) are universal.
Domain-specific fields live in their extension only.

| Context        | Extension Class            | What it adds                              |
|----------------|----------------------------|-------------------------------------------|
| `ecommerce`    | `EcommerceExtension`       | Price, stock, shipping, tier pricing, variants |
| `fnb`          | `FnbExtension`             | Channel prices, modifiers, dietary tags, prep time |
| `subscription` | `SubscriptionExtension`    | Billing intervals, seats, trial, entitlements |
| _your domain_  | Implement `ProductExtension` | Anything you need                        |

### 2. JSONB Storage
Extensions are stored as a single `JSONB` column in PostgreSQL.
Adding a new extension requires zero schema migrations.

### 3. CQRS Split
- `ProductService` → writes (create, update, lifecycle, extensions, price)
- `ProductQueryService` → reads (list, search, filter)
- `ProductRepository` (write SPI) and `ProductQuery` (read SPI) are separate interfaces

### 4. Lifecycle State Machine
```
DRAFT ──► ACTIVE ──► SUSPENDED ──► ACTIVE (reactivate)
  │          │              │
  └──────────┴──────────────┴──► ARCHIVED (terminal)
```

### 5. Pricing Strategy Pattern
Each extension context has a `ProductBehavior` implementation.
Pricing is fully auditable via `PricingResult.lineItems`.

---

## Adding a New Domain

To add, for example, a `pharmacy` domain:

**Step 1** — Create the extension:
```java
public final class PharmacyExtension implements ProductExtension {
    public static final String CONTEXT = "pharmacy";
    // ... fields: requiresPrescription, drugClass, expiryMonths ...
    @Override public String getContext() { return CONTEXT; }
}
```

**Step 2** — Create the behavior:
```java
@ApplicationScoped
public class PharmacyBehavior implements ProductBehavior {
    @Override public String getContext() { return "pharmacy"; }
    @Override public PricingResult calculatePrice(ProductAggregate p, PricingContext ctx) {
        // implement pharmacy-specific pricing
    }
}
```

**Step 3** — Optionally add a validator:
```java
@ApplicationScoped
public class PharmacyValidator implements ProductValidator {
    @Override public String supports() { return "pharmacy"; }
    @Override public void validate(ProductAggregate p) { ... }
}
```

**Step 4** — Register in `ExtensionSerializer.REGISTRY`:
```java
private static final Map<String, Class<? extends ProductExtension>> REGISTRY = Map.of(
    "ecommerce",    EcommerceExtension.class,
    "fnb",          FnbExtension.class,
    "subscription", SubscriptionExtension.class,
    "pharmacy",     PharmacyExtension.class   // ← add this line
);
```

**That's it.** No changes to ProductAggregate, ProductService, or the database.

---

## Module Structure

```
src/main/java/com/saas/product/
├── core/
│   ├── model/          ProductId, ProductCore, ProductType, Money
│   ├── lifecycle/      ProductStatus
│   ├── pricing/        PricingContext, PricingResult
│   └── ProductAggregate.java
├── spi/                ProductRepository, ProductQuery, ProductExtension,
│                       ProductBehavior, ProductValidator
├── service/            ProductService, ProductQueryService
├── events/             ProductEvent (sealed), EventPublisher, CdiEventPublisher
├── runtime/            ProductBehaviorRegistry
├── extension/
│   ├── ecommerce/      EcommerceExtension, EcommerceBehavior, EcommerceValidator
│   ├── fnb/            FnbExtension, FnbBehavior, FnbValidator
│   └── subscription/   SubscriptionExtension, SubscriptionBehavior, SubscriptionValidator
├── infrastructure/
│   ├── entity/         ProductJpaEntity
│   ├── jpa/            PanacheProductRepository, InMemoryProductRepository
│   ├── ExtensionSerializer.java
│   └── ProductAggregateMapper.java
└── api/
    ├── dto/            ProductDto (all request/response records)
    ├── mapper/         ProductDtoMapper
    └── rest/           ProductResource, GlobalExceptionMapper

src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__product_schema.sql      (core schema + outbox)
    └── V2__product_search_index.sql (FTS + trigram)

src/test/java/com/saas/product/
├── unit/               ProductAggregateTest, EcommerceBehaviorTest,
│                       FnbBehaviorTest, MoneyTest
└── integration/        ProductServiceIntegrationTest
```

---

## REST API Reference

| Method | Path                                  | Description                        |
|--------|---------------------------------------|------------------------------------|
| GET    | `/api/v1/products`                    | List (paginated, filterable)       |
| GET    | `/api/v1/products/{id}`               | Get by ID                          |
| GET    | `/api/v1/products/sku/{sku}`          | Get by SKU                         |
| POST   | `/api/v1/products`                    | Create (→ DRAFT)                   |
| PATCH  | `/api/v1/products/{id}`               | Update core fields                 |
| DELETE | `/api/v1/products/{id}`               | Archive (soft delete)              |
| POST   | `/api/v1/products/{id}/activate`      | DRAFT → ACTIVE                     |
| POST   | `/api/v1/products/{id}/suspend`       | ACTIVE → SUSPENDED                 |
| PUT    | `/api/v1/products/{id}/extensions/ecommerce`     | Attach ecommerce ext  |
| PUT    | `/api/v1/products/{id}/extensions/fnb`           | Attach FnB ext        |
| PUT    | `/api/v1/products/{id}/extensions/subscription`  | Attach subscription   |
| DELETE | `/api/v1/products/{id}/extensions/{ctx}`         | Remove extension      |
| POST   | `/api/v1/products/{id}/price`         | Calculate price (auditable)        |

All requests require `X-Tenant-Id` header.
Swagger UI available at `/swagger-ui` when running.

---

## What Comes Next (Future Phases)

| Phase | What to build                                              |
|-------|------------------------------------------------------------|
| 2     | Inventory module (stock movements, reservations)          |
| 3     | Order module (references ProductId + PricingResult)       |
| 4     | Kafka outbox relay (OutboxEventPublisher)                  |
| 5     | Elasticsearch read projection (swap ProductQuery impl)    |
| 6     | Multi-currency support (ExchangeRateService)              |
| 7     | AI pricing agent (reads PricingResult audit trail)        |
