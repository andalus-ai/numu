

## 10. M0.2 source-tracing update — verified reactor samples

The following aggregator POMs were fetched from `main` on 2026-11-05. This is a targeted sample, not an exhaustive repository reactor inventory.

| Reactor path | Verified declared modules / role | Evidence |
|---|---|---|
| `core/pom.xml` | `numou-platform`; aggregator includes `Framework` | `core/pom.xml` |
| `finance/pom.xml` | Accounting, billing, common, payment, subscription, Syirkah, tax, and wallet SPI/domain/application/adapter/PostgreSQL adapter | `finance/pom.xml` |
| `commerce/pom.xml` | Offering, configuration, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel, marketplace | `commerce/pom.xml` |
| `workspace/pom.xml` | Workspace domain, application, JDBC persistence, REST adapter and notification adapter | `workspace/pom.xml` |
| `crm/pom.xml` | CRM domain, application and adapter | `crm/pom.xml` |
| `supply/pom.xml` | Inventory, purchasing, fulfillment and warehouse | `supply/pom.xml` |
| `logistics/pom.xml` | Fleet plus courier domain, application and PostgreSQL adapter | `logistics/pom.xml` |
| `mobility/pom.xml` | Mobility kernel, pricing, driver and ride domain/SPI/application/adapters, integration, persistence and API | `mobility/pom.xml` |
| `construction/pom.xml` | Construction domain, SPI, application and adapter | `construction/pom.xml` |
| `workforce/pom.xml` | Workforce domain, SPI, application, adapter, JDBC persistence and HRIS | `workforce/pom.xml` |

### Findings from the sample

1. Finance and Commerce already contain meaningful product breadth. The competitive question is workflow completeness and integration, not whether those module names exist.
2. Finance wallet has explicit SPI, domain, application, adapter and PostgreSQL adapter artifacts in its aggregator. This is evidence of modular decomposition, not proof of production readiness.
3. Workspace is explicitly separated into domain/application/persistence/REST/notification artifacts; its own descriptor states it is optional.
4. CRM, Construction and Workforce use recognizable domain/application/adapter layering, while their complete test and runtime behavior still requires source tracing.
5. Supply and Logistics are distinct aggregators; cross-capability handoffs must be verified through contracts rather than inferred from co-location.
6. Mobility has a relatively broad set of subdomains and integration artifacts; the audit should verify its current contracts and tests rather than introduce parallel driver/ride/geo models.

### Remaining M0.2 work

- Enumerate all aggregator POMs, including nested reactor roots and shared parent relationships.
- Inspect root-level GitHub Actions workflows and map each workflow to its actual Maven reactor commands.
- Inventory test modules, architecture tests, database migrations and deployment/runtime entry points.
- Inspect actual public ports and persistence ownership for the sampled capability groups.
- Build an evidence-backed duplicate-model and reverse-dependency register.

The sampled POMs are structural evidence only. No build, test, CI, runtime, or end-to-end workflow result is claimed by this update.
