
┌────────────────────────────────────────────────────────────────────────┐
│                   numou-foundation-domain                            │
│  DomainId, TenantId, AuditMeta, PersonRef, ResourceRef, Money, DateRange│
└────────────────────────────────────────────────────────────────────────┘
          ▲                   ▲                   ▲                   ▲
          │                   │                   │                   │
┌─────────┴─────────┐ ┌───────┴─────────┐ ┌───────┴─────────┐ ┌───────┴─────────┐
│   organization    │ │   scheduling    │ │     leave       │ │      skill      │
│ (Corporate Orgs,  │ │(Rosters, Shifts,│ │(Entitlements,   │ │ (Competency &   │
│  Units, Branches) │ │  Availability)  │ │ Accruals, Req)  │ │  Licensing)     │
└─────────▲─────────┘ └───────▲─────────┘ └───────▲─────────┘ └───────▲─────────┘
          │                   │                   │                   │
          └───────────────────┴─────────┬─────────┴───────────────────┘
                                        │ (loosely coupled via refs & events)
                                        ▼
                             ┌─────────────────────┐
                             │      workforce      │
                             │ Worker, Employment, │
                             │ Position Assignment │
                             └─────────────────────┘
                             

### Detailed Domain Breakdown & Target Boundaries

#### 1. `organization` (Foundational / Shared Context)
* **What it is**: Legal entities, holdings, subsidiaries, operating units, departments, branches, cost centers.
* **Other use cases**:
  * **Finance / GL**: Company code, ledger entity, cost allocation across departments.
  * **Supply Chain / Logistics**: Purchasing organizations, distribution plants, warehouses.
  * **CRM / Commerce**: Partner organizations, customer accounts.
  * **Projects**: Delivering org vs. customer org.
* **Verdict**: **Should NOT be inside `workforce`**. It belongs either in a dedicated top-level module (`Framework/organization` or consolidated with [`corporate/numou-company`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Projects/Numou/Numou-Platform/Framework/corporate/numou-company/)). Workforce should merely reference `OrganizationRef` (as we did in `Employment.organization()`).

---

#### 2. `scheduling` (Generic Operational Capability)
* **What it is**: Time-bounding, calendar patterns, shifts, rosters, working windows, and availability declarations.
* **Other use cases**:
  * **Fleet / Logistics**: Scheduling driver shifts and vehicle dispatch windows.
  * **Healthcare / Field Operations**: Scheduling technician visits, doctor consultation slots, room reservations.
  * **Manufacturing**: Machine maintenance windows and production line shifts.
* **Verdict**: **Should be an independent bounded context** (`Framework/scheduling`). 
  * In pure scheduling, a shift is assigned to a `ResourceRef(type: WORKER | VEHICLE | ROOM | MACHINE, id: UUID)`.
  * `workforce` simply consumes `scheduling` by passing `ResourceRef(type = WORKER, id = workerId)`.

---

#### 3. `leave` / `time-off` (Absence & Entitlement Management)
* **What it is**: Entitlement rules, accruals, leave balances, requests, and manager approvals.
* **How it interacts with Scheduling**:
  * Leave is HR/entitlement policy-driven, while Scheduling is operational time-slot allocation.
  * When a `LeaveRequest` is approved, it raises a domain event: `LeaveRequestApproved`.
  * The `scheduling` module subscribes to `LeaveRequestApproved` and automatically creates an `UNAVAILABLE` block in `WorkerAvailability`.
* **Verdict**: Can either be a submodule under a broader `time-management` engine or an independent bounded context alongside `workforce`.

---

#### 4. `capability` / `skills`
* **What it is**: Competency taxonomies, ratings, certifications, licensing, and validity periods.
* **Other use cases**: Vendor compliance verification, project staffing requirement filters, equipment certifications.
* **Verdict**: Can be a shared competency/capability catalogue.

---

### Recommended Target Architecture

```
Framework/
├── corporate/ (or organization/)
│   ├── organization-domain/      <-- Legal Entity, OrganizationUnit, Cost Center
│   ├── organization-spi/
│   └── organization-application/
│
├── scheduling/
│   ├── scheduling-domain/        <-- WorkPattern, Schedule, Shift, Availability
│   ├── scheduling-spi/
│   └── scheduling-application/
│
├── leave/ (or time-management/)
│   ├── leave-domain/             <-- LeaveType, LeaveBalance, LeaveRequest
│   ├── leave-spi/
│   └── leave-application/
│
└── workforce/
    ├── workforce-domain/         <-- Worker, Employment, Position, PositionAssignment
    ├── workforce-spi/
    ├── workforce-application/    <-- Orchestrates Worker, references OrganizationRef,
    └── workforce-adapter/            and queries Scheduling/Leave via decoupled ports
```

---

