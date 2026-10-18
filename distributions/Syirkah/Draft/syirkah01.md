Yes. I would make the next step **Phase 12 — Syirkah ERP Core / Business Control Plane**.

This is where we turn the platform we've designed into something that can behave like a conventional ERP when a tenant wants it, while **not forcing every Syirkah product to depend on the whole ERP**.

The important architectural decision is:

> **ERP is a composition of reusable business capabilities, not one giant domain.**

Your existing Foundation already has the right base primitives—domain/application separation, commands, queries, events, results, transactions, value objects, aggregate roots, and architecture rules—so we should build on those rather than introduce another framework layer. syirkah-foundation syirkah-foundation

---

# Phase 12 — ERP Core / Business Control Plane

I would structure the next layer roughly like this:

```text
                         SYIRKAH FOUNDATION
                                │
                                ▼
                    COMMON BUSINESS CAPABILITIES
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
      Party                 Organization            Product
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                                ▼
                       ERP CONTROL PLANE
                                │
       ┌────────────────────────┼────────────────────────┐
       │                        │                        │
   Accounting                Reporting                 Rules
       │                        │                        │
       ├── GL                  ├── Query                ├── Policy
       ├── AR                  ├── Report               ├── Approval
       ├── AP                  ├── KPI                  ├── Validation
       ├── Tax                 └── Analytics            └── Automation
       ├── Cash
       └── Budget
                                │
                                ▼
                         BUSINESS PROCESSES
                                │
       ┌─────────────┬──────────┼──────────┬──────────────┐
       ▼             ▼          ▼          ▼              ▼
    Procure       Sell       Inventory   Payroll       Project
       │             │          │          │              │
       └─────────────┴──────────┼──────────┴──────────────┘
                                ▼
                              Events
                                │
                                ▼
                         External Ecosystem
```

The key is that **Accounting, Reporting, Rules, Budgeting, Tax, etc. are themselves reusable capabilities**.

---

# 12.1 First: Do Not Build an "ERP Domain"

I would explicitly avoid:

```text
erp-domain
    ├── accounting
    ├── purchasing
    ├── inventory
    ├── sales
    ├── payroll
    ├── reporting
    └── ...
```

as one giant domain.

Instead:

```text
accounting/
procurement/
commerce/
inventory/
workforce/
assets/
project/
process/
reporting/
rules/
```

Each remains independently usable.

Then create ERP compositions:

```text
syirkah-erp/
    ├── erp-accounting
    ├── erp-procurement
    ├── erp-sales
    ├── erp-inventory
    ├── erp-assets
    ├── erp-workforce
    ├── erp-project
    ├── erp-reporting
    └── erp-bom
```

This allows:

### Simple accounting product

```xml
<dependency>
    <artifactId>syirkah-accounting</artifactId>
</dependency>
```

### Full ERP

```xml
<dependency>
    <artifactId>syirkah-erp-bom</artifactId>
</dependency>
```

### Retail

```text
Commerce
Inventory
Pricing
Promotion
Payment
Accounting
Reporting
```

### Coffee shop

```text
Product
POS
Inventory
Payment
Accounting
Reporting
```

### Large ERP

```text
Commerce
Procurement
Supply
Inventory
Manufacturing
Logistics
HR
Payroll
Assets
Projects
Accounting
Tax
Budget
Reporting
Workflow
```

That is the flexibility you were originally aiming for.

---

# 12.2 Accounting Is the First Major ERP Core

I recommend making Accounting the first implementation of P12.

The architecture:

```text
accounting/
├── accounting-domain/
├── accounting-application/
└── accounting-spi/
```

Later:

```text
accounting/
├── accounting-domain
├── accounting-application
├── accounting-spi
├── accounting-persistence
├── accounting-reporting
└── accounting-integration
```

Don't create persistence-specific code in the domain.

This is especially compatible with the architecture discipline already present in Foundation. syirkah-foundation

---

# 12.3 Accounting Domain Map

```text
Accounting
│
├── Ledger
│   ├── General Ledger
│   ├── Journal
│   └── Journal Entry
│
├── Chart of Accounts
│
├── Fiscal
│   ├── Fiscal Year
│   ├── Fiscal Period
│   └── Period Control
│
├── Accounts Receivable
│   ├── Customer Account
│   ├── Receivable
│   ├── Invoice
│   └── Settlement
│
├── Accounts Payable
│   ├── Supplier Account
│   ├── Payable
│   ├── Supplier Invoice
│   └── Settlement
│
├── Cash & Bank
│   ├── Cash Account
│   ├── Bank Account
│   ├── Payment
│   └── Reconciliation
│
├── Tax
│
├── Currency
│
├── Cost Accounting
│
├── Budget
│
└── Accounting Policy
```

---

# 12.4 General Ledger

The fundamental accounting abstraction:

```text
Ledger
   │
   └── Journal
         │
         └── Journal Entry
                ├── Debit
                └── Credit
```

A journal entry:

```text
JournalEntry
├── id
├── tenantId
├── ledgerId
├── postingDate
├── documentDate
├── reference
├── description
├── currency
├── lines
└── status
```

Lines:

```text
JournalLine
├── account
├── debit
├── credit
├── currency
├── exchangeRate
├── dimensions
└── description
```

Core invariant:

```text
SUM(debit) == SUM(credit)
```

This should be a domain invariant, not a database convention.

---

# 12.5 Never Update Posted Journal Entries

This is one of the most important accounting rules.

Bad:

```text
UPDATE journal_entry
SET amount = ...
```

after posting.

Instead:

```text
Original Entry
     ↓
Correction / Reversal
     ↓
New Entry
```

Therefore:

```text
POSTED
```

is effectively immutable.

---

# 12.6 Accounting Period

```text
FiscalYear
    │
    ├── Period 01
    ├── Period 02
    ├── Period 03
    └── ...
```

Period status:

```text
OPEN
SOFT_CLOSED
CLOSED
LOCKED
```

Posting must respect the period.

Example:

```java
if (!period.canPost()) {
    throw new AccountingPeriodClosedException(...);
}
```

---

# 12.7 Chart of Accounts

```text
ChartOfAccounts
    │
    ├── Assets
    ├── Liabilities
    ├── Equity
    ├── Revenue
    └── Expenses
```

But don't hardcode exactly those categories into the entire platform.

Different tenants and accounting standards can require different structures.

Use:

```text
AccountType
Account
AccountGroup
AccountHierarchy
```

---

# 12.8 Account

```text
Account
├── id
├── code
├── name
├── type
├── parent
├── currencyPolicy
├── active
└── postingAllowed
```

Example:

```text
1100 Cash
1110 Bank
1200 Accounts Receivable

4000 Sales Revenue
5000 Cost of Goods Sold

6000 Salaries
6100 Rent
```

---

# 12.9 Account Dimensions

This becomes very important for your multi-business platform.

Don't create:

```text
account_warehouse_1
account_warehouse_2
account_branch_1
account_branch_2
```

Instead:

```text
JournalLine
    │
    ├── Account
    ├── Branch
    ├── Department
    ├── Project
    ├── Cost Center
    ├── Product
    └── Channel
```

This enables reporting without exploding the Chart of Accounts.

---

# 12.10 Accounting Dimensions

Generic:

```text
AccountingDimension
├── dimensionType
├── value
└── metadata
```

Possible dimensions:

```text
BRANCH
DEPARTMENT
COST_CENTER
PROJECT
PRODUCT
CHANNEL
WAREHOUSE
SALES_PERSON
CUSTOMER
SUPPLIER
```

But dimensions should be configurable.

A coffee shop may need:

```text
Branch
Channel
Product Category
```

An ERP may need:

```text
Company
Branch
Department
Cost Center
Project
Product
Warehouse
Channel
```

---

# 12.11 Multi-Company

This is different from multi-tenancy.

```text
Tenant
   │
   ├── Company A
   ├── Company B
   └── Company C
```

A tenant could represent:

```text
Holding Group
```

while accounting operates per legal entity.

Therefore:

```text
Tenant
  └── LegalEntity
       └── Ledger
```

This will be important later for consolidation.

---

# 12.12 Multi-Tenant + Multi-Company

The hierarchy should be capable of:

```text
Platform
  │
  ├── Tenant A
  │     ├── Company A1
  │     └── Company A2
  │
  └── Tenant B
        ├── Company B1
        ├── Company B2
        └── Company B3
```

And:

```text
Company
 ├── Branch
 ├── Department
 ├── Warehouse
 └── Cost Center
```

This is much more useful than assuming:

```text
tenant == company
```

---

# 12.13 Accounts Receivable

AR:

```text
Customer
   ↓
Invoice
   ↓
Receivable
   ↓
Payment
   ↓
Settlement
```

But remember:

**Commerce owns the sale.**

**Accounting owns the receivable.**

Example:

```text
OrderCompleted
      ↓
Billable Event
      ↓
Accounting
      ↓
Receivable Created
```

---

# 12.14 Accounts Payable

AP:

```text
Supplier
   ↓
Supplier Invoice
   ↓
Payable
   ↓
Payment
   ↓
Settlement
```

Procurement owns the purchasing process.

Accounting owns the liability.

---

# 12.15 Three-Way Matching

This is classic ERP functionality.

```text
Purchase Order
      │
      ├──────────────┐
      ▼              ▼
Goods Receipt    Supplier Invoice
      │              │
      └──────┬───────┘
             ▼
         Match Engine
             │
       ┌─────┴─────┐
       ▼           ▼
     Match       Variance
```

Rule:

```text
PO
≅
Receipt
≅
Invoice
```

But matching should be configurable.

Example:

```text
Quantity tolerance = 2%
Price tolerance = 1%
```

---

# 12.16 Accounts Receivable / Payable Are Not Separate Worlds

Both can share:

```text
Subledger
Settlement
OpenItem
Allocation
Aging
Currency
Terms
```

So I'd model a generic accounting concept:

```text
OpenItem
```

with specialized contexts:

```text
Receivable
Payable
```

---

# 12.17 Payment

Payment should remain reusable across:

- POS,
- ecommerce,
- invoices,
- subscriptions,
- suppliers,
- payroll.

The Accounting side records:

```text
Payment
   ↓
Settlement
```

The payment gateway side handles:

```text
Authorization
Capture
Refund
Gateway
```

These are different bounded contexts.

---

# 12.18 Cash & Bank

```text
CashAccount
BankAccount
Payment
Receipt
Transfer
Reconciliation
```

Bank transfer:

```text
Bank A
   ↓
Transfer
   ↓
Bank B
```

Accounting:

```text
Debit Bank B
Credit Bank A
```

---

# 12.19 Bank Reconciliation

```text
Bank Statement
       ↓
Imported Transactions
       ↓
Matching Engine
       ↓
Accounting Entries
       ↓
Reconciled
```

Matching candidates:

```text
amount
date
reference
counterparty
currency
```

But never silently create accounting entries without a defined policy.

---

# 12.20 Tax

Tax deserves its own reusable capability.

```text
tax/
├── tax-definition
├── tax-rate
├── tax-rule
├── tax-jurisdiction
├── tax-calculation
└── tax-reporting
```

Example:

```text
Product
   ↓
Tax Classification
   ↓
Customer
   ↓
Jurisdiction
   ↓
Tax Rule
   ↓
Tax Amount
```

The actual tax engine should be policy-driven.

---

# 12.21 Accounting Rules

This is where your question about **rules** becomes important.

Don't create one giant:

```text
RuleEngine
```

that knows everything.

Instead:

```text
rules/
├── policy
├── condition
├── action
├── evaluation
└── version
```

Domains define their own policies.

---

# 12.22 Rule Architecture

Generic:

```java
public interface Rule<C> {

    RuleResult evaluate(C context);
}
```

For example:

```java
public interface PostingRule {

    PostingDecision evaluate(
        PostingContext context
    );
}
```

Another:

```java
public interface PricingRule {

    PricingDecision evaluate(
        PricingContext context
    );
}
```

Another:

```java
public interface ApprovalRule {

    ApprovalDecision evaluate(
        ApprovalContext context
    );
}
```

Same framework.

Different domain semantics.

---

# 12.23 Rule Versioning

Rules must be versioned just like:

- workflow,
- documents,
- pricing.

```text
Rule
 ├── v1
 ├── v2
 └── v3
```

A historical transaction must remain explainable.

For example:

```text
Invoice #1001
Tax calculated using TaxRule v4
```

not:

```text
Tax calculated using whatever rule exists today
```

---

# 12.24 Effective Dates

Rules should support:

```text
effectiveFrom
effectiveUntil
```

Example:

```text
Pricing Rule
2026-01-01 → 2026-06-30
2026-07-01 → ...
```

Same for:

```text
Tax
Accounting
Approval
Pricing
Promotion
Payroll
Commission
```

---

# 12.25 ERP Configuration

We should distinguish:

### Configuration

```text
"What should this tenant use?"
```

from:

### Rules

```text
"How should the decision be calculated?"
```

from:

### Data

```text
"What actually happened?"
```

For example:

```text
Tenant Configuration
    ↓
Uses VAT
    ↓
Tax Rule
    ↓
Invoice
```

---

# 12.26 Reporting

Reporting should **not query every domain database directly**.

Instead:

```text
Domain Events
      ↓
Projections
      ↓
Reporting Read Model
      ↓
Reports
```

This works in both:

```text
Monolith
```

and:

```text
Microservices
```

---

# 12.27 Reporting Architecture

```text
                    Business Domains
                           │
                           ▼
                        Events
                           │
                           ▼
                    Reporting Projectors
                           │
               ┌───────────┼───────────┐
               ▼           ▼           ▼
             Sales      Finance     Inventory
               │           │           │
               └───────────┼───────────┘
                           ▼
                    Reporting Model
                           │
               ┌───────────┼───────────┐
               ▼           ▼           ▼
             Report       KPI       Dashboard
```

---

# 12.28 Operational Reporting vs Analytics

Keep these separate.

### Operational reporting

```text
Today's Sales
Outstanding Invoice
Stock on Hand
Open Purchase Orders
Pending Approvals
```

Can use near-real-time projections.

### Analytical reporting

```text
Sales trend
Margin analysis
Customer profitability
Inventory turnover
Branch performance
Year-over-year growth
```

Can eventually use:

```text
Data Warehouse
Lakehouse
OLAP
External BI
```

---

# 12.29 Report Definition

```text
ReportDefinition
├── id
├── code
├── name
├── version
├── parameters
├── queryDefinition
└── permissions
```

Don't make the query definition directly depend on PostgreSQL syntax.

---

# 12.30 KPI

KPI:

```text
KpiDefinition
├── code
├── name
├── formula
├── dimensions
└── target
```

Examples:

```text
Gross Margin
Inventory Turnover
DSO
DPO
Revenue
EBITDA
Order Fulfillment Rate
On-Time Delivery
```

---

# 12.31 Financial Reports

Accounting should provide standard report capabilities:

```text
Balance Sheet
Profit & Loss
Cash Flow
Trial Balance
General Ledger
AR Aging
AP Aging
Tax Summary
```

But don't hardcode a single country's accounting format.

Use report definitions and accounting policies.

---

# 12.32 Financial Statement Architecture

```text
Journal Entries
      ↓
Ledger Projection
      ↓
Account Balances
      ↓
Financial Statement Mapping
      ↓
       ┌───────────────┐
       │               │
      P&L           Balance Sheet
       │
       └───────────────┐
                       ▼
                  Cash Flow
```

---

# 12.33 Budgeting

Budget belongs close to Accounting but should remain separate from actual ledger.

```text
budget/
├── budget
├── budget-version
├── budget-line
├── allocation
├── forecast
└── variance
```

Example:

```text
2027 Budget
    │
    ├── Revenue
    ├── Salaries
    ├── Rent
    ├── Marketing
    └── Equipment
```

---

# 12.34 Budget vs Actual

Reporting can calculate:

```text
Budget
Actual
Variance
Variance %
```

Example:

```text
Marketing

Budget: 100M
Actual: 120M
Variance: +20M
```

The ledger remains authoritative for actuals.

---

# 12.35 Forecast

Forecast should not overwrite budget.

```text
Budget
   │
   └── Forecast
          │
          ├── Actual-to-date
          └── Expected-future
```

This enables:

```text
Budget
Actual
Forecast
```

simultaneously.

---

# 12.36 Cost Accounting

For ERP customers:

```text
CostCenter
ProfitCenter
Project
Activity
CostAllocation
```

Example:

```text
Branch Jakarta
    ↓
Cost Center
    ↓
Rent
Salary
Utilities
```

Another:

```text
Project P001
    ↓
Labor
Material
Logistics
Subcontractor
```

---

# 12.37 Period Closing

A real ERP needs controlled closing.

```text
Period
 ↓
Operational Complete
 ↓
Reconciliation
 ↓
Adjustments
 ↓
Review
 ↓
Close
 ↓
Lock
```

Possible services:

```text
PeriodCloseService
ReconciliationService
ClosingChecklist
```

This integrates beautifully with P11 workflow.

```text
Period Closing
     ↓
Workflow
     ↓
Department Review
     ↓
Finance Approval
     ↓
Period Closed
```

---

# 12.38 Month-End Close

Eventually:

```text
Month End
   │
   ├── AR reconciliation
   ├── AP reconciliation
   ├── Bank reconciliation
   ├── Inventory reconciliation
   ├── Fixed asset depreciation
   ├── Accruals
   ├── Tax
   ├── Intercompany
   ├── FX revaluation
   └── Financial review
```

Each is a domain capability.

P12 coordinates them.

---

# 12.39 Intercompany

For larger ERP tenants:

```text
Company A
   ↓
Intercompany Transaction
   ↓
Company B
```

This can generate:

```text
Company A:
Receivable

Company B:
Payable
```

Later:

```text
Consolidation
```

can eliminate intercompany balances.

---

# 12.40 Consolidation

Eventually:

```text
Holding
   │
   ├── Company A
   ├── Company B
   └── Company C
          ↓
      Consolidated
       Financials
```

But I would **not implement full consolidation first**.

Build the accounting primitives so consolidation can be added later.

---

# 12.41 ERP Business Processes

This is where everything begins to feel like a real ERP.

### Order to Cash

```text
Customer
 ↓
Quotation
 ↓
Order
 ↓
Fulfillment
 ↓
Delivery
 ↓
Invoice
 ↓
Receivable
 ↓
Payment
```

### Procure to Pay

```text
Demand
 ↓
Purchase Requisition
 ↓
Approval
 ↓
Purchase Order
 ↓
Receipt
 ↓
Supplier Invoice
 ↓
Payable
 ↓
Payment
```

### Record to Report

```text
Business Transactions
 ↓
Subledgers
 ↓
General Ledger
 ↓
Period Close
 ↓
Financial Statements
 ↓
Reporting
```

### Hire to Retire

```text
Employee
 ↓
Employment
 ↓
Attendance
 ↓
Payroll
 ↓
Accounting
```

### Project to Cash

```text
Project
 ↓
Work
 ↓
Milestone
 ↓
Acceptance
 ↓
Invoice
 ↓
Payment
```

---

# 12.42 The Critical ERP Principle

Each process crosses domains.

For example:

```text
Order-to-Cash
```

is **not** an Order domain.

It is an orchestration:

```text
Commerce
   ↓
Inventory
   ↓
Logistics
   ↓
Accounting
   ↓
Payment
```

Therefore:

```text
Process ≠ Domain
```

This is one of the most important principles for Syirkah.

---

# 12.43 ERP Composition

Then we can have:

```text
syirkah-erp-bom
```

which brings together:

```text
Foundation
Ecosystem
Commerce
Product
Pricing
Promotion
Inventory
Procurement
Supply
Logistics
Fleet
Workforce
HR
Assets
Project
Process
Accounting
Tax
Budget
Reporting
```

But a tenant/application doesn't have to use all of them.

---

# 12.44 Example: Simple Accounting Product

```text
simple-accounting
│
├── foundation
├── ecosystem
├── accounting
├── tax
└── reporting
```

No:

```text
Inventory
Manufacturing
Fleet
HR
Project
Procurement
```

unless needed.

---

# 12.45 Example: Retail POS

```text
retail-pos
│
├── foundation
├── ecosystem
├── product
├── pricing
├── promotion
├── commerce
├── inventory
├── payment
├── accounting
└── reporting
```

---

# 12.46 Example: Coffee Shop

```text
coffee-shop
│
├── product
├── recipe
├── pricing
├── promotion
├── POS
├── inventory
├── purchasing
├── payment
├── accounting
└── reporting
```

Potentially:

```text
manufacturing
```

for production/preparation workflows.

---

# 12.47 Example: Full ERP

```text
erp-enterprise
│
├── ecosystem
├── CRM
├── product
├── pricing
├── promotion
├── commerce
├── procurement
├── supply
├── inventory
├── manufacturing
├── warehouse
├── logistics
├── fleet
├── HR
├── payroll
├── assets
├── projects
├── service
├── accounting
├── tax
├── budget
├── reporting
├── workflow
└── integration
```

Same core.

Different composition.

---

# 12.48 Maven BOM

I'd now introduce a hierarchy like:

```text
syirkah-bom
│
├── foundation-bom
├── business-bom
├── accounting-bom
├── commerce-bom
├── supply-bom
├── people-bom
├── operations-bom
└── erp-bom
```

And:

```text
erp-bom
```

imports:

```text
accounting-bom
commerce-bom
supply-bom
people-bom
operations-bom
process-bom
reporting-bom
```

This is how we get the **"pick only what you need"** model you wanted at the beginning.

---

# 12.49 Monolith

A monolithic deployment can simply package:

```text
syirkah-app
    │
    ├── accounting
    ├── commerce
    ├── inventory
    ├── procurement
    ├── HR
    └── ...
```

Same domain/application code.

No duplication.

---

# 12.50 Microservice

Later:

```text
accounting-service
commerce-service
inventory-service
procurement-service
workflow-service
reporting-service
```

They can still use:

```text
syirkah-accounting-domain
syirkah-commerce-domain
...
```

The delivery architecture changes.

The domain doesn't.

That was one of your original goals, and this architecture preserves it.

---

# 12.51 ERP Events

For example:

```text
OrderCompleted
     ↓
AccountingIntegration
     ↓
ReceivableCreated
```

Or:

```text
GoodsReceived
     ↓
InventoryUpdated
     ↓
AccountingPostingRequested
```

Or:

```text
SupplierInvoiceApproved
     ↓
PayableCreated
```

Or:

```text
PaymentSettled
     ↓
ReceivableSettled
```

---

# 12.52 Accounting Integration Port

I'd make this explicit:

```java
public interface AccountingPostingPort {

    PostingResult post(
        AccountingPostingRequest request
    );
}
```

Commerce doesn't know:

```text
JournalEntry
ChartOfAccounts
Ledger
```

unless accounting integration specifically requires it.

Instead:

```text
Commerce
   ↓
AccountingPostingPort
   ↓
Accounting
```

This is a clean Hexagonal boundary.

---

# 12.53 Example Posting

Order completed:

```text
Sales
    Debit  Accounts Receivable
    Credit Sales Revenue

Tax
    Credit Tax Payable

Inventory
    Credit Inventory
    Debit COGS
```

But **which accounts** are used should come from configuration/rules.

Not:

```java
if (orderCompleted) {
    account = 4000;
}
```

---

# 12.54 Accounting Mapping

Introduce:

```text
AccountingMapping
```

Example:

```text
Product Category
       ↓
Revenue Account

Inventory Category
       ↓
Inventory Account

Tax Code
       ↓
Tax Liability Account
```

This is how a generic Product module can work across many tenants.

---

# 12.55 ERP Configuration Example

Tenant A:

```text
Coffee
→ Revenue 4100
→ Inventory 1200
→ COGS 5100
```

Tenant B:

```text
Coffee
→ Revenue 4001
→ Inventory 1300
→ COGS 5001
```

Same Product domain.

Different accounting mapping.

---

# 12.56 What Should Be Shared vs Product-Specific?

### Shared

```text
Money
Currency
Quantity
Tenant
Party
Organization
Product
Price
Order
Payment
Journal
Ledger
Workflow
Document
Event
Rule
Report
```

### Domain-specific

```text
Retail:
POS Session

Manufacturing:
Production Order

Fleet:
Vehicle Trip

HR:
Employment

Accounting:
Journal Entry

Project:
Milestone

Logistics:
Shipment
```

### Application-specific

```text
POS screen
Mobile warehouse app
Accounting UI
ERP dashboard
Kiosk
Ecommerce storefront
```

This is exactly where the distinction between **core**, **domain**, and **delivery/application** becomes valuable.

---

# 12.57 Phase 12 Maven Layout

I would now target:

```text
syirkah/
│
├── foundation/
│
├── ecosystem/
│
├── commerce/
├── product/
├── pricing/
├── promotion/
├── inventory/
├── procurement/
├── supply/
├── logistics/
├── fleet/
├── workforce/
├── hris/
├── payroll/
├── assets/
├── project/
├── process/
│
├── accounting/
│   ├── accounting-domain
│   ├── accounting-application
│   └── accounting-spi
│
├── tax/
├── budget/
│
├── reporting/
│   ├── reporting-domain
│   ├── reporting-application
│   └── reporting-spi
│
├── rules/
│   ├── rules-domain
│   ├── rules-application
│   └── rules-spi
│
└── erp/
    ├── erp-bom
    ├── erp-application
    └── erp-integration
```

---

# 12.58 But One Important Correction

I would **not** make:

```text
rules
```

a dependency of every domain just because we have a reusable rules framework.

Instead:

```text
Foundation
     │
     ▼
Rules Kernel
     │
     ├── Accounting Policies
     ├── Pricing Policies
     ├── Tax Policies
     ├── Approval Policies
     └── Inventory Policies
```

The actual rules remain owned by the bounded context.

This avoids creating a "God Rules Engine."

---

# 12.59 Same Principle for Reporting

Don't make:

```text
reporting
```

the owner of business calculations.

For example:

```text
Accounting
    owns:
    balance calculation semantics

Reporting
    owns:
    presentation/projection
```

Similarly:

```text
Inventory
    owns:
    stock-on-hand semantics

Reporting
    owns:
    stock-on-hand report projection
```

This distinction will save the architecture later.

---

# 12.60 Phase 12 Implementation Order

I would implement it in this order:

```text
P12.01 Accounting Core
P12.02 Chart of Accounts
P12.03 Fiscal Year / Period
P12.04 Journal / Journal Entry
P12.05 Posting Engine
P12.06 Accounting Dimensions
P12.07 AR
P12.08 AP
P12.09 Payment / Settlement
P12.10 Cash & Bank
P12.11 Bank Reconciliation
P12.12 Tax
P12.13 Accounting Mapping
P12.14 Budget
P12.15 Cost Accounting
P12.16 Period Close
P12.17 Financial Statements

P12.18 Rule Kernel
P12.19 Policy Versioning
P12.20 Effective Dating

P12.21 Reporting Core
P12.22 Reporting Projections
P12.23 Report Definition
P12.24 KPI
P12.25 Operational Reports
P12.26 Financial Reports

P12.27 ERP Process Orchestration
P12.28 Order-to-Cash
P12.29 Procure-to-Pay
P12.30 Record-to-Report
P12.31 Project-to-Cash

P12.32 Accounting Events
P12.33 Reporting Events
P12.34 Integration Ports
P12.35 Idempotency
P12.36 Outbox / Reliability
P12.37 Architecture Tests
P12.38 Reference ERP Application
```

---

# 12.61 The First Real End-to-End ERP Slice

Rather than building hundreds of classes first, I'd implement this vertical slice:

```text
                  PRODUCT
                     │
                     ▼
                  ORDER
                     │
                     ▼
                FULFILLMENT
                     │
                     ▼
                 INVOICE
                     │
                     ▼
                RECEIVABLE
                     │
                     ▼
                  PAYMENT
                     │
                     ▼
                SETTLEMENT
                     │
                     ▼
               JOURNAL ENTRY
                     │
                     ▼
                  LEDGER
                     │
                     ▼
               FINANCIAL REPORT
```

And simultaneously:

```text
Order
  ↓
Business Event
  ↓
Accounting Posting Policy
  ↓
Journal
  ↓
Reporting Projection
```

This becomes our **reference ERP transaction**.

---

# 12.62 Why This Is the Right Next Step

This gives Syirkah a very important property:

```text
                  SAME CORE
                      │
       ┌──────────────┼───────────────┐
       │              │               │
    POS App       Ecommerce       ERP App
       │              │               │
       └──────────────┼───────────────┘
                      │
                 Same Domains
                      │
       ┌──────────────┼───────────────┐
       │              │               │
    Accounting     Reporting        Rules
       │              │               │
       └──────────────┼───────────────┘
                      │
                 Same Events
                      │
                 Same Platform
```

So a **small accounting SaaS**, a **coffee-shop POS**, a **retail chain**, a **3PL provider**, and a **full multi-company ERP** can all use the same Syirkah foundation and progressively consume more capabilities.

And that is the key distinction I would preserve going forward:

> **Syirkah should be capable of being an ERP, but it should never architecturally require every application to become an ERP.**

The next concrete implementation should therefore be **P12.01–P12.05: Accounting Core + Chart of Accounts + Fiscal Period + Journal + Posting Engine**, including the actual Java package/module structure and code using the Foundation APIs already present in your `syirkah-foundation`. That will give us the first genuinely reusable ERP engine instead of just another conceptual module.

/////////////

Exactly. I understand the correction now.

We **should not create another ERP/accounting system**. The existing framework already has a substantial accounting implementation, including:

- accounting domain and ledger/journal
- close-cycle and period locking
- reporting
- accounting dimensions
- tax
- budgeting/cost accounting
- multitenancy
- Islamic accounting capability
- Sharia contract classification
- Sharia compliance strategy
- Zakat calculation/reporting
- Sharia funds statement
- AAOIFI plugin/compliance infrastructure

For example, the current code already has `ShariaContractType` covering **Murabahah, Mudharabah, Musyarakah, Ijarah, Salam, Istishna, Wadiah, Qardh, and ZISWAF**, and the accounting domain already depends on the Islamic capability. syirkah-framework(1) syirkah-framework(1)

So the next phase should be more like:

> **Syirkah Accounting Extension / Islamic Business Model Layer**

rather than `ERP Core`.

### What is actually missing

I would extend the existing system around the **business meaning of Syirkah**, especially:

```text
Existing Accounting
        │
        ├── Ledger
        ├── Journal
        ├── AR/AP
        ├── Reporting
        ├── Closing
        └── Sharia Compliance
                │
                ▼
        SYIRKAH EXTENSION
                │
        ┌───────┼────────┐
        ▼       ▼        ▼
      Akad    Modal    Bagi Hasil
        │       │        │
        │       │        ├── Nisbah
        │       │        ├── Profit Basis
        │       │        ├── Profit Calculation
        │       │        ├── Loss Allocation
        │       │        └── Distribution
        │
        ├── Partner / Syarik
        ├── Capital Contribution
        ├── Capital Withdrawal
        ├── Ownership / Participation
        └── Akad Lifecycle
                │
                ▼
          Tutup Buku Syirkah
                │
        ├── calculate result
        ├── determine profit/loss
        ├── reconcile capital
        ├── calculate nisbah
        ├── allocate profit
        ├── recognize payable/distribution
        └── lock period
```

The important point is that **bagi hasil should not simply be another accounting percentage calculation**.

Your source material explicitly frames the Syirkah model around **profit rather than revenue**: profit is based on agreement, while loss is borne according to the applicable capital basis. The material also gives an example where net profit is calculated after direct, operational, and other expenses and then distributed according to a 65/35 nisbah. Hukum-muamalah Hukum-muamalah

So I would add these capabilities to the existing framework:

### 1. Syirkah / Akad domain

```text
syirkah-accounting-islamic
    ├── akad
    │   ├── SyirkahContract
    │   ├── SyirkahType
    │   ├── SyirkahParty
    │   ├── CapitalContribution
    │   └── ContractVersion
    │
    ├── nisbah
    │   ├── ProfitSharingRatio
    │   ├── ProfitSharingRule
    │   └── LossAllocationRule
    │
    └── distribution
        ├── ProfitDistribution
        ├── PartnerDistribution
        └── DistributionSettlement
```

This should **reuse the existing `ShariaContractType`**, not replace it.

---

### 2. Bagi hasil engine

Something along the lines of:

```java
ProfitSharingResult calculate(
    SyirkahContract contract,
    FinancialPeriod period,
    FinancialResult result
);
```

with a distinction between:

```text
Revenue
- Direct Cost
- Operating Expense
- Other Expense
----------------
Net Profit
```

Then:

```text
Net Profit
    × Partner A Nisbah
    = Partner A Profit Share

Net Profit
    × Partner B Nisbah
    = Partner B Profit Share
```

But importantly, the engine must also support:

```text
PROFIT
LOSS
BREAK_EVEN
```

and different Syirkah models.

For example:

```text
Mudharabah
    capital → shahibul maal
    management → mudharib
    profit → agreed nisbah
    ordinary business loss → capital provider
    negligence/breach → potentially manager liability

Musyarakah
    capital → multiple syarik
    profit → agreed nisbah
    loss → capital participation basis
```

The uploaded material specifically describes this distinction for Mudharabah, including the treatment of ordinary business loss versus loss caused by negligence/breach. 

---

### 3. Tutup buku needs a Syirkah-aware layer

This is probably the **most important extension**.

You already have `CloseCycle`, including tasks such as subledger cutoff, accruals, depreciation, FX revaluation, reconciliation, and GL locking. syirkah-framework(1)

Don't replace that.

Instead:

```text
Accounting CloseCycle
        │
        └── Syirkah Close Policy
                │
                ├── Financial reconciliation
                ├── Determine distributable profit
                ├── Determine loss
                ├── Apply akad rules
                ├── Calculate nisbah
                ├── Allocate partner results
                ├── Generate distribution obligations
                ├── Generate closing journal
                └── Lock period
```

So a normal company might do:

```text
MONTH END
    ↓
calculate P&L
    ↓
close period
```

while a Syirkah entity does:

```text
MONTH END
    ↓
calculate P&L
    ↓
validate akad
    ↓
determine profit basis
    ↓
calculate bagi hasil
    ↓
allocate partner profit
    ↓
post accounting entries
    ↓
generate Syirkah statement
    ↓
close period
```

And **tutup buku should be the event that crystallizes the distributable result**, not merely a database flag saying `period.closed = true`.

---

### 4. New Syirkah reports

The existing reporting framework already has Sharia reporting and a `ShariaFundsStatement`. syirkah-framework(1)

I'd extend it with:

```text
Syirkah Profit & Loss
Syirkah Capital Statement
Partner Capital Statement
Bagi Hasil Statement
Nisbah Calculation Statement
Profit Distribution Statement
Loss Allocation Statement
Akad Position Statement
Syirkah Closing Statement
```

For example:

```text
PERIODE: September 2026

Revenue                         500,000,000
Direct Cost                   (250,000,000)
Operating Expense             (100,000,000)
Other Expense                  (20,000,000)
                               ----------
NET PROFIT                     130,000,000

Nisbah:
  Partner A        60%
  Partner B        40%

Profit allocation:
  Partner A         78,000,000
  Partner B         52,000,000
```

The report should preserve **how the 130m was derived**, rather than merely storing the final 78m/52m.

That auditability is very important for Syirkah.

---

### 5. Akad versioning

This is another area where the existing generic rule/version architecture should be extended.

Suppose:

```text
Jan–Jun
A = 60%
B = 40%

Jul–Dec
A = 65%
B = 35%
```

We must never recalculate January using the July agreement.

Therefore:

```text
SyirkahContract
    version 1
       effectiveFrom = 2026-01-01
       effectiveUntil = 2026-06-30
       nisbah A = 60%
       nisbah B = 40%

    version 2
       effectiveFrom = 2026-07-01
       nisbah A = 65%
       nisbah B = 35%
```

And every distribution stores:

```text
contractVersion
profitCalculationVersion
period
calculationBasis
```

This gives us reproducibility.

---

### 6. Don't hard-code "Islamic law" into accounting

This is important architecturally.

I would **not** do:

```java
if (syirkah) {
    ...
}
```

all over Accounting.

Instead:

```text
Accounting
     │
     └── Policy / Rule SPI
              │
              ├── ConventionalAccountingPolicy
              ├── ShariaAccountingPolicy
              └── SyirkahProfitSharingPolicy
```

Your existing compliance/plugin architecture is already moving in this direction. The current Sharia compliance strategy can reject prohibited interest/riba accounts and require Sharia contract classification. syirkah-framework(1)

We extend that architecture rather than creating another parallel framework.

---

## So I would change our Phase 12 direction

Instead of:

> "Implement ERP accounting from scratch"

the next work should be:

### **P12 — Syirkah Accounting & Islamic Business Extensions**

```text
P12.01 Audit existing accounting
P12.02 Audit existing Islamic accounting
P12.03 Syirkah akad model
P12.04 Syirkah partner/syarik model
P12.05 Capital contribution model
P12.06 Nisbah model
P12.07 Profit basis engine
P12.08 Profit/loss allocation
P12.09 Bagi hasil calculation
P12.10 Distribution settlement
P12.11 Syirkah accounting mapping
P12.12 Syirkah-aware period close
P12.13 Tutup buku calculation
P12.14 Closing journal integration
P12.15 Partner capital reconciliation
P12.16 Syirkah reports
P12.17 Akad/version/effective dating
P12.18 Sharia rule extensions
P12.19 Zakat integration
P12.20 Audit/explainability
P12.21 Tests
```

And then later:

```text
P13  Syirkah Commercial Contracts
P14  Syirkah Marketplace/Ecosystem
P15  Islamic Procurement/Trade
P16  Islamic Financing
P17  ZISWAF
...
```

This is much closer to what I think you mean by **"apply Syirkah to what we already built."**

The existing framework is already quite mature; the opportunity now is to make its accounting **understand Syirkah as a business/legal model**, especially **akad → capital → business result → profit/loss → nisbah → bagi hasil → tutup buku**, rather than adding another generic ERP layer.

//////////////


Yes. We should now implement this as an **extension of the existing accounting + Islamic modules**, not create another accounting engine.

The first implementation tranche should establish the core Syirkah domain:

1. canonical Syirkah contract
2. parties / syarik
3. capital contributions
4. versioned nisbah
5. net-profit calculation
6. profit/loss allocation
7. immutable calculation result
8. tests
9. then integrate it into existing `CloseCycle`

The source material explicitly defines net profit as:

`Total Revenue - Direct Costs - Operating Costs - Other Costs`

and states that profit is distributed according to the agreed nisbah, while loss treatment depends on the Syirkah arrangement. Hukum-muamalah Hukum-muamalah

## 1. Add `SyirkahType`

**Path**

```text
finance/accounting/accounting-capabilities/
syirkah-accounting-islamic/src/main/java/
tech/kayys/syirkah/accounting/domain/islamic/SyirkahType.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Islamic partnership structures supported by the Syirkah accounting extension.
 */
public enum SyirkahType implements ValueObject {

    MUDHARABAH,

    MUSYARAKAH,

    INAN,

    ABDAN,

    WUJUH
}
```

I would keep this separate from `ShariaContractType` for now.

`ShariaContractType` answers:

> What Sharia accounting contract is this transaction?

`SyirkahType` answers:

> What partnership structure governs this business?

That avoids immediately breaking existing code.

---

# 2. Add `SyirkahPartyRole`

```text
.../domain/islamic/SyirkahPartyRole.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Role performed by a party in a Syirkah partnership.
 */
public enum SyirkahPartyRole implements ValueObject {

    CAPITAL_PROVIDER,

    MANAGING_PARTNER,

    CAPITAL_AND_MANAGING_PARTNER,

    WORKING_PARTNER
}
```

This is important because the source material distinguishes capital contribution from work contribution. For example, Mudharabah can have one party contributing capital and another contributing work. Hukum-muamalah

---

# 3. Add `SyirkahParty`

```text
.../domain/islamic/SyirkahParty.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.util.Objects;

/**
 * A party participating in a Syirkah contract.
 */
public record SyirkahParty(
        String partyId,
        String name,
        SyirkahPartyRole role
) {

    public SyirkahParty {
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(role, "role must not be null");

        if (partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
```

---

# 4. Add `ProfitSharingRatio`

This should be a proper value object rather than allowing arbitrary `BigDecimal` percentages everywhere.

```text
.../domain/islamic/ProfitSharingRatio.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Agreed profit-sharing ratio for one Syirkah party.
 *
 * The value is represented as a percentage from 0 to 100.
 */
public record ProfitSharingRatio(BigDecimal percentage) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    public ProfitSharingRatio {
        Objects.requireNonNull(percentage, "percentage must not be null");

        if (percentage.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("percentage must not be negative");
        }

        if (percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("percentage must not exceed 100");
        }

        percentage = percentage.setScale(2, RoundingMode.HALF_UP);
    }

    public static ProfitSharingRatio of(BigDecimal percentage) {
        return new ProfitSharingRatio(percentage);
    }

    public static ProfitSharingRatio of(double percentage) {
        return of(BigDecimal.valueOf(percentage));
    }

    public BigDecimal apply(BigDecimal profit) {
        Objects.requireNonNull(profit, "profit must not be null");

        return profit
                .multiply(percentage)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
```

---

# 5. Add `CapitalContribution`

```text
.../domain/islamic/CapitalContribution.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Capital contribution made by a Syirkah party.
 */
public record CapitalContribution(
        String partyId,
        BigDecimal amount,
        LocalDate contributionDate
) {

    public CapitalContribution {
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(contributionDate, "contributionDate must not be null");

        if (partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("capital contribution must be greater than zero");
        }
    }
}
```

---

# 6. Add `SyirkahPartyAgreement`

This connects a party to both capital and profit sharing.

```text
.../domain/islamic/SyirkahPartyAgreement.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contractual participation of a party in a Syirkah agreement.
 */
public record SyirkahPartyAgreement(
        SyirkahParty party,
        BigDecimal capitalContribution,
        ProfitSharingRatio profitSharingRatio
) {

    public SyirkahPartyAgreement {
        Objects.requireNonNull(party, "party must not be null");
        Objects.requireNonNull(capitalContribution, "capitalContribution must not be null");
        Objects.requireNonNull(profitSharingRatio, "profitSharingRatio must not be null");

        if (capitalContribution.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "capitalContribution must not be negative"
            );
        }
    }
}
```

---

# 7. Add versioned `SyirkahContract`

This is one of the most important pieces.

The source material describes situations where the Syirkah must be closed before a new party, capital, or working contribution enters, followed by a new agreement and new nisbah. Hukum-muamalah

Therefore, don't mutate historical nisbah.

```text
.../domain/islamic/SyirkahContract.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Versioned Syirkah agreement.
 *
 * Historical versions are preserved so that past profit distributions
 * can always be reproduced using the agreement that was effective
 * during the relevant period.
 */
public final class SyirkahContract {

    private final String contractId;
    private final SyirkahType type;
    private final List<Version> versions = new ArrayList<>();

    public SyirkahContract(
            String contractId,
            SyirkahType type
    ) {
        this.contractId = Objects.requireNonNull(contractId, "contractId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");

        if (contractId.isBlank()) {
            throw new IllegalArgumentException("contractId must not be blank");
        }
    }

    public void addVersion(
            int versionNumber,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            List<SyirkahPartyAgreement> parties
    ) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        Objects.requireNonNull(parties, "parties must not be null");

        if (parties.isEmpty()) {
            throw new IllegalArgumentException("Syirkah contract must contain at least one party");
        }

        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil must not be before effectiveFrom"
            );
        }

        if (versionNumber <= 0) {
            throw new IllegalArgumentException(
                    "versionNumber must be greater than zero"
            );
        }

        if (versions.stream().anyMatch(v -> v.versionNumber() == versionNumber)) {
            throw new IllegalArgumentException(
                    "Contract version already exists: " + versionNumber
            );
        }

        validateNisbahTotal(parties);

        versions.add(new Version(
                versionNumber,
                effectiveFrom,
                effectiveUntil,
                List.copyOf(parties)
        ));
    }

    public Version effectiveVersion(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");

        return versions.stream()
                .filter(version ->
                        !date.isBefore(version.effectiveFrom())
                                && (
                                version.effectiveUntil() == null
                                        || !date.isAfter(version.effectiveUntil())
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No Syirkah contract version is effective on " + date
                        )
                );
    }

    private void validateNisbahTotal(
            List<SyirkahPartyAgreement> parties
    ) {
        var total = parties.stream()
                .map(p -> p.profitSharingRatio().percentage())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        if (total.compareTo(new java.math.BigDecimal("100.00")) != 0) {
            throw new IllegalArgumentException(
                    "Total profit-sharing ratio must equal 100%, but was " + total
            );
        }
    }

    public String contractId() {
        return contractId;
    }

    public SyirkahType type() {
        return type;
    }

    public List<Version> versions() {
        return Collections.unmodifiableList(versions);
    }

    public record Version(
            int versionNumber,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            List<SyirkahPartyAgreement> parties
    ) {
        public Version {
            Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
            Objects.requireNonNull(parties, "parties must not be null");

            if (parties.isEmpty()) {
                throw new IllegalArgumentException(
                        "Contract version must contain at least one party"
                );
            }

            parties = List.copyOf(parties);
        }
    }
}
```

This gives us:

```text
Contract
 ├── Version 1
 │    ├── effectiveFrom
 │    ├── effectiveUntil
 │    ├── Ahmad 40%
 │    └── Budi 60%
 │
 └── Version 2
      ├── effectiveFrom
      ├── effectiveUntil
      ├── Ahmad 38%
      └── Budi 62%
```

Historical calculations therefore don't accidentally use today's agreement.

---

# 8. Add `FinancialResult`

Now we explicitly model the profit basis instead of letting the distribution engine operate directly on revenue.

```text
.../domain/islamic/FinancialResult.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Financial result used as the basis for Syirkah profit and loss calculation.
 */
public record FinancialResult(
        BigDecimal revenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts
) {

    public FinancialResult {
        revenue = requireNonNegative(revenue, "revenue");
        directCosts = requireNonNegative(directCosts, "directCosts");
        operatingCosts = requireNonNegative(operatingCosts, "operatingCosts");
        otherCosts = requireNonNegative(otherCosts, "otherCosts");
    }

    public BigDecimal netProfit() {
        return revenue
                .subtract(directCosts)
                .subtract(operatingCosts)
                .subtract(otherCosts);
    }

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String field
    ) {
        Objects.requireNonNull(value, field + " must not be null");

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    field + " must not be negative"
            );
        }

        return value;
    }
}
```

This directly represents the formula in the source material:

```text
Net Profit =
    Total Revenue
    - Total Direct Costs
    - Total Operating Costs
    - Total Other Costs
```

---

# 9. Add `ProfitSharingResult`

```text
.../domain/islamic/ProfitSharingResult.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ProfitSharingResult(
        String contractId,
        int contractVersion,
        BigDecimal revenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts,
        BigDecimal netResult,
        ResultType resultType,
        List<PartyAllocation> allocations
) {

    public ProfitSharingResult {
        Objects.requireNonNull(contractId, "contractId must not be null");
        Objects.requireNonNull(revenue, "revenue must not be null");
        Objects.requireNonNull(directCosts, "directCosts must not be null");
        Objects.requireNonNull(operatingCosts, "operatingCosts must not be null");
        Objects.requireNonNull(otherCosts, "otherCosts must not be null");
        Objects.requireNonNull(netResult, "netResult must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(allocations, "allocations must not be null");

        allocations = List.copyOf(allocations);
    }

    public enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }

    public record PartyAllocation(
            String partyId,
            BigDecimal amount
    ) {
        public PartyAllocation {
            Objects.requireNonNull(partyId, "partyId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }
}
```

---

# 10. Add the actual `SyirkahProfitSharingEngine`

This is the first real business engine.

```text
.../domain/islamic/SyirkahProfitSharingEngine.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calculates Syirkah profit sharing from the net business result.
 *
 * Profit is distributed according to the agreed nisbah.
 *
 * Loss allocation is intentionally handled separately from profit
 * distribution because the applicable rule depends on the Syirkah type
 * and the circumstances of the loss.
 */
public final class SyirkahProfitSharingEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public ProfitSharingResult calculate(
            SyirkahContract contract,
            LocalDate calculationDate,
            FinancialResult financialResult
    ) {
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(calculationDate, "calculationDate must not be null");
        Objects.requireNonNull(financialResult, "financialResult must not be null");

        SyirkahContract.Version version =
                contract.effectiveVersion(calculationDate);

        BigDecimal netResult = financialResult.netProfit();

        ProfitSharingResult.ResultType resultType;

        if (netResult.compareTo(ZERO) > 0) {
            resultType = ProfitSharingResult.ResultType.PROFIT;
        } else if (netResult.compareTo(ZERO) < 0) {
            resultType = ProfitSharingResult.ResultType.LOSS;
        } else {
            resultType = ProfitSharingResult.ResultType.BREAK_EVEN;
        }

        List<ProfitSharingResult.PartyAllocation> allocations =
                calculateProfitAllocations(version, netResult);

        return new ProfitSharingResult(
                contract.contractId(),
                version.versionNumber(),
                financialResult.revenue(),
                financialResult.directCosts(),
                financialResult.operatingCosts(),
                financialResult.otherCosts(),
                netResult,
                resultType,
                allocations
        );
    }

    private List<ProfitSharingResult.PartyAllocation> calculateProfitAllocations(
            SyirkahContract.Version version,
            BigDecimal netResult
    ) {
        if (netResult.compareTo(ZERO) <= 0) {
            return version.parties().stream()
                    .map(party ->
                            new ProfitSharingResult.PartyAllocation(
                                    party.party().partyId(),
                                    ZERO
                            )
                    )
                    .toList();
        }

        List<ProfitSharingResult.PartyAllocation> allocations =
                new ArrayList<>();

        BigDecimal allocated = ZERO;

        for (int i = 0; i < version.parties().size(); i++) {
            SyirkahPartyAgreement party = version.parties().get(i);

            BigDecimal amount;

            if (i == version.parties().size() - 1) {
                // Assign the remainder to the final party so rounding
                // can never create an unallocated amount.
                amount = netResult.subtract(allocated);
            } else {
                amount = party.profitSharingRatio()
                        .apply(netResult)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            allocated = allocated.add(amount);

            allocations.add(
                    new ProfitSharingResult.PartyAllocation(
                            party.party().partyId(),
                            amount
                    )
            );
        }

        return allocations;
    }
}
```

Notice an important design decision:

**The engine does not distribute losses using the profit nisbah.**

That is intentional.

The source explicitly distinguishes profit and loss. In the described Syirkah Inan case, loss follows capital proportion, while the profit follows agreement. Hukum-muamalah

For Mudharabah, the source further distinguishes ordinary capital loss from loss caused by negligence or violation by the manager. Hukum-muamalah

So we need a separate loss policy.

---

# 11. Add `LossAllocationPolicy`

```text
.../domain/islamic/LossAllocationPolicy.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.List;

public interface LossAllocationPolicy {

    List<ProfitSharingResult.PartyAllocation> allocate(
            SyirkahContract.Version contractVersion,
            BigDecimal loss
    );
}
```

---

# 12. Implement capital-proportion loss

```text
.../domain/islamic/CapitalProportionLossAllocationPolicy.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Allocates ordinary business losses according to capital contribution.
 *
 * This policy is appropriate for partnership structures where the source
 * rules require loss allocation according to capital proportion.
 */
public final class CapitalProportionLossAllocationPolicy
        implements LossAllocationPolicy {

    @Override
    public List<ProfitSharingResult.PartyAllocation> allocate(
            SyirkahContract.Version contractVersion,
            BigDecimal loss
    ) {
        Objects.requireNonNull(contractVersion, "contractVersion must not be null");
        Objects.requireNonNull(loss, "loss must not be null");

        if (loss.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "loss must not be negative"
            );
        }

        BigDecimal totalCapital = contractVersion.parties().stream()
                .map(SyirkahPartyAgreement::capitalContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Capital-proportion loss allocation requires positive total capital"
            );
        }

        List<ProfitSharingResult.PartyAllocation> result =
                new ArrayList<>();

        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < contractVersion.parties().size(); i++) {
            SyirkahPartyAgreement party =
                    contractVersion.parties().get(i);

            BigDecimal allocation;

            if (i == contractVersion.parties().size() - 1) {
                allocation = loss.subtract(allocated);
            } else {
                allocation = loss
                        .multiply(party.capitalContribution())
                        .divide(totalCapital, 2, RoundingMode.HALF_UP);
            }

            allocated = allocated.add(allocation);

            result.add(
                    new ProfitSharingResult.PartyAllocation(
                            party.party().partyId(),
                            allocation
                    )
            );
        }

        return List.copyOf(result);
    }
}
```

---

# 13. Add the Syirkah calculation facade

This prevents application code from knowing which loss policy to use.

```text
.../domain/islamic/SyirkahResultCalculator.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Coordinates Syirkah result calculation and the applicable loss policy.
 */
public final class SyirkahResultCalculator {

    private final SyirkahProfitSharingEngine profitSharingEngine;
    private final LossAllocationPolicy lossAllocationPolicy;

    public SyirkahResultCalculator(
            SyirkahProfitSharingEngine profitSharingEngine,
            LossAllocationPolicy lossAllocationPolicy
    ) {
        this.profitSharingEngine =
                Objects.requireNonNull(profitSharingEngine);

        this.lossAllocationPolicy =
                Objects.requireNonNull(lossAllocationPolicy);
    }

    public SyirkahCalculation calculate(
            SyirkahContract contract,
            LocalDate calculationDate,
            FinancialResult financialResult
    ) {
        ProfitSharingResult profitSharing =
                profitSharingEngine.calculate(
                        contract,
                        calculationDate,
                        financialResult
                );

        if (profitSharing.resultType()
                == ProfitSharingResult.ResultType.LOSS) {

            var version = contract.effectiveVersion(calculationDate);

            var lossAllocations =
                    lossAllocationPolicy.allocate(
                            version,
                            profitSharing.netResult().abs()
                    );

            return new SyirkahCalculation(
                    profitSharing,
                    lossAllocations
            );
        }

        return new SyirkahCalculation(
                profitSharing,
                java.util.List.of()
        );
    }

    public record SyirkahCalculation(
            ProfitSharingResult result,
            java.util.List<ProfitSharingResult.PartyAllocation> lossAllocations
    ) {
    }
}
```

---

# 14. Unit tests

Create:

```text
finance/accounting/accounting-capabilities/
syirkah-accounting-islamic/src/test/java/
tech/kayys/syirkah/accounting/domain/islamic/
SyirkahProfitSharingEngineTest.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyirkahProfitSharingEngineTest {

    @Test
    void shouldCalculateProfitUsingNetProfitAndNisbah() {

        SyirkahParty ahmad = new SyirkahParty(
                "AHMAD",
                "Ahmad",
                SyirkahPartyRole.CAPITAL_PROVIDER
        );

        SyirkahParty budi = new SyirkahParty(
                "BUDI",
                "Budi",
                SyirkahPartyRole.MANAGING_PARTNER
        );

        SyirkahContract contract =
                new SyirkahContract(
                        "SYIRKAH-001",
                        SyirkahType.MUDHARABAH
                );

        contract.addVersion(
                1,
                LocalDate.of(2026, 1, 1),
                null,
                List.of(
                        new SyirkahPartyAgreement(
                                ahmad,
                                new BigDecimal("100000000"),
                                ProfitSharingRatio.of(40)
                        ),
                        new SyirkahPartyAgreement(
                                budi,
                                BigDecimal.ZERO,
                                ProfitSharingRatio.of(60)
                        )
                )
        );

        FinancialResult result =
                new FinancialResult(
                        new BigDecimal("25000000"),
                        new BigDecimal("15000000"),
                        new BigDecimal("3000000"),
                        new BigDecimal("2000000")
                );

        SyirkahProfitSharingEngine engine =
                new SyirkahProfitSharingEngine();

        ProfitSharingResult sharing =
                engine.calculate(
                        contract,
                        LocalDate.of(2026, 1, 31),
                        result
                );

        assertEquals(
                new BigDecimal("5000000"),
                sharing.netResult()
        );

        assertEquals(
                ProfitSharingResult.ResultType.PROFIT,
                sharing.resultType()
        );

        assertEquals(
                new BigDecimal("2000000"),
                sharing.allocations().get(0).amount()
        );

        assertEquals(
                new BigDecimal("3000000"),
                sharing.allocations().get(1).amount()
        );
    }
}
```

This represents the example from the source material:

```text
Revenue       25,000,000
Total Cost    20,000,000
Profit         5,000,000

Ahmad 40% = 2,000,000
Umar  60% = 3,000,000
```

The source explicitly gives this calculation pattern. 

---

# 15. Test the critical "revenue sharing is not allowed" behavior

```java
@Test
void shouldUseNetProfitInsteadOfRevenueForProfitSharing() {

    SyirkahParty first = new SyirkahParty(
            "P1",
            "Partner One",
            SyirkahPartyRole.CAPITAL_PROVIDER
    );

    SyirkahParty second = new SyirkahParty(
            "P2",
            "Partner Two",
            SyirkahPartyRole.MANAGING_PARTNER
    );

    SyirkahContract contract =
            new SyirkahContract(
                    "SYIRKAH-002",
                    SyirkahType.MUDHARABAH
            );

    contract.addVersion(
            1,
            LocalDate.of(2026, 1, 1),
            null,
            List.of(
                    new SyirkahPartyAgreement(
                            first,
                            new BigDecimal("100000000"),
                            ProfitSharingRatio.of(40)
                    ),
                    new SyirkahPartyAgreement(
                            second,
                            BigDecimal.ZERO,
                            ProfitSharingRatio.of(60)
                    )
            )
    );

    FinancialResult financialResult =
            new FinancialResult(
                    new BigDecimal("100000000"),
                    new BigDecimal("70000000"),
                    new BigDecimal("10000000"),
                    new BigDecimal("5000000")
            );

    SyirkahProfitSharingEngine engine =
            new SyirkahProfitSharingEngine();

    ProfitSharingResult result =
            engine.calculate(
                    contract,
                    LocalDate.of(2026, 1, 31),
                    financialResult
            );

    // Net profit = 100m - 70m - 10m - 5m = 15m
    assertEquals(
            new BigDecimal("15000000"),
            result.netResult()
    );

    // 40% of 15m, not 40% of 100m revenue.
    assertEquals(
            new BigDecimal("6000000"),
            result.allocations().get(0).amount()
    );

    assertEquals(
            new BigDecimal("9000000"),
            result.allocations().get(1).amount()
    );
}
```

This is a key guardrail because the supplied source explicitly says the basis should be **profit sharing, not revenue sharing**. Hukum-muamalah

---

# 16. Test capital-proportion loss

```java
@Test
void shouldAllocateLossAccordingToCapitalProportion() {

    SyirkahParty ahmad = new SyirkahParty(
            "AHMAD",
            "Ahmad",
            SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER
    );

    SyirkahParty budi = new SyirkahParty(
            "BUDI",
            "Budi",
            SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER
    );

    SyirkahContract contract =
            new SyirkahContract(
                    "SYIRKAH-003",
                    SyirkahType.INAN
            );

    contract.addVersion(
            1,
            LocalDate.of(2026, 1, 1),
            null,
            List.of(
                    new SyirkahPartyAgreement(
                            ahmad,
                            new BigDecimal("60000000"),
                            ProfitSharingRatio.of(50)
                    ),
                    new SyirkahPartyAgreement(
                            budi,
                            new BigDecimal("40000000"),
                            ProfitSharingRatio.of(50)
                    )
            )
    );

    LossAllocationPolicy policy =
            new CapitalProportionLossAllocationPolicy();

    var allocations =
            policy.allocate(
                    contract.effectiveVersion(
                            LocalDate.of(2026, 1, 31)
                    ),
                    new BigDecimal("10000000")
            );

    assertEquals(
            new BigDecimal("6000000"),
            allocations.get(0).amount()
    );

    assertEquals(
            new BigDecimal("4000000"),
            allocations.get(1).amount()
    );
}
```

That directly implements the source's Syirkah Inan rule that losses follow capital proportion while profit follows the agreed arrangement. Hukum-muamalah

---

## 17. Next: integrate with existing `CloseCycle`

Now we have the calculation domain. **We should not replace `CloseCycle`.**

The existing implementation already has:

```text
TASK_SUBLEDGER_CUTOFF
TASK_ACCRUALS
TASK_DEPRECIATION
TASK_FX_REVALUATION
TASK_RECONCILIATION
TASK_LOCK
```

and lifecycle:

```text
OPEN
  ↓
VALIDATING
  ↓
APPROVED
  ↓
LOCKED
```

So the next modification should extend it with:

```text
TASK_SYIRKAH_RESULT
TASK_SYIRKAH_ALLOCATION
TASK_SYIRKAH_DISTRIBUTION
```

before:

```text
TASK_LOCK
```

The resulting close becomes:

```text
OPEN
 │
 ├── subledger cut-off
 ├── accruals
 ├── depreciation
 ├── FX revaluation
 ├── reconciliation
 │
 ├── Syirkah financial result
 │       │
 │       ├── revenue
 │       ├── direct costs
 │       ├── operating costs
 │       ├── other costs
 │       └── net result
 │
 ├── Syirkah profit/loss allocation
 │       │
 │       ├── validate effective akad
 │       ├── resolve contract version
 │       ├── apply nisbah
 │       └── apply loss policy
 │
 ├── Syirkah distribution
 │
 └── GL lock
```

This is the important architectural point: **`tutup buku` becomes a real Syirkah calculation phase inside the existing accounting close, rather than merely setting the period to closed.**

Also, the source explicitly says that when a new partner/capital/work contribution enters, the existing Syirkah should be closed first so the existing parties' result can be determined before a new agreement is established. Hukum-muamalah

### One refactoring I would do immediately

The existing framework currently has both:

```java
ShariaContractType
```

and:

```java
IslamicContractType
```

with overlapping concepts such as:

```text
MUDHARABAH / MUDARABA
MUSYARAKAH / MUSHARAKA
MURABAHAH / MURABAHA
```

I would **not add a third contract enum**. The new code above deliberately uses `SyirkahType` only for the partnership structure. The next step should cleanly establish:

```text
ShariaContractType
        │
        │ accounting contract classification
        ▼
MUDHARABAH
MUSYARAKAH
MURABAHAH
IJARAH
...

SyirkahType
        │
        │ partnership structure
        ▼
MUDHARABAH
MUSYARAKAH
INAN
ABDAN
WUJUH
```

Then we can make the existing `JournalEntry` use the canonical `ShariaContractType.MUDHARABAH/MUSYARAKAH`, while the Syirkah aggregate carries the more detailed partnership semantics.

**Next implementation step: I would modify the existing `CloseCycle` and `CloseTaskKind`, then add `SyirkahCloseService` that produces the close result and distribution journal through the existing `JournalEntry` infrastructure.**

/////////////


The next change is to integrate Syirkah into the existing financial close lifecycle. Your framework already has `CloseCycle`, mandatory close tasks, approval checks, period locking, `GeneralLedgerService`, and `JournalEntry`; we should extend these rather than create parallel infrastructure.&#x20;

syirkah-framework(1).txt

syirkah-framework(1).txt



I’ll keep all code, identifiers, comments, and API names in English. This tranche adds the Syirkah close tasks and a service boundary for calculating and recording the result. One important accounting safeguard: calculating a distribution must not itself post a journal entry. Calculation, review, approval, posting, and period locking should remain distinguishable steps.

## 1. Extend `CloseTaskKind`

File:

`finance/accounting/syirkah-accounting-domain/src/main/java/tech/kayys/syirkah/accounting/domain/close/CloseTaskKind.java`

Replace the enum with:

```

package tech.kayys.syirkah.accounting.domain.close;

/**
 * Classification of tasks performed during a financial period close.
 */
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,

    /**
     * Calculates the finalized financial result used for Syirkah settlement.
     */
    SYIRKAH_RESULT,

    /**
     * Allocates profit or loss according to the effective agreement and rules.
     */
    SYIRKAH_ALLOCATION,

    /**
     * Prepares and reconciles partner distribution obligations.
     */
    SYIRKAH_DISTRIBUTION,

    /**
     * Posts approved closing entries and locks the general ledger.
     */
    LOCK
}

```

## 2. Add Syirkah tasks to `CloseCycle`

Keep the existing lifecycle and task-completion checks. Update only the `defaultTasks` list inside `CloseCycle.open(...)`.

```

List<CloseTask> defaultTasks = List.of(
        new CloseTask(
                "TASK_SUBLEDGER_CUTOFF",
                "Subledger Cut-off",
                CloseTaskKind.SUBLEDGER_CUTOFF,
                true
        ),
        new CloseTask(
                "TASK_ACCRUALS",
                "Post Accruals",
                CloseTaskKind.ACCRUALS,
                true
        ),
        new CloseTask(
                "TASK_DEPRECIATION",
                "Fixed Assets Depreciation",
                CloseTaskKind.DEPRECIATION,
                true
        ),
        new CloseTask(
                "TASK_FX_REVALUATION",
                "Multi-Currency FX Revaluation",
                CloseTaskKind.FX_REVALUATION,
                false
        ),
        new CloseTask(
                "TASK_RECONCILIATION",
                "Subledger and Bank Reconciliation",
                CloseTaskKind.RECONCILIATION,
                true
        ),
        new CloseTask(
                "TASK_SYIRKAH_RESULT",
                "Calculate Syirkah Financial Result",
                CloseTaskKind.SYIRKAH_RESULT,
                true
        ),
        new CloseTask(
                "TASK_SYIRKAH_ALLOCATION",
                "Allocate Syirkah Profit or Loss",
                CloseTaskKind.SYIRKAH_ALLOCATION,
                true
        ),
        new CloseTask(
                "TASK_SYIRKAH_DISTRIBUTION",
                "Prepare Partner Distribution",
                CloseTaskKind.SYIRKAH_DISTRIBUTION,
                true
        ),
        new CloseTask(
                "TASK_LOCK",
                "General Ledger Hard Lock",
                CloseTaskKind.LOCK,
                true
        )
);

return new CloseCycle(
        id,
        tenantId,
        ledgerId,
        fiscalPeriodId,
        defaultTasks
);

```

Why mandatory? For a close cycle configured for Syirkah accounting, the result and allocation must be reviewed before the ledger is locked. If your platform also supports ordinary, non-Syirkah ledgers, we should make the task set policy-driven rather than forcing every tenant to run Syirkah tasks.

## 3. Make Syirkah closing opt-in

Because Syirkah is a capability, ordinary accounting ledgers should not be forced to run Syirkah-specific tasks.

In `CloseCycle.java`, preserve the existing `open(...)` method and delegate to an overload:

```

public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId
) {
    return open(id, tenantId, ledgerId, fiscalPeriodId, false);
}

public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        boolean syirkahEnabled
) {
    List<CloseTask> tasks = new ArrayList<>(List.of(
            new CloseTask(
                    "TASK_SUBLEDGER_CUTOFF",
                    "Subledger Cut-off",
                    CloseTaskKind.SUBLEDGER_CUTOFF,
                    true
            ),
            new CloseTask(
                    "TASK_ACCRUALS",
                    "Post Accruals",
                    CloseTaskKind.ACCRUALS,
                    true
            ),
            new CloseTask(
                    "TASK_DEPRECIATION",
                    "Fixed Assets Depreciation",
                    CloseTaskKind.DEPRECIATION,
                    true
            ),
            new CloseTask(
                    "TASK_FX_REVALUATION",
                    "Multi-Currency FX Revaluation",
                    CloseTaskKind.FX_REVALUATION,
                    false
            ),
            new CloseTask(
                    "TASK_RECONCILIATION",
                    "Subledger and Bank Reconciliation",
                    CloseTaskKind.RECONCILIATION,
                    true
            )
    ));

    if (syirkahEnabled) {
        tasks.add(new CloseTask(
                "TASK_SYIRKAH_RESULT",
                "Calculate Syirkah Financial Result",
                CloseTaskKind.SYIRKAH_RESULT,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_ALLOCATION",
                "Allocate Syirkah Profit or Loss",
                CloseTaskKind.SYIRKAH_ALLOCATION,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_DISTRIBUTION",
                "Prepare Partner Distribution",
                CloseTaskKind.SYIRKAH_DISTRIBUTION,
                true
        ));
    }

    tasks.add(new CloseTask(
            "TASK_LOCK",
            "General Ledger Hard Lock",
            CloseTaskKind.LOCK,
            true
    ));

    return new CloseCycle(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            tasks
    );
}

```

This keeps existing callers compatible while allowing a Syirkah-enabled ledger to opt into the additional close steps.

## 4. Add the application service boundary

The close workflow needs a defined contract for the calculation work. It should not embed Sharia rules or journal-writing logic inside `CloseCycle`.

Create:

`finance/accounting/syirkah-accounting-application/src/main/java/tech/kayys/syirkah/accounting/application/close/SyirkahCloseProcessor.java`

```

package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Application boundary for Syirkah-specific close calculations.
 *
 * Implementations coordinate financial-result retrieval, agreement
 * validation, profit/loss allocation, and distribution preparation.
 * They must not lock the fiscal period.
 */
public interface SyirkahCloseProcessor {

    Uni<CloseCalculation> calculateResult(CloseRequest request);

    Uni<CloseCalculation> allocateResult(CloseRequest request);

    Uni<CloseCalculation> prepareDistribution(CloseRequest request);

    record CloseRequest(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String requestedBy
    ) {
        public CloseRequest {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(ledgerId, "ledgerId must not be null");
            Objects.requireNonNull(fiscalPeriodId, "fiscalPeriodId must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
            Objects.requireNonNull(requestedBy, "requestedBy must not be null");

            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException(
                        "periodEnd must not be before periodStart"
                );
            }
        }
    }

    record CloseCalculation(
            String calculationId,
            String contractId,
            int contractVersion,
            BigDecimal netResult,
            ResultType resultType,
            List<PartnerAllocation> allocations,
            String calculationReference
    ) {
        public CloseCalculation {
            Objects.requireNonNull(calculationId, "calculationId must not be null");
            Objects.requireNonNull(contractId, "contractId must not be null");
            Objects.requireNonNull(netResult, "netResult must not be null");
            Objects.requireNonNull(resultType, "resultType must not be null");
            Objects.requireNonNull(allocations, "allocations must not be null");

            allocations = List.copyOf(allocations);
        }
    }

    enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }

    record PartnerAllocation(
            String partyId,
            BigDecimal amount,
            String allocationBasis
    ) {
        public PartnerAllocation {
            Objects.requireNonNull(partyId, "partyId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(allocationBasis, "allocationBasis must not be null");
        }
    }
}

```

The boundary deliberately returns a calculation reference and the contract version. That gives the closing process a way to trace which agreement and calculation produced each allocation.

## 5. Add an orchestrator for the three Syirkah tasks

Create `SyirkahCloseTaskService.java` in the same `application/close` package.

```

package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseTask;

import java.util.Objects;

/**
 * Executes Syirkah close steps and records their completion on the close cycle.
 */
public final class SyirkahCloseTaskService {

    private final SyirkahCloseProcessor processor;

    public SyirkahCloseTaskService(SyirkahCloseProcessor processor) {
        this.processor = Objects.requireNonNull(
                processor,
                "processor must not be null"
        );
    }

    public Uni<SyirkahCloseProcessor.CloseCalculation> calculateResult(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request
    ) {
        return execute(
                cycle,
                "TASK_SYIRKAH_RESULT",
                request,
                processor::calculateResult
        );
    }

    public Uni<SyirkahCloseProcessor.CloseCalculation> allocateResult(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request
    ) {
        return execute(
                cycle,
                "TASK_SYIRKAH_ALLOCATION",
                request,
                processor::allocateResult
        );
    }

    public Uni<SyirkahCloseProcessor.CloseCalculation> prepareDistribution(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request
    ) {
        return execute(
                cycle,
                "TASK_SYIRKAH_DISTRIBUTION",
                request,
                processor::prepareDistribution
        );
    }

    private Uni<SyirkahCloseProcessor.CloseCalculation> execute(
            CloseCycle cycle,
            String taskCode,
            SyirkahCloseProcessor.CloseRequest request,
            java.util.function.Function<
                    SyirkahCloseProcessor.CloseRequest,
                    Uni<SyirkahCloseProcessor.CloseCalculation>
                    > operation
    ) {
        Objects.requireNonNull(cycle, "cycle must not be null");
        Objects.requireNonNull(request, "request must not be null");

        CloseTask task = cycle.findTask(taskCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Required close task is missing: " + taskCode
                ));

        if (cycle.status()
                != tech.kayys.syirkah.accounting.domain.close.CloseStatus.OPEN) {
            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "Syirkah calculations must execute before close validation"
                    )
            );
        }

        if (task.status()
                == tech.kayys.syirkah.accounting.domain.close.CloseTaskStatus.COMPLETED) {
            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "Close task is already completed: " + taskCode
                    )
            );
        }

        task.start();

        return operation.apply(request)
                .invoke(result -> task.complete(request.requestedBy()))
                .onFailure().invoke(task::block);
    }
}

```

This follows the existing framework's `CloseCycle` and `CloseTask` lifecycle.&#x20;

syirkah-framework(1).txt



Important implementation note: `CloseCycle` is currently held in an in-memory map by `FinancialCloseService`. This is suitable for a prototype, but production closing needs durable persistence and transactional coordination. Otherwise a restart could lose the close state, and a calculation might be completed without its result being durably recorded.

## 6. Add close workflow tests

The immediate tests should verify that:

- a normal close cycle does not contain Syirkah tasks;
- a Syirkah-enabled cycle contains all three mandatory Syirkah tasks;
- approval fails while mandatory tasks remain incomplete;
- a failed calculation does not mark its task completed;
- an already-completed task cannot be executed a second time;
- the ledger cannot be locked before approval.

Your existing `CloseCycle.approve(...)` already checks mandatory task completion, so these tests protect the intended behavior without replacing the lifecycle.

## What comes next

The next implementation tranche should connect `SyirkahCloseProcessor` to real data:

1. Read the existing fiscal period and ledger financial results.
2. Resolve the effective Syirkah contract version for the period.
3. Calculate net profit using the configured accounting basis.
4. Apply the appropriate profit-sharing or loss-allocation policy.
5. Persist the calculation and its explanation.
6. Prepare the partner distribution obligations.
7. After review and approval, generate and post the journal through the existing general-ledger infrastructure.

I would not yet post distribution journals automatically from this task service. The current `GeneralLedgerService` already validates journal entries and fiscal-period status, so we should use that existing path once the account mapping, liability treatment, approval requirements, and idempotency behavior are defined.&#x20;

The next concrete code change is the implementation of the processor against your existing ledger/reporting and journal APIs, including an explicit mapping from each partner allocation to the correct accounting accounts.

///////////

Good. The next step should be slightly more disciplined than the previous draft: the existing `FinancialReportingService` is **not yet safe as the authoritative period-result provider** because its current `generateIncomeStatement(...)` reads current account balances and does not actually filter transactions by `ReportPeriod`. syirkah-framework(1)

So we should **not silently use it to calculate a historical Syirkah close**.

Instead, introduce explicit ports now. Then we can connect them to the real ledger/reporting implementation when that implementation exposes period-filtered balances.

## 18. Add the financial-result provider port

Create:

```text
finance/accounting/syirkah-accounting-application/src/main/java/
tech/kayys/syirkah/accounting/application/close/
SyirkahFinancialResultProvider.java
```

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.FinancialResult;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Provides the financial result for a specific accounting period.
 *
 * Implementations must use period-aware accounting data.
 * They must not calculate a historical period from current account balances.
 */
public interface SyirkahFinancialResultProvider {

    Uni<FinancialResult> calculate(
            String tenantId,
            String ledgerId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    record Request(
            String tenantId,
            String ledgerId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        public Request {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(ledgerId, "ledgerId must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");

            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException(
                        "periodEnd must not be before periodStart"
                );
            }
        }
    }
}
```

This gives us an important architectural contract:

```text
Syirkah Close
      |
      v
SyirkahFinancialResultProvider
      |
      +-- period-aware GL implementation
      +-- reporting implementation
      +-- database implementation
      +-- test implementation
```

The Syirkah domain does not care where the result comes from.

---

# 19. Add the contract repository

We also need to resolve the effective akad.

Create:

```text
finance/accounting/syirkah-accounting-application/src/main/java/
tech/kayys/syirkah/accounting/application/port/
SyirkahContractRepository.java
```

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahContract;

import java.time.LocalDate;
import java.util.Optional;

public interface SyirkahContractRepository {

    Uni<Optional<SyirkahContract>> findEffectiveContract(
            String tenantId,
            String ledgerId,
            LocalDate effectiveDate
    );

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahContract contract
    );
}
```

This is deliberately tenant/ledger-aware.

A contract must never accidentally be resolved from another tenant.

---

# 20. Add a persistent calculation abstraction

The calculation must survive the close workflow.

Create:

```text
SyirkahCloseCalculationRepository.java
```

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.close.SyirkahCloseProcessor;

import java.util.Optional;

public interface SyirkahCloseCalculationRepository {

    Uni<Optional<SyirkahCloseProcessor.CloseCalculation>> findByCalculationId(
            String calculationId
    );

    Uni<Optional<SyirkahCloseProcessor.CloseCalculation>> findByCloseCycleId(
            String closeCycleId
    );

    Uni<Void> save(
            String closeCycleId,
            SyirkahCloseProcessor.CloseCalculation calculation
    );
}
```

The `closeCycleId` relationship is important.

We want:

```text
CloseCycle
    |
    +-- Syirkah Calculation
            |
            +-- contractId
            +-- contractVersion
            +-- financial result
            +-- allocation
            +-- calculationReference
```

rather than only storing a final number.

---

# 21. Add a proper calculation reference

The existing `CloseCalculation` currently has a string `calculationReference`.

Let's make the identifier explicit.

Create in the Islamic domain:

```text
SyirkahCalculationId.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for an immutable Syirkah financial calculation.
 */
public record SyirkahCalculationId(String value) {

    public SyirkahCalculationId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "SyirkahCalculationId value must not be blank"
            );
        }
    }

    public static SyirkahCalculationId generate() {
        return new SyirkahCalculationId(UUID.randomUUID().toString());
    }

    public static SyirkahCalculationId of(String value) {
        return new SyirkahCalculationId(value);
    }
}
```

Then change the application result eventually to use this identifier instead of an unrestricted String.

For compatibility, we can initially keep the String API.

---

# 22. Implement the real close processor

Now we can build the processor without inventing where financial data comes from.

Create:

```text
SyirkahCloseProcessorImpl.java
```

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.SyirkahCloseCalculationRepository;
import tech.kayys.syirkah.accounting.application.port.SyirkahContractRepository;
import tech.kayys.syirkah.accounting.domain.islamic.FinancialResult;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahProfitSharingEngine;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahResultCalculator;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Default application implementation for Syirkah period-end processing.
 *
 * The processor calculates and persists the economic result.
 * It does not post accounting entries and does not lock the period.
 */
public final class SyirkahCloseProcessorImpl
        implements SyirkahCloseProcessor {

    private final SyirkahFinancialResultProvider financialResultProvider;
    private final SyirkahContractRepository contractRepository;
    private final SyirkahCloseCalculationRepository calculationRepository;
    private final SyirkahResultCalculator resultCalculator;

    public SyirkahCloseProcessorImpl(
            SyirkahFinancialResultProvider financialResultProvider,
            SyirkahContractRepository contractRepository,
            SyirkahCloseCalculationRepository calculationRepository,
            SyirkahResultCalculator resultCalculator
    ) {
        this.financialResultProvider =
                Objects.requireNonNull(financialResultProvider);

        this.contractRepository =
                Objects.requireNonNull(contractRepository);

        this.calculationRepository =
                Objects.requireNonNull(calculationRepository);

        this.resultCalculator =
                Objects.requireNonNull(resultCalculator);
    }

    @Override
    public Uni<CloseCalculation> calculateResult(
            CloseRequest request
    ) {
        return resolveContract(request)
                .chain(contract ->
                        financialResultProvider.calculate(
                                request.tenantId(),
                                request.ledgerId(),
                                request.periodStart(),
                                request.periodEnd()
                        ).map(financialResult -> {

                            var calculation =
                                    resultCalculator.calculate(
                                            contract,
                                            request.periodEnd(),
                                            financialResult
                                    );

                            return toCloseCalculation(
                                    contract,
                                    calculation,
                                    financialResult
                            );
                        })
                )
                .chain(result ->
                        calculationRepository.save(
                                request.fiscalPeriodId(),
                                result
                        ).replaceWith(result)
                );
    }

    @Override
    public Uni<CloseCalculation> allocateResult(
            CloseRequest request
    ) {
        return calculationRepository
                .findByCloseCycleId(request.fiscalPeriodId())
                .chain(existing -> {
                    if (existing.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Syirkah financial result has not been calculated"
                                )
                        );
                    }

                    return Uni.createFrom().item(existing.get());
                });
    }

    @Override
    public Uni<CloseCalculation> prepareDistribution(
            CloseRequest request
    ) {
        return calculationRepository
                .findByCloseCycleId(request.fiscalPeriodId())
                .chain(existing -> {
                    if (existing.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Syirkah allocation has not been calculated"
                                )
                        );
                    }

                    return Uni.createFrom().item(existing.get());
                });
    }

    private Uni<SyirkahContract> resolveContract(
            CloseRequest request
    ) {
        LocalDate effectiveDate = request.periodEnd();

        return contractRepository.findEffectiveContract(
                        request.tenantId(),
                        request.ledgerId(),
                        effectiveDate
                )
                .chain(optional -> optional
                        .map(Uni.createFrom()::item)
                        .orElseGet(() ->
                                Uni.createFrom().failure(
                                        new IllegalStateException(
                                                "No effective Syirkah contract found for "
                                                        + effectiveDate
                                        )
                                )
                        )
                );
    }

    private CloseCalculation toCloseCalculation(
            SyirkahContract contract,
            SyirkahResultCalculator.SyirkahCalculation calculation,
            FinancialResult financialResult
    ) {
        var result = calculation.result();

        var allocations = result.allocations()
                .stream()
                .map(allocation ->
                        new CloseCalculation.PartnerAllocation(
                                allocation.partyId(),
                                allocation.amount(),
                                "AGREED_PROFIT_SHARING_RATIO"
                        )
                )
                .toList();

        if (result.resultType()
                == tech.kayys.syirkah.accounting.domain.islamic
                .ProfitSharingResult.ResultType.LOSS) {

            allocations = calculation.lossAllocations()
                    .stream()
                    .map(allocation ->
                            new CloseCalculation.PartnerAllocation(
                                    allocation.partyId(),
                                    allocation.amount(),
                                    "CAPITAL_PROPORTION"
                            )
                    )
                    .toList();
        }

        return new CloseCalculation(
                UUID.randomUUID().toString(),
                contract.contractId(),
                result.contractVersion(),
                result.netResult(),
                toApplicationResultType(result.resultType()),
                allocations,
                buildCalculationReference(
                        contract,
                        result.contractVersion()
                )
        );
    }

    private CloseCalculation.ResultType toApplicationResultType(
            tech.kayys.syirkah.accounting.domain.islamic
                    .ProfitSharingResult.ResultType resultType
    ) {
        return switch (resultType) {
            case PROFIT -> CloseCalculation.ResultType.PROFIT;
            case LOSS -> CloseCalculation.ResultType.LOSS;
            case BREAK_EVEN -> CloseCalculation.ResultType.BREAK_EVEN;
        };
    }

    private String buildCalculationReference(
            SyirkahContract contract,
            int version
    ) {
        return "SYIRKAH:"
                + contract.contractId()
                + ":V"
                + version;
    }
}
```

There is one thing I would change from the previous version: **`fiscalPeriodId` should not be used as the close-cycle repository key**.

The processor request currently doesn't contain `closeCycleId`.

Let's fix that now rather than allowing an ambiguous identifier.

---

# 23. Correct `CloseRequest`

Change the record to:

```java
record CloseRequest(
        String closeCycleId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String requestedBy
) {
    public CloseRequest {
        Objects.requireNonNull(
                closeCycleId,
                "closeCycleId must not be null"
        );

        Objects.requireNonNull(
                tenantId,
                "tenantId must not be null"
        );

        Objects.requireNonNull(
                ledgerId,
                "ledgerId must not be null"
        );

        Objects.requireNonNull(
                fiscalPeriodId,
                "fiscalPeriodId must not be null"
        );

        Objects.requireNonNull(
                periodStart,
                "periodStart must not be null"
        );

        Objects.requireNonNull(
                periodEnd,
                "periodEnd must not be null"
        );

        Objects.requireNonNull(
                requestedBy,
                "requestedBy must not be null"
        );

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "periodEnd must not be before periodStart"
            );
        }
    }
}
```

Then change:

```java
calculationRepository.save(
        request.fiscalPeriodId(),
        result
)
```

to:

```java
calculationRepository.save(
        request.closeCycleId(),
        result
)
```

and:

```java
.findByCloseCycleId(request.fiscalPeriodId())
```

to:

```java
.findByCloseCycleId(request.closeCycleId())
```

This is a small detail, but it matters greatly when one fiscal period has multiple close attempts/reviews.

---

# 24. Do not create the distribution journal yet

This is where we need to be careful.

The existing framework already has a general ledger posting path:

```text
PostJournalEntryCommand
        ↓
PostJournalEntryHandler
        ↓
ComplianceRulePipeline
        ↓
JournalEntry
        ↓
AccountRepository
        ↓
JournalEntryRepository
```

and the journal supports:

```java
ShariaContractType
```

as well as dimensions. syirkah-framework(1) syirkah-framework(1)

So eventually the Syirkah distribution will go through this existing path.

But we **should not yet hardcode**:

```text
Debit Profit Distribution Expense
Credit Partner Payable
```

because the correct accounting treatment depends on the existing Syirkah accounting policy and the nature of the partnership.

The safer architecture is:

```text
Syirkah Calculation
       │
       ▼
Distribution Obligation
       │
       ▼
Accounting Policy
       │
       ▼
Journal Mapping
       │
       ▼
PostJournalEntryCommand
```

rather than:

```text
Syirkah Calculation
       │
       └── hardcoded journal
```

---

# 25. Add `PartnerDistributionObligation`

Create in the Islamic domain:

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Economic obligation created by an approved Syirkah result.
 *
 * This is not itself a general-ledger journal entry.
 */
public record PartnerDistributionObligation(
        String obligationId,
        String contractId,
        int contractVersion,
        String partyId,
        BigDecimal amount,
        LocalDate determinationDate,
        DistributionStatus status
) {

    public PartnerDistributionObligation {
        Objects.requireNonNull(obligationId, "obligationId must not be null");
        Objects.requireNonNull(contractId, "contractId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(determinationDate, "determinationDate must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Distribution amount must not be negative"
            );
        }
    }

    public enum DistributionStatus {
        CALCULATED,
        APPROVED,
        POSTED,
        SETTLED,
        CANCELLED
    }
}
```

Now the lifecycle becomes:

```text
Financial Result
      ↓
Profit/Loss Allocation
      ↓
Distribution Obligation
      ↓
Sharia Review
      ↓
Accounting Approval
      ↓
GL Posting
      ↓
Settlement
```

That separation is exactly what we need for an auditable `tutup buku`.

---

# 26. Add an explicit accounting policy

Create:

```text
SyirkahDistributionAccountingPolicy.java
```

```java
package tech.kayys.syirkah.accounting.application.close;

import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.util.List;

/**
 * Converts approved Syirkah distribution obligations into accounting instructions.
 *
 * The policy determines the accounting treatment. The Syirkah domain itself
 * does not assume that every distribution is an operating expense.
 */
public interface SyirkahDistributionAccountingPolicy {

    JournalMapping map(
            List<PartnerDistributionObligation> obligations
    );

    record JournalMapping(
            List<JournalLineMapping> lines,
            String description
    ) {
        public JournalMapping {
            lines = List.copyOf(lines);
        }
    }

    record JournalLineMapping(
            String accountId,
            LineType type,
            String amount,
            String currencyCode,
            String description
    ) {
    }

    enum LineType {
        DEBIT,
        CREDIT
    }
}
```

This is the extension point for:

```text
Mudharabah accounting policy
Musyarakah accounting policy
specific entity accounting policy
Sharia accounting standard policy
```

without polluting `GeneralLedgerService`.

---

# 27. Then posting becomes very small

When we reach the approved-posting phase, the adapter will look conceptually like this:

```java
PostJournalEntryCommand command =
        PostJournalEntryCommand.builder()
                .description(mapping.description())
                .referenceNumber(
                        "SYIRKAH-" + calculationId
                )
                .sourceType("SYIRKAH_DISTRIBUTION")
                .sourceId(calculationId)
                .postedBy(approvedBy)
                .lines(
                        mapping.lines()
                                .stream()
                                .map(line ->
                                        new PostJournalEntryCommand.JournalLineCommand(
                                                UUID.fromString(line.accountId()),
                                                line.type().name(),
                                                line.amount(),
                                                line.currencyCode(),
                                                line.description()
                                        )
                                )
                                .toList()
                )
                .build();
```

Then:

```java
generalLedgerService.postJournalEntry(command);
```

or preferably the existing `CommandBus` / `PostJournalEntryHandler`, depending on which application entry point we standardize.

The existing command already has:

```text
sourceType
sourceId
postedBy
```

which is ideal for Syirkah traceability. syirkah-framework(1)

---

# 28. One important correction to `FinancialReportingService`

The current implementation does:

```java
accountRepository.findAll()
```

and then uses:

```java
acc.getCurrentBalance()
```

for the Income Statement. syirkah-framework(1)

That means this:

```java
generateIncomeStatement(
    new ReportPeriod(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31),
        "January"
    )
);
```

does **not currently guarantee January-only accounting data**.

So for Syirkah `tutup buku`, we should create a period-aware ledger provider rather than reusing that implementation blindly.

The target is:

```text
Ledger
  |
  +-- Journal entries
        |
        +-- 2026-01-01 ... 2026-01-31
        |
        v
Period Financial Result
        |
        +-- Revenue
        +-- Direct Cost
        +-- Operating Cost
        +-- Other Cost
        |
        v
Net Result
        |
        v
Syirkah Engine
```

This is especially important because the Syirkah source material defines the distribution basis from the **period's realized financial result**, not arbitrary current account balances. Hukum-muamalah

---

## Current implementation state

We now have the architecture:

```text
                    EXISTING ACCOUNTING
                           │
             ┌─────────────┴─────────────┐
             │                           │
        General Ledger              CloseCycle
             │                           │
             │                    ┌──────┴──────┐
             │                    │             │
             │                 Normal       Syirkah
             │                                  │
             │                       ┌──────────┼──────────┐
             │                       │          │          │
             │                    Result    Allocation  Distribution
             │                       │          │          │
             │                       └──────────┴──────────┘
             │                                  │
             │                           Approval / Review
             │                                  │
             └──────────────────────────────────┤
                                                │
                                      Accounting Policy
                                                │
                                      PostJournalEntry
                                                │
                                           GL Posting
                                                │
                                           Period Lock
```

And importantly:

**No second accounting engine has been introduced.**

The next implementation step is the **period-aware `SyirkahFinancialResultProvider`**, using the existing journal repository/ledger data, followed by the **distribution obligation repository and idempotent posting handler**. That will turn the current domain calculation into an actual end-to-end `tutup buku Syirkah` flow.


/////////////


We found the exact existing APIs needed. `JournalEntryRepository` already exposes all journal entries, `JournalEntry` exposes tenant/ledger/date/lines, and `Account` exposes its `AccountType`; therefore we can build the period result directly from **posted journal lines**, without changing the existing ledger engine. syirkah-framework(1) syirkah-framework(1) syirkah-framework(1)

## 29. Implement the period-aware financial result provider

One correction to the earlier approach: we should **not** use `Account.currentBalance` for a historical close. Instead:

```text
Posted Journal Entries
        ↓
filter tenant + ledger
        ↓
filter period dates
        ↓
resolve AccountType
        ↓
Revenue / Expense movements
        ↓
FinancialResult
```

Create:

```text
finance/accounting/syirkah-accounting-application/src/main/java/
tech/kayys/syirkah/accounting/application/close/
PeriodAwareSyirkahFinancialResultProvider.java
```

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.islamic.FinancialResult;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates a period financial result from posted journal lines.
 *
 * Only posted entries belonging to the requested tenant, ledger,
 * and accounting period are included.
 */
public final class PeriodAwareSyirkahFinancialResultProvider
        implements SyirkahFinancialResultProvider {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;

    public PeriodAwareSyirkahFinancialResultProvider(
            JournalEntryRepository journalEntryRepository,
            AccountRepository accountRepository
    ) {
        this.journalEntryRepository =
                Objects.requireNonNull(journalEntryRepository);

        this.accountRepository =
                Objects.requireNonNull(accountRepository);
    }

    @Override
    public Uni<FinancialResult> calculate(
            String tenantId,
            String ledgerId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ledgerId, "ledgerId must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");

        if (periodEnd.isBefore(periodStart)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "periodEnd must not be before periodStart"
                    )
            );
        }

        return journalEntryRepository.findAll()
                .chain(entries -> calculateFromEntries(
                        entries,
                        tenantId,
                        ledgerId,
                        periodStart,
                        periodEnd
                ));
    }

    private Uni<FinancialResult> calculateFromEntries(
            List<JournalEntry> entries,
            String tenantId,
            String ledgerId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        List<JournalEntry> periodEntries = entries.stream()
                .filter(entry ->
                        tenantId.equals(entry.tenantId().value())
                )
                .filter(entry ->
                        ledgerId.equals(entry.ledgerId().value())
                )
                .filter(entry ->
                        entry.getStatus()
                                == JournalEntry.EntryStatus.POSTED
                )
                .filter(entry -> {
                    LocalDate date = LocalDate.ofInstant(
                            entry.getEntryDate(),
                            ZoneOffset.UTC
                    );

                    return !date.isBefore(periodStart)
                            && !date.isAfter(periodEnd);
                })
                .toList();

        return resolveAccounts(periodEntries)
                .map(accounts ->
                        calculate(
                                periodEntries,
                                accounts
                        )
                );
    }

    private Uni<Map<String, Account>> resolveAccounts(
            List<JournalEntry> entries
    ) {
        Map<String, Account> accounts = new HashMap<>();

        for (JournalEntry entry : entries) {
            for (JournalEntry.JournalLine line : entry.getLines()) {
                String accountId = line.accountId().getValue();

                if (accounts.containsKey(accountId)) {
                    continue;
                }

                // Resolution is performed sequentially below.
            }
        }

        Uni<Void> resolution = Uni.createFrom().voidItem();

        for (JournalEntry entry : entries) {
            for (JournalEntry.JournalLine line : entry.getLines()) {

                String accountId = line.accountId().getValue();

                if (accounts.containsKey(accountId)) {
                    continue;
                }

                resolution = resolution.chain(() ->
                        accountRepository
                                .findById(line.accountId())
                                .chain(optional -> {
                                    if (optional.isEmpty()) {
                                        return Uni.createFrom().failure(
                                                new IllegalStateException(
                                                        "Account not found: "
                                                                + accountId
                                                )
                                        );
                                    }

                                    accounts.put(
                                            accountId,
                                            optional.get()
                                    );

                                    return Uni.createFrom().voidItem();
                                })
                );
            }
        }

        return resolution.map(ignored -> accounts);
    }

    private FinancialResult calculate(
            List<JournalEntry> entries,
            Map<String, Account> accounts
    ) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (JournalEntry entry : entries) {
            for (JournalEntry.JournalLine line : entry.getLines()) {

                Account account =
                        accounts.get(line.accountId().getValue());

                if (account == null) {
                    throw new IllegalStateException(
                            "Account metadata is missing: "
                                    + line.accountId().getValue()
                    );
                }

                BigDecimal debit =
                        line.debit().amount();

                BigDecimal credit =
                        line.credit().amount();

                switch (account.getAccountType()) {

                    case REVENUE ->
                            revenue = revenue
                                    .add(credit)
                                    .subtract(debit);

                    case EXPENSE ->
                            expenses = expenses
                                    .add(debit)
                                    .subtract(credit);

                    default -> {
                        // Assets, liabilities, and equity do not form
                        // part of the operating financial result.
                    }
                }
            }
        }

        /*
         * The current FinancialResult model distinguishes:
         *
         * revenue
         * direct costs
         * operating costs
         * other costs
         *
         * The existing AccountType model only distinguishes REVENUE
         * and EXPENSE, so the existing framework does not currently
         * provide enough classification information to reliably split
         * expenses into those three categories.
         *
         * Until an expense classification exists, treat the complete
         * expense movement as operating cost.
         */
        return new FinancialResult(
                revenue,
                BigDecimal.ZERO,
                expenses,
                BigDecimal.ZERO
        );
    }
}
```

This uses the existing `AccountType` values:

```text
ASSET
LIABILITY
EQUITY
REVENUE
EXPENSE
```

The important limitation is real: **the current framework cannot reliably distinguish direct cost, operating cost, and other cost from `AccountType` alone**. syirkah-framework(1)

So we should not invent that classification.

---

# 30. Add expense classification

This is the next necessary Syirkah extension.

Create:

```text
domain/islamic/SyirkahExpenseCategory.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

public enum SyirkahExpenseCategory {

    DIRECT_COST,

    OPERATING_COST,

    OTHER_COST
}
```

Then create:

```text
domain/islamic/SyirkahAccountClassification.java
```

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.util.Objects;

/**
 * Sharia/Syirkah financial-result classification for an account.
 */
public record SyirkahAccountClassification(
        String accountId,
        SyirkahExpenseCategory expenseCategory
) {

    public SyirkahAccountClassification {
        Objects.requireNonNull(
                accountId,
                "accountId must not be null"
        );

        Objects.requireNonNull(
                expenseCategory,
                "expenseCategory must not be null"
        );
    }
}
```

And the provider should receive a classification port:

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahAccountClassification;

import java.util.Optional;

public interface SyirkahAccountClassificationProvider {

    Uni<Optional<SyirkahAccountClassification>> classify(
            String tenantId,
            String ledgerId,
            String accountId
    );
}
```

Now the financial formula can eventually become exactly:

```text
Revenue
- Direct Costs
- Operating Costs
- Other Costs
= Net Result
```

rather than guessing from generic `EXPENSE`.

---

# 31. Add a default classification adapter

Until the chart-of-accounts extension is implemented, we can use a conservative adapter:

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahAccountClassification;
import tech.kayys.syirkah.accounting.domain.islamic.SyirkahExpenseCategory;

import java.util.Optional;

/**
 * Temporary default classification.
 *
 * Unclassified expense accounts are treated as operating costs.
 */
public final class DefaultSyirkahAccountClassificationProvider
        implements SyirkahAccountClassificationProvider {

    @Override
    public Uni<Optional<SyirkahAccountClassification>> classify(
            String tenantId,
            String ledgerId,
            String accountId
    ) {
        return Uni.createFrom().item(
                Optional.of(
                        new SyirkahAccountClassification(
                                accountId,
                                SyirkahExpenseCategory.OPERATING_COST
                        )
                )
        );
    }
}
```

This is intentionally replaceable.

Later:

```text
Chart of Accounts
       |
       +-- 4000 Revenue
       |
       +-- 5000 Direct Cost
       |      └── DIRECT_COST
       |
       +-- 6000 Operating Expense
       |      └── OPERATING_COST
       |
       +-- 7000 Other Expense
              └── OTHER_COST
```

---

# 32. Make the provider use the classification

Update the constructor:

```java
private final SyirkahAccountClassificationProvider
        classificationProvider;
```

and:

```java
public PeriodAwareSyirkahFinancialResultProvider(
        JournalEntryRepository journalEntryRepository,
        AccountRepository accountRepository,
        SyirkahAccountClassificationProvider classificationProvider
) {
    this.journalEntryRepository =
            Objects.requireNonNull(journalEntryRepository);

    this.accountRepository =
            Objects.requireNonNull(accountRepository);

    this.classificationProvider =
            Objects.requireNonNull(classificationProvider);
}
```

Then the calculation should resolve classifications before calculating.

A clean implementation is:

```java
private Uni<FinancialResult> calculate(
        List<JournalEntry> entries,
        Map<String, Account> accounts,
        String tenantId,
        String ledgerId
) {
    Map<String, SyirkahExpenseCategory> classifications =
            new HashMap<>();

    Uni<Void> resolution = Uni.createFrom().voidItem();

    for (Account account : accounts.values()) {

        if (account.getAccountType()
                != AccountType.EXPENSE) {
            continue;
        }

        String accountId =
                account.getId().getValue();

        resolution = resolution.chain(() ->
                classificationProvider.classify(
                        tenantId,
                        ledgerId,
                        accountId
                ).chain(optional -> {

                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "No Syirkah classification for expense account: "
                                                + accountId
                                )
                        );
                    }

                    classifications.put(
                            accountId,
                            optional.get().expenseCategory()
                    );

                    return Uni.createFrom().voidItem();
                })
        );
    }

    return resolution.map(ignored ->
            calculate(
                    entries,
                    accounts,
                    classifications
            )
    );
}
```

Then:

```java
private FinancialResult calculate(
        List<JournalEntry> entries,
        Map<String, Account> accounts,
        Map<String, SyirkahExpenseCategory> classifications
) {
    BigDecimal revenue = BigDecimal.ZERO;
    BigDecimal directCosts = BigDecimal.ZERO;
    BigDecimal operatingCosts = BigDecimal.ZERO;
    BigDecimal otherCosts = BigDecimal.ZERO;

    for (JournalEntry entry : entries) {

        for (JournalEntry.JournalLine line : entry.getLines()) {

            Account account =
                    accounts.get(
                            line.accountId().getValue()
                    );

            BigDecimal debit =
                    line.debit().amount();

            BigDecimal credit =
                    line.credit().amount();

            if (account.getAccountType()
                    == AccountType.REVENUE) {

                revenue = revenue
                        .add(credit)
                        .subtract(debit);

                continue;
            }

            if (account.getAccountType()
                    != AccountType.EXPENSE) {
                continue;
            }

            BigDecimal expenseMovement =
                    debit.subtract(credit);

            SyirkahExpenseCategory category =
                    classifications.get(
                            account.getId().getValue()
                    );

            switch (category) {
                case DIRECT_COST ->
                        directCosts =
                                directCosts.add(expenseMovement);

                case OPERATING_COST ->
                        operatingCosts =
                                operatingCosts.add(expenseMovement);

                case OTHER_COST ->
                        otherCosts =
                                otherCosts.add(expenseMovement);
            }
        }
    }

    return new FinancialResult(
            revenue,
            directCosts,
            operatingCosts,
            otherCosts
    );
}
```

This is now aligned with the financial-result formula we established earlier rather than hiding all expenses inside one bucket.

---

# 33. Add idempotent distribution obligations

The existing framework already has a reliability module whose stated goals include **idempotency, retry, outbox, inbox, and failure classification**. syirkah-framework(1)

So Syirkah should use that architecture rather than inventing a separate idempotency mechanism.

Create:

```text
SyirkahDistributionRepository.java
```

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.util.List;
import java.util.Optional;

public interface SyirkahDistributionRepository {

    Uni<List<PartnerDistributionObligation>> findByCalculationId(
            String calculationId
    );

    Uni<Optional<PartnerDistributionObligation>> findById(
            String obligationId
    );

    Uni<Void> save(
            String calculationId,
            PartnerDistributionObligation obligation
    );
}
```

The calculation ID becomes the idempotency boundary:

```text
SYIRKAH CALCULATION
       |
       +-- Partner A distribution
       |
       +-- Partner B distribution
       |
       +-- Partner C distribution
```

If the close workflow retries, it queries the existing calculation/distributions instead of creating another obligation.

---

# 34. Implement the distribution preparation service

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.SyirkahDistributionRepository;
import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SyirkahDistributionPreparationService {

    private final SyirkahDistributionRepository repository;

    public SyirkahDistributionPreparationService(
            SyirkahDistributionRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Uni<List<PartnerDistributionObligation>> prepare(
            String calculationId,
            String contractId,
            int contractVersion,
            LocalDate determinationDate,
            List<SyirkahCloseProcessor.PartnerAllocation> allocations
    ) {
        Objects.requireNonNull(calculationId);
        Objects.requireNonNull(contractId);
        Objects.requireNonNull(determinationDate);
        Objects.requireNonNull(allocations);

        return repository.findByCalculationId(calculationId)
                .chain(existing -> {

                    if (!existing.isEmpty()) {
                        return Uni.createFrom().item(
                                List.copyOf(existing)
                        );
                    }

                    List<PartnerDistributionObligation> obligations =
                            new ArrayList<>();

                    for (var allocation : allocations) {

                        if (allocation.amount().signum() <= 0) {
                            continue;
                        }

                        obligations.add(
                                new PartnerDistributionObligation(
                                        UUID.randomUUID().toString(),
                                        contractId,
                                        contractVersion,
                                        allocation.partyId(),
                                        allocation.amount(),
                                        determinationDate,
                                        PartnerDistributionObligation
                                                .DistributionStatus
                                                .CALCULATED
                                )
                        );
                    }

                    Uni<Void> saveChain =
                            Uni.createFrom().voidItem();

                    for (var obligation : obligations) {
                        saveChain = saveChain.chain(() ->
                                repository.save(
                                        calculationId,
                                        obligation
                                )
                        );
                    }

                    return saveChain.map(
                            ignored -> List.copyOf(obligations)
                    );
                });
    }
}
```

The important property is:

```java
if (!existing.isEmpty()) {
    return existing;
}
```

Therefore:

```text
first attempt
    → create obligations

retry
    → find existing
    → return existing

retry again
    → find existing
    → return existing
```

No duplicate partner obligation.

---

# 35. Add a domain event for the result

The existing accounting event architecture already has tenant and ledger context. syirkah-framework(1)

Create:

```text
domain/event/SyirkahResultCalculated.java
```

```java
package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SyirkahResultCalculated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,
        String closeCycleId,
        String calculationId,
        String contractId,
        int contractVersion,
        BigDecimal netResult,
        ResultType resultType,
        String correlationId,
        String causationId
) implements AccountingEvent {

    public enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }

    public static SyirkahResultCalculated of(
            TenantId tenantId,
            LedgerId ledgerId,
            String closeCycleId,
            String calculationId,
            String contractId,
            int contractVersion,
            BigDecimal netResult,
            ResultType resultType,
            String correlationId,
            String causationId
    ) {
        return new SyirkahResultCalculated(
                UUID.randomUUID(),
                Instant.now(),
                tenantId,
                ledgerId,
                closeCycleId,
                calculationId,
                contractId,
                contractVersion,
                netResult,
                resultType,
                correlationId,
                causationId
        );
    }
}
```

This lets the rest of the ecosystem react:

```text
SyirkahResultCalculated
        |
        +-- accounting projection
        +-- partner statement
        +-- reporting
        +-- audit
        +-- notification
        +-- distribution workflow
        +-- external integration
```

without coupling those systems directly to `SyirkahCloseProcessor`.

---

# 36. The close flow is now becoming complete

We now have:

```text
                EXISTING CLOSE CYCLE
                       │
                       ▼
                Reconciliation
                       │
                       ▼
             ┌───────────────────┐
             │ Financial Result  │
             └─────────┬─────────┘
                       │
              period-aware GL
                       │
                       ▼
              Revenue / Costs
                       │
                       ▼
                 Net Result
                       │
                       ▼
              Effective Akad
                       │
                       ▼
              Contract Version
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
          PROFIT                LOSS
             │                   │
          Nisbah             Loss Policy
             │                   │
             └─────────┬─────────┘
                       ▼
                Allocation
                       │
                       ▼
             Distribution Obligation
                       │
                       ▼
                  Approval
                       │
                       ▼
             Existing GL Posting
                       │
                       ▼
                  Period Lock
```

And there is a crucial distinction:

**`FinancialResult` is not yet a journal.**

**`ProfitSharingResult` is not yet a journal.**

**`PartnerDistributionObligation` is not yet a journal.**

Only the final approved accounting policy should turn it into an existing `PostJournalEntryCommand`.

That separation will make the eventual `tutup buku` implementation much safer and auditable.

### One issue we should fix before journal posting

The current `GeneralLedgerService.postJournalEntry(...)` creates the `JournalEntry` and then posts it, but it does not yet appear to consume `sourceType/sourceId` into the domain entry itself. syirkah-framework(1)

For Syirkah idempotency, **`sourceType = SYIRKAH_DISTRIBUTION` and `sourceId = calculationId/obligationId` should become queryable persistence metadata**, otherwise retries can still create duplicate GL entries even though the distribution obligation is idempotent.

So the next tranche should be:

**add posting-source/idempotency metadata to the existing `JournalEntry`, implement `SyirkahDistributionPostingService` through `PostJournalEntryCommand`, and add the final `CloseCycle → calculate → allocate → approve → post → lock` orchestration.**


//////////


Yes. The important discovery is that `PostJournalEntryCommand` **already carries `sourceType` and `sourceId`**, but `GeneralLedgerService` currently ignores them when constructing `JournalEntry`. syirkah-framework(1) syirkah-framework(1)

So we should extend the existing journal aggregate rather than create another posting mechanism.

## 37. Add posting-source metadata to `JournalEntry`

In:

```text
finance/accounting/syirkah-accounting-domain/
  src/main/java/tech/kayys/syirkah/accounting/domain/model/JournalEntry.java
```

add:

```java
private String sourceType;
private String sourceId;
```

Add an explicit method:

```java
public void setPostingSource(
        String sourceType,
        String sourceId
) {
    if (status != EntryStatus.DRAFT) {
        throw new IllegalStateException(
                "Cannot modify posting source of a non-draft journal entry"
        );
    }

    if (sourceType == null || sourceType.isBlank()) {
        throw new IllegalArgumentException(
                "sourceType must not be blank"
        );
    }

    if (sourceId == null || sourceId.isBlank()) {
        throw new IllegalArgumentException(
                "sourceId must not be blank"
        );
    }

    this.sourceType = sourceType;
    this.sourceId = sourceId;
}
```

And getters:

```java
public String getSourceType() {
    return sourceType;
}

public String getSourceId() {
    return sourceId;
}
```

This gives us:

```text
JournalEntry
 ├── sourceType = SYIRKAH_DISTRIBUTION
 └── sourceId   = <calculation-id>
```

without changing the existing journal-line model.

---

# 38. Preserve source metadata during reversal

This is important for auditability.

Inside `createStornoReversal(...)`, after constructing the reversal:

```java
reversal.sourceType = this.sourceType;
reversal.sourceId = this.sourceId;
```

Therefore:

```text
Original
SYIRKAH_DISTRIBUTION / CALC-123

        ↓ reversal

Reversal
SYIRKAH_DISTRIBUTION / CALC-123
```

The reversal is still traceable to the original Syirkah distribution.

---

# 39. Add repository lookup by posting source

The existing `JournalEntryRepository` should gain:

```java
Uni<Optional<JournalEntry>> findBySource(
        String sourceType,
        String sourceId
);
```

So the repository contract becomes:

```java
public interface JournalEntryRepository {

    Uni<Void> save(JournalEntry entry);

    Uni<Optional<JournalEntry>> findById(
            JournalEntryId id
    );

    Uni<List<JournalEntry>> findAll();

    Uni<Optional<JournalEntry>> findBySource(
            String sourceType,
            String sourceId
    );
}
```

This is the actual GL idempotency boundary.

---

# 40. Fix `GeneralLedgerService`

The existing service currently does:

```java
JournalEntry entry = new JournalEntry(
        entryId,
        command.referenceNumber() != null
                ? command.referenceNumber()
                : "JE-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8),
        Instant.now(),
        command.description()
);
```

and ignores:

```java
command.sourceType()
command.sourceId()
```

Add this immediately afterward:

```java
if (command.sourceType() != null
        || command.sourceId() != null) {

    if (command.sourceType() == null
            || command.sourceType().isBlank()) {
        return Uni.createFrom().failure(
                new IllegalArgumentException(
                        "sourceType must be provided when sourceId is provided"
                )
        );
    }

    if (command.sourceId() == null
            || command.sourceId().isBlank()) {
        return Uni.createFrom().failure(
                new IllegalArgumentException(
                        "sourceId must be provided when sourceType is provided"
                )
        );
    }

    entry.setPostingSource(
            command.sourceType(),
            command.sourceId()
    );
}
```

But we also need idempotency **before creating the entry**.

---

# 41. Make posting idempotent

At the beginning of `postJournalEntry(...)`:

```java
public Uni<JournalEntryId> postJournalEntry(
        PostJournalEntryCommand command
) {
    if (command.sourceType() != null
            && command.sourceId() != null) {

        return journalEntryRepository
                .findBySource(
                        command.sourceType(),
                        command.sourceId()
                )
                .chain(existing -> {

                    if (existing.isPresent()) {
                        JournalEntry entry = existing.get();

                        if (entry.getStatus()
                                == JournalEntry.EntryStatus.POSTED) {

                            return Uni.createFrom()
                                    .item(entry.getId());
                        }

                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Journal entry already exists for source "
                                                + command.sourceType()
                                                + "/"
                                                + command.sourceId()
                                                + " but is not posted"
                                )
                        );
                    }

                    return postNewJournalEntry(command);
                });
    }

    return postNewJournalEntry(command);
}
```

Move the existing posting implementation into:

```java
private Uni<JournalEntryId> postNewJournalEntry(
        PostJournalEntryCommand command
) {
    // existing implementation
}
```

This gives:

```text
first request
    source = SYIRKAH_DISTRIBUTION/CALC-123
          ↓
    no existing entry
          ↓
    create + post

retry
    source = SYIRKAH_DISTRIBUTION/CALC-123
          ↓
    existing POSTED entry
          ↓
    return existing ID
```

So a network retry cannot generate another GL posting.

---

# 42. Add a Syirkah-specific posting service

Now we can finally create the Syirkah application boundary.

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.service.GeneralLedgerService;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.util.List;
import java.util.Objects;

public final class SyirkahDistributionPostingService {

    public static final String SOURCE_TYPE =
            "SYIRKAH_DISTRIBUTION";

    private final GeneralLedgerService generalLedgerService;
    private final SyirkahDistributionAccountingPolicy accountingPolicy;

    public SyirkahDistributionPostingService(
            GeneralLedgerService generalLedgerService,
            SyirkahDistributionAccountingPolicy accountingPolicy
    ) {
        this.generalLedgerService =
                Objects.requireNonNull(generalLedgerService);

        this.accountingPolicy =
                Objects.requireNonNull(accountingPolicy);
    }

    public Uni<JournalEntryId> post(
            String calculationId,
            List<PartnerDistributionObligation> obligations,
            String postedBy
    ) {
        Objects.requireNonNull(calculationId);
        Objects.requireNonNull(obligations);
        Objects.requireNonNull(postedBy);

        if (obligations.isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Cannot post empty Syirkah distribution"
                    )
            );
        }

        for (var obligation : obligations) {
            if (obligation.status()
                    != PartnerDistributionObligation
                    .DistributionStatus.APPROVED) {

                return Uni.createFrom().failure(
                        new IllegalStateException(
                                "All Syirkah distribution obligations "
                                        + "must be APPROVED before posting"
                        )
                );
            }
        }

        var mapping =
                accountingPolicy.map(obligations);

        List<PostJournalEntryCommand.JournalLineCommand> lines =
                mapping.lines()
                        .stream()
                        .map(line ->
                                new PostJournalEntryCommand
                                        .JournalLineCommand(
                                                java.util.UUID.fromString(
                                                        line.accountId()
                                                ),
                                                line.type().name(),
                                                line.amount(),
                                                line.currencyCode(),
                                                line.description()
                                        )
                        )
                        .toList();

        PostJournalEntryCommand command =
                PostJournalEntryCommand.builder()
                        .description(mapping.description())
                        .referenceNumber(
                                "SYIRKAH-" + calculationId
                        )
                        .lines(lines)
                        .sourceType(SOURCE_TYPE)
                        .sourceId(calculationId)
                        .postedBy(postedBy)
                        .build();

        return generalLedgerService.postJournalEntry(command);
    }
}
```

Notice what this service **doesn't** decide:

- whether profit is distributable;
- what the nisbah is;
- how loss is allocated;
- which accounts are used.

Those decisions remain in the appropriate policies.

---

# 43. Add an approval service

Distribution must not jump directly from `CALCULATED` to `POSTED`.

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.SyirkahDistributionRepository;
import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.util.List;
import java.util.Objects;

public final class SyirkahDistributionApprovalService {

    private final SyirkahDistributionRepository repository;

    public SyirkahDistributionApprovalService(
            SyirkahDistributionRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Uni<List<PartnerDistributionObligation>> approve(
            String calculationId,
            String approver
    ) {
        if (approver == null || approver.isBlank()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "approver must not be blank"
                    )
            );
        }

        return repository.findByCalculationId(calculationId)
                .chain(obligations -> {

                    if (obligations.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "No distribution obligations found for calculation "
                                                + calculationId
                                )
                        );
                    }

                    for (var obligation : obligations) {
                        if (obligation.status()
                                != PartnerDistributionObligation
                                .DistributionStatus.CALCULATED) {

                            return Uni.createFrom().failure(
                                    new IllegalStateException(
                                            "Distribution obligation "
                                                    + obligation.obligationId()
                                                    + " cannot be approved from status "
                                                    + obligation.status()
                                    )
                            );
                        }
                    }

                    List<PartnerDistributionObligation> approved =
                            obligations.stream()
                                    .map(obligation ->
                                            new PartnerDistributionObligation(
                                                    obligation.obligationId(),
                                                    obligation.contractId(),
                                                    obligation.contractVersion(),
                                                    obligation.partyId(),
                                                    obligation.amount(),
                                                    obligation.determinationDate(),
                                                    PartnerDistributionObligation
                                                            .DistributionStatus
                                                            .APPROVED
                                            )
                                    )
                                    .toList();

                    Uni<Void> saveChain =
                            Uni.createFrom().voidItem();

                    for (var obligation : approved) {
                        saveChain = saveChain.chain(() ->
                                repository.save(
                                        calculationId,
                                        obligation
                                )
                        );
                    }

                    return saveChain.map(
                            ignored -> approved
                    );
                });
    }
}
```

For production, I would add `approvedBy` and `approvedAt` to the obligation itself. That is worth doing before we call this feature complete.

---

# 44. Important: `CloseCycle` already protects the final lock

The current framework already has the correct invariant:

```java
public void approve(String approver) {
    if (status != CloseStatus.VALIDATING) {
        throw new IllegalStateException(...);
    }

    boolean allMandatoryDone = tasks.stream()
            .filter(CloseTask::isMandatory)
            .allMatch(t ->
                    t.status() == CloseTaskStatus.COMPLETED
            );

    if (!allMandatoryDone) {
        throw new IllegalStateException(
                "Cannot approve close cycle: "
                        + "some mandatory tasks are incomplete"
        );
    }

    this.approvedBy = Objects.requireNonNull(approver);
    this.status = CloseStatus.APPROVED;
}
```

and:

```java
public void lock() {
    if (status != CloseStatus.APPROVED) {
        throw new IllegalStateException(
                "CloseCycle must be APPROVED before locking"
        );
    }

    this.status = CloseStatus.LOCKED;
}
```

syirkah-framework(1)

That is exactly what we need.

---

# 45. Extend `CloseTaskKind`

Change:

```java
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,
    LOCK
}
```

to:

```java
public enum CloseTaskKind {

    SUBLEDGER_CUTOFF,

    ACCRUALS,

    DEPRECIATION,

    FX_REVALUATION,

    RECONCILIATION,

    SYIRKAH_RESULT,

    SYIRKAH_ALLOCATION,

    SYIRKAH_DISTRIBUTION,

    LOCK
}
```

This is an extension, not a replacement.

---

# 46. Make Syirkah close tasks opt-in

Keep the existing behavior:

```java
CloseCycle.open(
    id,
    tenantId,
    ledgerId,
    fiscalPeriodId
);
```

and add:

```java
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        boolean syirkahEnabled
) {
    List<CloseTask> tasks = new ArrayList<>();

    tasks.add(new CloseTask(
            "TASK_SUBLEDGER_CUTOFF",
            "Subledger Cut-off",
            CloseTaskKind.SUBLEDGER_CUTOFF,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_ACCRUALS",
            "Post Accruals",
            CloseTaskKind.ACCRUALS,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_DEPRECIATION",
            "Fixed Assets Depreciation",
            CloseTaskKind.DEPRECIATION,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_FX_REVALUATION",
            "Multi-Currency FX Revaluation",
            CloseTaskKind.FX_REVALUATION,
            false
    ));

    tasks.add(new CloseTask(
            "TASK_RECONCILIATION",
            "Subledger & Bank Reconciliation",
            CloseTaskKind.RECONCILIATION,
            true
    ));

    if (syirkahEnabled) {

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_RESULT",
                "Calculate Syirkah Financial Result",
                CloseTaskKind.SYIRKAH_RESULT,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_ALLOCATION",
                "Allocate Syirkah Result",
                CloseTaskKind.SYIRKAH_ALLOCATION,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_DISTRIBUTION",
                "Prepare and Post Syirkah Distribution",
                CloseTaskKind.SYIRKAH_DISTRIBUTION,
                true
        ));
    }

    tasks.add(new CloseTask(
            "TASK_LOCK",
            "General Ledger Hard Lock",
            CloseTaskKind.LOCK,
            true
    ));

    return new CloseCycle(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            tasks
    );
}
```

And retain the original method as:

```java
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId
) {
    return open(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            false
    );
}
```

This means existing customers are completely unaffected.

---

# 47. The final orchestration

Now the close application service should have a dedicated method:

```java
public Uni<CloseCycle> executeSyirkahClose(
        SyirkahCloseProcessor.CloseRequest request
) {
    CloseCycle cycle =
            load(CloseCycleId.of(request.closeCycleId()));

    executeTask(
            cycle,
            "TASK_SYIRKAH_RESULT",
            request.requestedBy(),
            () -> processor.calculateResult(request)
    );

    executeTask(
            cycle,
            "TASK_SYIRKAH_ALLOCATION",
            request.requestedBy(),
            () -> processor.allocateResult(request)
    );

    return processor
            .prepareDistribution(request)
            .chain(calculation ->
                    executeDistribution(
                            cycle,
                            request,
                            calculation
                    )
            )
            .map(ignored -> cycle);
}
```

But **do not call `approveAndLock()` here**.

The existing lifecycle intentionally separates processing from approval:

```text
execute close
     ↓
VALIDATING
     ↓
all mandatory tasks completed
     ↓
human approval
     ↓
APPROVED
     ↓
LOCKED
```

The framework already exposes:

```java
closeService.approveAndLock(
    CloseCycleId.of(id),
    approver
);
```

syirkah-framework(1)

That is the right boundary for maker-checker.

---

# 48. One more correction: posting should happen before final task completion

For `TASK_SYIRKAH_DISTRIBUTION`, completion should mean:

```text
obligations approved
        ↓
journal successfully posted
        ↓
obligations marked POSTED
        ↓
task COMPLETED
```

**Not:**

```text
create obligations
        ↓
task COMPLETED
        ↓
later attempt journal
```

Otherwise the close cycle could become approved/locked while the accounting distribution has never actually been posted.

So the state transition should be:

```text
CALCULATED
    ↓
APPROVED
    ↓
POSTED
    ↓
SETTLED
```

with `CloseTask` only becoming `COMPLETED` at `POSTED`.

---

# 49. Resulting Syirkah `tutup buku`

We now have a much stronger semantic definition:

```text
┌──────────────────────────────────────┐
│          SYIRKAH TUTUP BUKU         │
└──────────────────────────────────────┘

1. Subledger cutoff
2. Accruals
3. Depreciation
4. FX revaluation
5. Reconciliation
6. Calculate period financial result
7. Resolve effective akad version
8. Determine profit / loss
9. Apply profit nisbah
10. Apply loss allocation policy
11. Create partner obligations
12. Obtain approval
13. Generate accounting mapping
14. Post through existing GL
15. Mark distribution POSTED
16. Complete Syirkah close tasks
17. Approve close cycle
18. Hard-lock fiscal period
```

This preserves the existing accounting infrastructure while adding the **Syirkah-specific meaning of closing**.

### Next tranche

The next implementation should focus on the **most important missing domain piece**: making the distribution accounting policy concrete for `MUDHARABAH` vs `MUSYARAKAH`, including the distinction between **profit allocation, loss allocation, partner capital, and payable/distribution accounts**. We should also add `approvedBy/approvedAt/postedJournalEntryId` to `PartnerDistributionObligation` so its lifecycle is fully auditable.


//////////////


Next tranche should lock down the **distribution lifecycle + GL idempotency boundary**. This avoids duplicate partner payouts and makes `tutup buku` auditable.

### 1. Replace `PartnerDistributionObligation` with a lifecycle entity

The current record is too static. It needs to remember who approved it and which journal actually posted it.

```java
package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class PartnerDistributionObligation {

    public enum DistributionStatus {
        CALCULATED,
        APPROVED,
        POSTED,
        SETTLED,
        CANCELLED
    }

    private final String obligationId;
    private final String calculationId;
    private final String contractId;
    private final int contractVersion;
    private final String partyId;
    private final BigDecimal amount;
    private final LocalDate determinationDate;

    private DistributionStatus status;
    private String approvedBy;
    private Instant approvedAt;
    private String postedJournalEntryId;
    private Instant postedAt;

    public PartnerDistributionObligation(
            String obligationId,
            String calculationId,
            String contractId,
            int contractVersion,
            String partyId,
            BigDecimal amount,
            LocalDate determinationDate
    ) {
        this.obligationId = requireText(obligationId, "obligationId");
        this.calculationId = requireText(calculationId, "calculationId");
        this.contractId = requireText(contractId, "contractId");
        this.contractVersion = contractVersion;
        this.partyId = requireText(partyId, "partyId");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.determinationDate =
                Objects.requireNonNull(
                        determinationDate,
                        "determinationDate must not be null"
                );
        this.status = DistributionStatus.CALCULATED;

        if (contractVersion <= 0) {
            throw new IllegalArgumentException(
                    "contractVersion must be greater than zero"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Distribution amount must be greater than zero"
            );
        }
    }

    public void approve(String approver) {
        if (status != DistributionStatus.CALCULATED) {
            throw new IllegalStateException(
                    "Only CALCULATED obligations can be approved"
            );
        }

        this.approvedBy = requireText(approver, "approver");
        this.approvedAt = Instant.now();
        this.status = DistributionStatus.APPROVED;
    }

    public void markPosted(
            String journalEntryId,
            String postedBy
    ) {
        if (status != DistributionStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED obligations can be posted"
            );
        }

        this.postedJournalEntryId =
                requireText(journalEntryId, "journalEntryId");

        requireText(postedBy, "postedBy");

        this.postedAt = Instant.now();
        this.status = DistributionStatus.POSTED;
    }

    public void settle() {
        if (status != DistributionStatus.POSTED) {
            throw new IllegalStateException(
                    "Only POSTED obligations can be settled"
            );
        }

        this.status = DistributionStatus.SETTLED;
    }

    public void cancel() {
        if (status == DistributionStatus.SETTLED) {
            throw new IllegalStateException(
                    "A settled obligation cannot be cancelled"
            );
        }

        if (status == DistributionStatus.POSTED) {
            throw new IllegalStateException(
                    "A posted obligation must be reversed before cancellation"
            );
        }

        this.status = DistributionStatus.CANCELLED;
    }

    private static String requireText(
            String value,
            String field
    ) {
        Objects.requireNonNull(value, field + " must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value;
    }

    public String obligationId() {
        return obligationId;
    }

    public String calculationId() {
        return calculationId;
    }

    public String contractId() {
        return contractId;
    }

    public int contractVersion() {
        return contractVersion;
    }

    public String partyId() {
        return partyId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate determinationDate() {
        return determinationDate;
    }

    public DistributionStatus status() {
        return status;
    }

    public String approvedBy() {
        return approvedBy;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    public String postedJournalEntryId() {
        return postedJournalEntryId;
    }

    public Instant postedAt() {
        return postedAt;
    }
}
```

The important addition is `calculationId`. Without it, the obligation itself cannot reliably explain which close calculation generated it.

---

### 2. Approval must be maker-checker

The approval service should not merely map records from `CALCULATED → APPROVED`.

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.SyirkahDistributionRepository;
import tech.kayys.syirkah.accounting.domain.islamic.PartnerDistributionObligation;

import java.util.List;
import java.util.Objects;

public final class SyirkahDistributionApprovalService {

    private final SyirkahDistributionRepository repository;

    public SyirkahDistributionApprovalService(
            SyirkahDistributionRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Uni<List<PartnerDistributionObligation>> approve(
            String calculationId,
            String approver
    ) {
        Objects.requireNonNull(calculationId);
        Objects.requireNonNull(approver);

        return repository.findByCalculationId(calculationId)
                .chain(obligations -> {

                    if (obligations.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "No distribution obligations found for calculation: "
                                                + calculationId
                        )
                        );
                    }

                    boolean allCalculated = obligations.stream()
                            .allMatch(o ->
                                    o.status()
                                            == PartnerDistributionObligation
                                            .DistributionStatus.CALCULATED);

                    if (!allCalculated) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "All distribution obligations must be CALCULATED before approval"
                                )
                        );
                    }

                    for (var obligation : obligations) {
                        obligation.approve(approver);
                    }

                    return saveSequentially(obligations);
                });
    }

    private Uni<List<PartnerDistributionObligation>> saveSequentially(
            List<PartnerDistributionObligation> obligations
    ) {
        Uni<Void> chain = Uni.createFrom().voidItem();

        for (var obligation : obligations) {
            chain = chain.chain(
                    () -> repository.save(
                            obligation.calculationId(),
                            obligation
                    )
            );
        }

        return chain.replaceWith(obligations);
    }
}
```

This preserves the existing maker-checker philosophy already present in the accounting module.

---

### 3. Fix journal source metadata

The existing `PostJournalEntryCommand` already contains:

```java
String sourceType
String sourceId
```

but `GeneralLedgerService` currently does not propagate them into `JournalEntry`.

Add these fields to `JournalEntry`:

```java
private String sourceType;
private String sourceId;
```

and:

```java
public void setPostingSource(
        String sourceType,
        String sourceId
) {
    if (status != EntryStatus.DRAFT) {
        throw new IllegalStateException(
                "Posting source can only be changed while the journal entry is DRAFT"
        );
    }

    this.sourceType = sourceType;
    this.sourceId = sourceId;
}

public String getSourceType() {
    return sourceType;
}

public String getSourceId() {
    return sourceId;
}
```

Then in `GeneralLedgerService` immediately after creating the entry:

```java
if (command.sourceType() != null || command.sourceId() != null) {
    entry.setPostingSource(
            command.sourceType(),
            command.sourceId()
    );
}
```

This gives us:

```text
SYIRKAH_DISTRIBUTION
        +
calculationId
        ↓
JournalEntry.sourceType
JournalEntry.sourceId
```

---

### 4. Make reversal preserve the source

`createStornoReversal(...)` should copy the source metadata:

```java
reversal.sourceType = this.sourceType;
reversal.sourceId = this.sourceId;
```

So a distribution reversal remains traceable to the original Syirkah calculation.

---

### 5. Add source lookup to the repository

Extend:

```java
public interface JournalEntryRepository {

    Uni<Optional<JournalEntry>> findById(
            JournalEntryId id
    );

    Uni<List<JournalEntry>> findAll();

    Uni<Optional<JournalEntry>> findBySource(
            String sourceType,
            String sourceId
    );

    Uni<Void> save(
            JournalEntry entry
    );
}
```

Now `GeneralLedgerService` can enforce idempotency.

Before creating a journal:

```java
if (command.sourceType() != null
        && command.sourceId() != null) {

    return journalEntryRepository.findBySource(
            command.sourceType(),
            command.sourceId()
    ).chain(existing -> {

        if (existing.isPresent()) {
            JournalEntry entry = existing.get();

            if (entry.getStatus()
                    == JournalEntry.EntryStatus.POSTED) {

                return Uni.createFrom().item(entry.getId());
            }

            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "A journal already exists for source "
                                    + command.sourceType()
                                    + ":"
                                    + command.sourceId()
                    )
            );
        }

        return createAndPost(command);
    });
}
```

Then move the existing posting logic into:

```java
private Uni<JournalEntryId> createAndPost(
        PostJournalEntryCommand command
) {
    // existing GeneralLedgerService implementation
}
```

This is the important boundary:

```text
Retry request
    │
    ▼
sourceType + sourceId
    │
    ▼
JournalEntryRepository.findBySource(...)
    │
    ├── POSTED → return existing journal ID
    │
    ├── DRAFT/PENDING → fail safely
    │
    └── absent → create and post
```

Therefore:

```text
Close calculation
      ↓
Distribution obligations
      ↓
Approval
      ↓
Post journal
      ↓
SYIRKAH_DISTRIBUTION + calculationId
      ↓
Idempotency
```

No duplicate journal on retry.

---

### 6. Distribution posting must update the obligations

After successful posting:

```java
return generalLedgerService
        .postJournalEntry(command)
        .chain(journalEntryId -> {

            Uni<Void> updates =
                    Uni.createFrom().voidItem();

            for (var obligation : obligations) {
                obligation.markPosted(
                        journalEntryId.value(),
                        postedBy
                );

                updates = updates.chain(
                        () -> repository.save(
                                obligation.calculationId(),
                                obligation
                        )
                );
            }

            return updates.replaceWith(journalEntryId);
        });
```

The resulting lifecycle becomes:

```text
CALCULATED
    │
    │ approve()
    ▼
APPROVED
    │
    │ GL successfully posts
    ▼
POSTED
    │
    │ actual partner payment
    ▼
SETTLED
```

And importantly:

```text
APPROVED
   │
   ├── GL fails → remains APPROVED
   │
   └── GL succeeds → POSTED
```

So a temporary accounting/database failure does **not** incorrectly mark the distribution as posted.

---

### 7. Separate accounting posting from cash settlement

This is an important Syirkah distinction.

`tutup buku` should create the **distribution obligation**, then accounting recognition:

```text
Profit determined
      ↓
Partner allocation
      ↓
Distribution payable
```

Actual money movement is later:

```text
Partner payable
      ↓
Payment execution
      ↓
SETTLED
```

So we should **not** model:

```text
profit calculation → bank payment
```

as one operation.

Instead:

```text
                 TUTUP BUKU
                     │
             calculate result
                     │
             calculate nisbah
                     │
            create obligation
                     │
                 approval
                     │
             accounting posting
                     │
             ┌───────┴────────┐
             │                │
          payable         audit trail
             │
        payment process
             │
          SETTLED
```

This will also make integration with banking, payment, treasury, and external partner systems much cleaner.

---

### 8. Next piece: actual `tutup buku` orchestration

The next implementation should connect these pieces to the existing `CloseCycle` instead of creating another close engine:

```text
TASK_RECONCILIATION
        ↓
TASK_SYIRKAH_RESULT
        ↓
TASK_SYIRKAH_ALLOCATION
        ↓
TASK_SYIRKAH_DISTRIBUTION
        ↓
TASK_LOCK
```

with these guarantees:

- no effective Syirkah contract → result task fails
- no calculation → allocation cannot complete
- no approved distribution → distribution task cannot complete
- failed GL posting → distribution task remains incomplete
- completed distribution → close can enter validation
- existing `CloseCycle.approve()` remains the maker-checker boundary
- existing `CloseCycle.lock()` remains the final hard lock

One further issue I would fix in that tranche: the current `GeneralLedgerService` uses the `JournalEntry` constructor that defaults to `TenantId.defaultTenant()` and `LedgerId.primary()`. For a real multi-tenant Syirkah close, the distribution posting must carry the **actual tenant and ledger context**, not silently fall back to those defaults.


/////////////

The next correction is to make **posting context explicit**. The existing `PostJournalEntryCommand` has no tenant/ledger fields, so even though `CloseRequest` knows them, the journal currently falls back to `defaultTenant()` / `primary()`.

### 1. Extend `PostJournalEntryCommand`

Add:

```java id="r4h2q1"
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        String tenantId,
        String ledgerId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {
```

Builder:

```java id="q8x4vn"
private String tenantId;
private String ledgerId;

public Builder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
}

public Builder ledgerId(String ledgerId) {
    this.ledgerId = ledgerId;
    return this;
}
```

And validate them:

```java id="c5kz7m"
if (tenantId == null || tenantId.isBlank()) {
    throw new IllegalArgumentException("Tenant ID is required");
}

if (ledgerId == null || ledgerId.isBlank()) {
    throw new IllegalArgumentException("Ledger ID is required");
}
```

The builder then becomes:

```java id="w8s2pf"
return new PostJournalEntryCommand(
        journalEntryId,
        tenantId,
        ledgerId,
        description,
        referenceNumber,
        lines,
        sourceType,
        sourceId,
        postedBy
);
```

This is preferable to having Syirkah-specific posting logic manipulate accounting tenant context implicitly.

---

### 2. Construct the journal with the actual context

The existing code:

```java id="b3n7qk"
new JournalEntry(
        entryId,
        command.referenceNumber(),
        Instant.now(),
        command.description()
);
```

must become:

```java id="j6m1cz"
JournalEntry entry = new JournalEntry(
        entryId,
        TenantId.of(command.tenantId()),
        LedgerId.of(command.ledgerId()),
        command.referenceNumber() != null
                ? command.referenceNumber()
                : "JE-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8),
        Instant.now(),
        command.description()
);
```

Then immediately:

```java id="a9v4ds"
if (command.sourceType() != null
        || command.sourceId() != null) {

    entry.setPostingSource(
            command.sourceType(),
            command.sourceId()
    );
}
```

And posting should use:

```java id="m2p7xy"
entry.post(
        command.postedBy() != null
                ? command.postedBy()
                : "system"
);
```

rather than always:

```java
entry.post();
```

---

## 3. Syirkah distribution now carries the close context

The posting service should therefore receive the `CloseRequest` context:

```java id="n5q8ra"
public Uni<JournalEntryId> post(
        SyirkahCloseProcessor.CloseRequest request,
        String calculationId,
        List<PartnerDistributionObligation> obligations,
        String postedBy
) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(calculationId);
    Objects.requireNonNull(obligations);
    Objects.requireNonNull(postedBy);

    if (obligations.isEmpty()) {
        return Uni.createFrom().failure(
                new IllegalArgumentException(
                        "Cannot post empty Syirkah distribution"
                )
        );
    }

    boolean allApproved = obligations.stream()
            .allMatch(obligation ->
                    obligation.status()
                            == PartnerDistributionObligation
                            .DistributionStatus.APPROVED);

    if (!allApproved) {
        return Uni.createFrom().failure(
                new IllegalStateException(
                        "All Syirkah distribution obligations must be APPROVED"
                )
        );
    }

    var mapping = accountingPolicy.map(obligations);

    List<PostJournalEntryCommand.JournalLineCommand> lines =
            mapping.lines()
                    .stream()
                    .map(line ->
                            new PostJournalEntryCommand.JournalLineCommand(
                                    UUID.fromString(line.accountId()),
                                    line.type().name(),
                                    line.amount(),
                                    line.currencyCode(),
                                    line.description()
                            )
                    )
                    .toList();

    PostJournalEntryCommand command =
            PostJournalEntryCommand.builder()
                    .tenantId(request.tenantId())
                    .ledgerId(request.ledgerId())
                    .description(mapping.description())
                    .referenceNumber(
                            "SYIRKAH-" + calculationId
                    )
                    .lines(lines)
                    .sourceType(
                            SyirkahDistributionPostingService
                                    .SOURCE_TYPE
                    )
                    .sourceId(calculationId)
                    .postedBy(postedBy)
                    .build();

    return generalLedgerService
            .postJournalEntry(command);
}
```

Now the accounting boundary is:

```text
CloseRequest
 ├── tenantId
 ├── ledgerId
 └── fiscalPeriodId
          │
          ▼
Syirkah Distribution
          │
          ▼
PostJournalEntryCommand
 ├── tenantId
 ├── ledgerId
 ├── sourceType = SYIRKAH_DISTRIBUTION
 └── sourceId   = calculationId
```

No accidental cross-tenant/default-ledger posting.

---

## 4. Wire the distribution task into `CloseCycle`

The key rule is:

> **Do not call `completeTask()` until the journal has actually been posted.**

The application orchestration can therefore be:

```java id="u2k7hf"
public Uni<Void> executeDistributionTask(
        SyirkahCloseProcessor.CloseRequest request,
        String requestedBy
) {
    return calculationRepository
            .findByCloseCycleId(request.closeCycleId())
            .chain(calculation -> {

                if (calculation.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Syirkah close calculation not found for close cycle: "
                                            + request.closeCycleId()
                            )
                    );
                }

                return distributionRepository
                        .findByCalculationId(
                                calculation.get().calculationId()
                        );
            })
            .chain(obligations -> {

                if (obligations.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "No Syirkah distribution obligations exist"
                            )
                    );
                }

                boolean approved = obligations.stream()
                        .allMatch(o ->
                                o.status()
                                        == PartnerDistributionObligation
                                        .DistributionStatus.APPROVED);

                if (!approved) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Syirkah distribution requires approval"
                            )
                    );
                }

                return postingService.post(
                        request,
                        request.closeCycleId(),
                        obligations,
                        requestedBy
                );
            })
            .replaceWithVoid();
}
```

But one correction is important here: **the source ID must be the `calculationId`, not `closeCycleId`**.

So the final implementation should retain the calculation ID from the first lookup rather than substitute the close-cycle ID.

---

## 5. Correct orchestration shape

I recommend making the processor responsible for the whole state transition:

```text id="j9c5kw"
calculateResult()
       │
       ▼
CloseCalculation
       │
       ▼
allocateResult()
       │
       ▼
PartnerDistributionObligation
       │
       ▼
approve distribution
       │
       ▼
post distribution journal
       │
       ▼
mark obligations POSTED
       │
       ▼
complete TASK_SYIRKAH_DISTRIBUTION
```

The close task itself should **not** know how to calculate profit or construct journal entries.

---

## 6. Add the Syirkah task kinds

The existing framework currently has only:

```java id="b7r1pd"
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,
    LOCK
}
```

Extend it:

```java id="m4d8qx"
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,
    SYIRKAH_RESULT,
    SYIRKAH_ALLOCATION,
    SYIRKAH_DISTRIBUTION,
    LOCK
}
```

Then make Syirkah explicitly enabled when opening the cycle:

```java id="z6p2kc"
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId
) {
    return open(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            false
    );
}
```

and:

```java id="h3v9qa"
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        boolean syirkahEnabled
) {
    List<CloseTask> tasks = new ArrayList<>(List.of(
            new CloseTask(
                    "TASK_SUBLEDGER_CUTOFF",
                    "Subledger Cut-off",
                    CloseTaskKind.SUBLEDGER_CUTOFF,
                    true
            ),
            new CloseTask(
                    "TASK_ACCRUALS",
                    "Post Accruals",
                    CloseTaskKind.ACCRUALS,
                    true
            ),
            new CloseTask(
                    "TASK_DEPRECIATION",
                    "Fixed Assets Depreciation",
                    CloseTaskKind.DEPRECIATION,
                    true
            ),
            new CloseTask(
                    "TASK_FX_REVALUATION",
                    "Multi-Currency FX Revaluation",
                    CloseTaskKind.FX_REVALUATION,
                    false
            ),
            new CloseTask(
                    "TASK_RECONCILIATION",
                    "Subledger & Bank Reconciliation",
                    CloseTaskKind.RECONCILIATION,
                    true
            )
    ));

    if (syirkahEnabled) {
        tasks.add(new CloseTask(
                "TASK_SYIRKAH_RESULT",
                "Calculate Syirkah Financial Result",
                CloseTaskKind.SYIRKAH_RESULT,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_ALLOCATION",
                "Allocate Syirkah Result",
                CloseTaskKind.SYIRKAH_ALLOCATION,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_DISTRIBUTION",
                "Post Syirkah Distribution",
                CloseTaskKind.SYIRKAH_DISTRIBUTION,
                true
        ));
    }

    tasks.add(new CloseTask(
            "TASK_LOCK",
            "General Ledger Hard Lock",
            CloseTaskKind.LOCK,
            true
    ));

    return new CloseCycle(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            tasks
    );
}
```

This preserves backward compatibility: existing ordinary accounting closes still use the original four/five operational tasks.

---

## 7. Resulting `tutup buku` state machine

We now have a clean domain boundary:

```text
                 ORDINARY CLOSE
                       │
                       ▼
              RECONCILIATION
                       │
                       ▼
                     LOCK


                 SYIRKAH CLOSE
                       │
                       ▼
              RECONCILIATION
                       │
                       ▼
              SYIRKAH_RESULT
                       │
                       ▼
            SYIRKAH_ALLOCATION
                       │
                       ▼
          SYIRKAH_DISTRIBUTION
                       │
                       ▼
                   VALIDATING
                       │
                       ▼
                   APPROVED
                       │
                       ▼
                     LOCK
```

And the critical invariant is now:

```text
SYIRKAH_DISTRIBUTION
        ≠
"we calculated a distribution"

SYIRKAH_DISTRIBUTION
        =
"the approved distribution accounting entry was successfully posted"
```

That distinction is important for reliable `tutup buku`.

The next tranche should be the **actual `SyirkahCloseOrchestrator` implementation**, including result → allocation → distribution task completion and idempotent retry behavior, followed by the focused unit tests for profit, loss, approval, tenant isolation, and duplicate posting.

////////////


The framework file confirms the existing close aggregate is still the original implementation, so the Syirkah extension should be layered onto it rather than replacing it. syirkah-framework(1)

The next implementation tranche is the **close orchestrator**. The important rule is:

> `TASK_SYIRKAH_DISTRIBUTION` is completed only after the distribution journal has successfully posted.

### 1. Add Syirkah task constants

```java
package tech.kayys.syirkah.accounting.domain.close;

public final class SyirkahCloseTaskCodes {

    public static final String RESULT = "TASK_SYIRKAH_RESULT";
    public static final String ALLOCATION = "TASK_SYIRKAH_ALLOCATION";
    public static final String DISTRIBUTION = "TASK_SYIRKAH_DISTRIBUTION";

    private SyirkahCloseTaskCodes() {
    }
}
```

Extend `CloseTaskKind`:

```java
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,

    SYIRKAH_RESULT,
    SYIRKAH_ALLOCATION,
    SYIRKAH_DISTRIBUTION,

    LOCK
}
```

And preserve the existing `CloseCycle.open(...)` API while adding the Syirkah-aware overload:

```java
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId) {

    return open(id, tenantId, ledgerId, fiscalPeriodId, false);
}

public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        boolean syirkahEnabled) {

    List<CloseTask> tasks = new ArrayList<>(List.of(
        new CloseTask(
            "TASK_SUBLEDGER_CUTOFF",
            "Subledger Cut-off",
            CloseTaskKind.SUBLEDGER_CUTOFF,
            true
        ),
        new CloseTask(
            "TASK_ACCRUALS",
            "Post Accruals",
            CloseTaskKind.ACCRUALS,
            true
        ),
        new CloseTask(
            "TASK_DEPRECIATION",
            "Fixed Assets Depreciation",
            CloseTaskKind.DEPRECIATION,
            true
        ),
        new CloseTask(
            "TASK_FX_REVALUATION",
            "Multi-Currency FX Revaluation",
            CloseTaskKind.FX_REVALUATION,
            false
        ),
        new CloseTask(
            "TASK_RECONCILIATION",
            "Subledger & Bank Reconciliation",
            CloseTaskKind.RECONCILIATION,
            true
        )
    ));

    if (syirkahEnabled) {
        tasks.add(new CloseTask(
            SyirkahCloseTaskCodes.RESULT,
            "Calculate Syirkah Financial Result",
            CloseTaskKind.SYIRKAH_RESULT,
            true
        ));

        tasks.add(new CloseTask(
            SyirkahCloseTaskCodes.ALLOCATION,
            "Allocate Syirkah Result",
            CloseTaskKind.SYIRKAH_ALLOCATION,
            true
        ));

        tasks.add(new CloseTask(
            SyirkahCloseTaskCodes.DISTRIBUTION,
            "Post Syirkah Distribution",
            CloseTaskKind.SYIRKAH_DISTRIBUTION,
            true
        ));
    }

    tasks.add(new CloseTask(
        "TASK_LOCK",
        "General Ledger Hard Lock",
        CloseTaskKind.LOCK,
        true
    ));

    return new CloseCycle(id, tenantId, ledgerId, fiscalPeriodId, tasks);
}
```

This keeps ordinary accounting closes completely unchanged.

---

## 2. Make task completion strict

The current `CloseTask.complete()` can technically complete a task directly from `PENDING`. That is too permissive for the Syirkah workflow.

Change it to:

```java
public void complete(String user) {
    Objects.requireNonNull(user, "completedBy user must not be null");

    if (status != CloseTaskStatus.IN_PROGRESS) {
        throw new IllegalStateException(
            "Task must be IN_PROGRESS before completion: " + taskCode
        );
    }

    this.completedBy = user;
    this.completedAt = Instant.now();
    this.status = CloseTaskStatus.COMPLETED;
}
```

Then every orchestrated operation follows:

```text
PENDING
   ↓
start()
   ↓
IN_PROGRESS
   ↓
complete()
   ↓
COMPLETED
```

A failed operation leaves the task `IN_PROGRESS`, allowing a controlled retry.

---

# 3. Syirkah close orchestrator

The orchestrator should own **workflow sequencing**, while the domain services continue owning the actual calculations and accounting rules.

```java
package tech.kayys.syirkah.accounting.application.syirkah.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseCycleId;
import tech.kayys.syirkah.accounting.domain.close.CloseTask;
import tech.kayys.syirkah.accounting.domain.close.SyirkahCloseTaskCodes;

import java.util.Objects;

public final class SyirkahCloseOrchestrator {

    private final CloseCycleRepository closeCycleRepository;
    private final SyirkahCloseProcessor closeProcessor;
    private final SyirkahDistributionPreparationService distributionPreparationService;
    private final SyirkahDistributionApprovalService distributionApprovalService;
    private final SyirkahDistributionPostingService distributionPostingService;

    public SyirkahCloseOrchestrator(
            CloseCycleRepository closeCycleRepository,
            SyirkahCloseProcessor closeProcessor,
            SyirkahDistributionPreparationService distributionPreparationService,
            SyirkahDistributionApprovalService distributionApprovalService,
            SyirkahDistributionPostingService distributionPostingService) {

        this.closeCycleRepository =
            Objects.requireNonNull(closeCycleRepository);

        this.closeProcessor =
            Objects.requireNonNull(closeProcessor);

        this.distributionPreparationService =
            Objects.requireNonNull(distributionPreparationService);

        this.distributionApprovalService =
            Objects.requireNonNull(distributionApprovalService);

        this.distributionPostingService =
            Objects.requireNonNull(distributionPostingService);
    }

    public Uni<SyirkahCloseResult> execute(
            SyirkahCloseProcessor.CloseRequest request,
            String operator) {

        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(operator, "operator must not be null");

        return closeCycleRepository
            .findById(CloseCycleId.of(request.closeCycleId()))
            .onItem()
            .ifNull()
            .failWith(() ->
                new IllegalArgumentException(
                    "Close cycle not found: " + request.closeCycleId()
                )
            )
            .flatMap(cycle ->
                executeResultTask(cycle, request, operator)
            )
            .flatMap(state ->
                executeAllocationTask(
                    state.cycle(),
                    state.request(),
                    state.operator(),
                    state.calculation()
                )
            )
            .flatMap(state ->
                executeDistributionTask(
                    state.cycle(),
                    state.request(),
                    state.operator(),
                    state.calculation()
                )
            );
    }

    private Uni<ExecutionState> executeResultTask(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request,
            String operator) {

        CloseTask task = requiredTask(
            cycle,
            SyirkahCloseTaskCodes.RESULT
        );

        if (task.status().isCompleted()) {
            return closeProcessor
                .loadCalculationByCloseCycle(request.closeCycleId())
                .map(calculation ->
                    new ExecutionState(
                        cycle,
                        request,
                        operator,
                        calculation
                    )
                );
        }

        task.start();

        return closeProcessor
            .calculateResult(request)
            .flatMap(calculation ->
                closeCycleRepository.save(cycle)
                    .replaceWith(
                        new ExecutionState(
                            cycle,
                            request,
                            operator,
                            calculation
                        )
                    )
            );
    }

    private Uni<ExecutionState> executeAllocationTask(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request,
            String operator,
            SyirkahCloseProcessor.CloseCalculation calculation) {

        CloseTask task = requiredTask(
            cycle,
            SyirkahCloseTaskCodes.ALLOCATION
        );

        if (task.status().isCompleted()) {
            return Uni.createFrom().item(
                new ExecutionState(
                    cycle,
                    request,
                    operator,
                    calculation
                )
            );
        }

        task.start();

        return closeProcessor
            .allocateResult(request, calculation)
            .flatMap(updatedCalculation ->
                closeCycleRepository.save(cycle)
                    .replaceWith(
                        new ExecutionState(
                            cycle,
                            request,
                            operator,
                            updatedCalculation
                        )
                    )
            );
    }

    private Uni<SyirkahCloseResult> executeDistributionTask(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request,
            String operator,
            SyirkahCloseProcessor.CloseCalculation calculation) {

        CloseTask task = requiredTask(
            cycle,
            SyirkahCloseTaskCodes.DISTRIBUTION
        );

        if (task.status().isCompleted()) {
            return Uni.createFrom().item(
                SyirkahCloseResult.alreadyCompleted(
                    request.closeCycleId(),
                    calculation.calculationId()
                )
            );
        }

        task.start();

        return distributionPreparationService
            .prepare(
                request,
                calculation
            )
            .flatMap(obligations ->
                distributionPostingService.post(
                    request,
                    calculation.calculationId(),
                    obligations,
                    operator
                )
            )
            .flatMap(journalEntryId -> {
                task.complete(operator);

                return closeCycleRepository
                    .save(cycle)
                    .replaceWith(
                        SyirkahCloseResult.completed(
                            request.closeCycleId(),
                            calculation.calculationId(),
                            journalEntryId.value()
                        )
                    );
            });
    }

    private CloseTask requiredTask(
            CloseCycle cycle,
            String taskCode) {

        return cycle.findTask(taskCode)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Required Syirkah close task is missing: " + taskCode
                )
            );
    }

    private record ExecutionState(
            CloseCycle cycle,
            SyirkahCloseProcessor.CloseRequest request,
            String operator,
            SyirkahCloseProcessor.CloseCalculation calculation
    ) {
    }
}
```

There is one important refinement here: `CloseTaskStatus` currently does not have `isCompleted()`. Add it rather than scattering enum comparisons:

```java
public enum CloseTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED;

    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
```

---

# 4. Result object

```java
package tech.kayys.syirkah.accounting.application.syirkah.close;

import java.util.Objects;

public record SyirkahCloseResult(
        String closeCycleId,
        String calculationId,
        String journalEntryId,
        boolean alreadyCompleted) {

    public SyirkahCloseResult {
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(calculationId);
    }

    public static SyirkahCloseResult completed(
            String closeCycleId,
            String calculationId,
            String journalEntryId) {

        return new SyirkahCloseResult(
            closeCycleId,
            calculationId,
            journalEntryId,
            false
        );
    }

    public static SyirkahCloseResult alreadyCompleted(
            String closeCycleId,
            String calculationId) {

        return new SyirkahCloseResult(
            closeCycleId,
            calculationId,
            null,
            true
        );
    }
}
```

---

# 5. Approval must remain a separate operation

The orchestrator should **not** approve the partner distribution automatically.

The lifecycle becomes:

```text
ACCOUNTING CLOSE
       │
       ▼
SYIRKAH RESULT
       │
       ▼
SYIRKAH ALLOCATION
       │
       ▼
DISTRIBUTION OBLIGATIONS
       │
       ▼
       ├── CALCULATED
       │
       ▼
Maker/Checker approval
       │
       ▼
       APPROVED
       │
       ▼
Accounting policy
       │
       ▼
GL journal posting
       │
       ▼
       POSTED
       │
       ▼
TASK_SYIRKAH_DISTRIBUTION = COMPLETED
       │
       ▼
CloseCycle validation
       │
       ▼
Approval
       │
       ▼
Ledger lock
```

So I would actually make one adjustment to the orchestrator above:

**distribution preparation and distribution posting should not be one automatic action if maker-checker is required.**

The safer production API is:

```java
public Uni<CloseCalculationView> calculate(
        SyirkahCloseProcessor.CloseRequest request,
        String operator);

public Uni<List<PartnerDistributionObligation>> prepareDistribution(
        SyirkahCloseProcessor.CloseRequest request,
        String operator);

public Uni<List<PartnerDistributionObligation>> approveDistribution(
        String calculationId,
        String approver);

public Uni<String> postDistribution(
        SyirkahCloseProcessor.CloseRequest request,
        String calculationId,
        String poster);
```

Then:

```text
calculate()
    ↓
result task complete

prepareDistribution()
    ↓
allocation task complete

approveDistribution()
    ↓
maker/checker

postDistribution()
    ↓
GL POSTED
    ↓
distribution task complete
```

This is more appropriate for a real accounting system because **calculation, authorization, and accounting posting are separate controls**.

---

# 6. Critical idempotency rule

The existing `JournalEntryRepository` currently only exposes:

```java
Uni<Optional<JournalEntry>> findById(JournalEntryId id);

Uni<List<JournalEntry>> findAll();

Uni<Void> save(JournalEntry entry);
```

The source-based lookup needs to be added:

```java
Uni<Optional<JournalEntry>> findBySource(
        String sourceType,
        String sourceId);
```

Then `GeneralLedgerService` must enforce:

```java
public Uni<JournalEntryId> postJournalEntry(
        PostJournalEntryCommand command) {

    return journalEntryRepository
        .findBySource(
            command.sourceType(),
            command.sourceId()
        )
        .flatMap(existing -> {

            if (existing.isPresent()) {
                JournalEntry entry = existing.get();

                if (entry.status() == JournalEntry.EntryStatus.POSTED) {
                    return Uni.createFrom()
                        .item(entry.id());
                }

                return Uni.createFrom()
                    .failure(
                        new IllegalStateException(
                            "A journal already exists for source but is not posted: "
                                + command.sourceType()
                                + "/"
                                + command.sourceId()
                        )
                    );
            }

            return createAndPost(command);
        });
}
```

The database should additionally have a uniqueness constraint equivalent to:

```text
(tenant_id, ledger_id, source_type, source_id)
```

That protects against two concurrent close workers both reaching the posting boundary.

---

## 7. One more important concurrency correction

`CloseTask.start()` currently permits:

```text
PENDING      → IN_PROGRESS
IN_PROGRESS  → IN_PROGRESS
BLOCKED      → IN_PROGRESS
```

For close processing, I recommend:

```java
public void start() {
    if (status == CloseTaskStatus.COMPLETED) {
        throw new IllegalStateException(
            "Task is already completed: " + taskCode
        );
    }

    if (status == CloseTaskStatus.IN_PROGRESS) {
        throw new IllegalStateException(
            "Task is already in progress: " + taskCode
        );
    }

    if (status == CloseTaskStatus.BLOCKED) {
        throw new IllegalStateException(
            "Blocked task must be explicitly unblocked: " + taskCode
        );
    }

    this.status = CloseTaskStatus.IN_PROGRESS;
}
```

That prevents two workers from executing the same close task simultaneously.

The **database-level unique/idempotency boundary is still required**; domain-state protection alone cannot solve distributed concurrency.

---

## 8. Test matrix for this tranche

The next tests should specifically prove these invariants:

```text
SyirkahCloseOrchestratorTest

✓ result task starts
✓ result calculation is persisted
✓ result task becomes COMPLETED
✓ allocation cannot run before result
✓ allocation task becomes COMPLETED
✓ distribution preparation creates one obligation per positive allocation
✓ distribution preparation is idempotent
✓ unapproved distribution cannot be posted
✓ approved distribution can be posted
✓ successful GL posting marks obligations POSTED
✓ distribution task becomes COMPLETED only after GL success
✓ failed GL posting leaves obligations APPROVED
✓ failed GL posting leaves distribution task incomplete
✓ retry after failed GL posting succeeds
✓ duplicate GL posting returns original journal ID
✓ completed close task is not recalculated
✓ tenantId is propagated into financial-result calculation
✓ ledgerId is propagated into financial-result calculation
✓ wrong tenant cannot resolve another tenant's contract
✓ close approval is blocked while Syirkah tasks remain incomplete
✓ close lock is impossible before approval
```

The most important test is:

```java
@Test
void distributionTaskMustNotCompleteWhenJournalPostingFails() {
    // arrange:
    // - allocation is APPROVED
    // - GL posting fails

    // act:
    // orchestrator.postDistribution(...)

    // assert:
    // task.status() == IN_PROGRESS
    // obligation.status() == APPROVED
    // obligation.postedJournalEntryId() == null
}
```

That invariant is what prevents **“tutup buku” from claiming a successful Syirkah distribution when the accounting entry never actually reached the ledger**.

One source limitation is worth noting: the uploaded framework snapshot does **not yet contain the Syirkah orchestrator/classes described in the preceding design**, while it does contain the existing `CloseCycle`, `CloseTask`, and `JournalEntryRepository`. syirkah-framework(1) syirkah-framework(1) So the code above is the extension layer to add next, rather than a claim that those Syirkah classes are already present in the uploaded snapshot.

////////////

The next correction is important: the current `GeneralLedgerService` still creates `JournalEntry` with the default tenant/ledger and mutates accounts **before** persisting the journal. That is unsafe for Syirkah close retries and concurrent workers. syirkah-framework(1)

## 1. Make posting context explicit

Extend the command:

```java id="51824"
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        String tenantId,
        String ledgerId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {

    public PostJournalEntryCommand {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(description, "description");

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal entry must have at least one line");
        }

        if (sourceType == null ^ sourceId == null) {
            throw new IllegalArgumentException(
                    "sourceType and sourceId must either both be provided or both be absent");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private JournalEntryId journalEntryId;
        private String tenantId;
        private String ledgerId;
        private String description;
        private String referenceNumber;
        private List<JournalLineCommand> lines;
        private String sourceType;
        private String sourceId;
        private String postedBy;

        public Builder journalEntryId(JournalEntryId value) {
            this.journalEntryId = value;
            return this;
        }

        public Builder tenantId(String value) {
            this.tenantId = value;
            return this;
        }

        public Builder ledgerId(String value) {
            this.ledgerId = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder referenceNumber(String value) {
            this.referenceNumber = value;
            return this;
        }

        public Builder lines(List<JournalLineCommand> value) {
            this.lines = value;
            return this;
        }

        public Builder sourceType(String value) {
            this.sourceType = value;
            return this;
        }

        public Builder sourceId(String value) {
            this.sourceId = value;
            return this;
        }

        public Builder postedBy(String value) {
            this.postedBy = value;
            return this;
        }

        public PostJournalEntryCommand build() {
            return new PostJournalEntryCommand(
                    journalEntryId != null
                            ? journalEntryId
                            : JournalEntryId.generate(),
                    tenantId,
                    ledgerId,
                    description,
                    referenceNumber,
                    lines,
                    sourceType,
                    sourceId,
                    postedBy
            );
        }
    }
}
```

The existing command already carries `sourceType` and `sourceId`, but currently does not carry tenant/ledger context. syirkah-framework(1)

---

## 2. Preserve posting source on `JournalEntry`

Add:

```java id="w3n9t6"
private String sourceType;
private String sourceId;

public void setPostingSource(String sourceType, String sourceId) {
    if (status != EntryStatus.DRAFT) {
        throw new IllegalStateException(
                "Cannot modify posting source of non-draft journal entry");
    }

    if ((sourceType == null) != (sourceId == null)) {
        throw new IllegalArgumentException(
                "sourceType and sourceId must both be provided");
    }

    this.sourceType = sourceType;
    this.sourceId = sourceId;
}

public String getSourceType() {
    return sourceType;
}

public String getSourceId() {
    return sourceId;
}
```

And when creating a reversal:

```java id="6s6m9d"
reversal.sourceType = this.sourceType;
reversal.sourceId = this.sourceId;
```

This is especially important because the current reversal implementation already preserves tenant, ledger, and Sharia contract metadata, but not the new source identity. syirkah-framework(1)

---

# 3. Add source lookup to the repository

```java id="6t3g7a"
public interface JournalEntryRepository {

    Uni<Optional<JournalEntry>> findById(JournalEntryId id);

    Uni<List<JournalEntry>> findAll();

    Uni<Optional<JournalEntry>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId);

    Uni<Void> save(JournalEntry entry);
}
```

Including tenant and ledger in the lookup is preferable to:

```text
sourceType + sourceId
```

because source IDs can only be considered globally unique **inside the accounting context**.

For Syirkah:

```text
tenant
  +
ledger
  +
SYIRKAH_DISTRIBUTION
  +
calculationId
```

becomes the idempotency key.

---

# 4. Correct `GeneralLedgerService`

The first stage should be:

```java id="0m4q5r"
public Uni<JournalEntryId> postJournalEntry(
        PostJournalEntryCommand command) {

    return journalEntryRepository.findBySource(
            command.tenantId(),
            command.ledgerId(),
            command.sourceType(),
            command.sourceId()
    ).flatMap(existing -> {

        if (existing.isPresent()) {
            JournalEntry entry = existing.get();

            if (entry.getStatus() == JournalEntry.EntryStatus.POSTED) {
                return Uni.createFrom().item(entry.getId());
            }

            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "Posting source already exists but journal is not posted: "
                                    + command.sourceType()
                                    + "/"
                                    + command.sourceId()
                    )
            );
        }

        return createAndPost(command);
    });
}
```

Then:

```java id="3r0xk5"
private Uni<JournalEntryId> createAndPost(
        PostJournalEntryCommand command) {

    JournalEntryId entryId =
            command.journalEntryId() != null
                    ? command.journalEntryId()
                    : JournalEntryId.generate();

    JournalEntry entry = new JournalEntry(
            entryId,
            TenantId.of(command.tenantId()),
            LedgerId.of(command.ledgerId()),
            command.referenceNumber() != null
                    ? command.referenceNumber()
                    : "JE-" + UUID.randomUUID()
                            .toString()
                            .substring(0, 8),
            Instant.now(),
            command.description()
    );

    entry.setPostingSource(
            command.sourceType(),
            command.sourceId()
    );

    for (PostJournalEntryCommand.JournalLineCommand line
            : command.lines()) {

        AccountId accountId = AccountId.of(line.accountId());

        Money amount = Money.of(
                new BigDecimal(line.amount()),
                line.currencyCode()
        );

        Money debit =
                "DEBIT".equalsIgnoreCase(line.type())
                        ? amount
                        : Money.zero(line.currencyCode());

        Money credit =
                "CREDIT".equalsIgnoreCase(line.type())
                        ? amount
                        : Money.zero(line.currencyCode());

        entry.addLine(
                accountId,
                debit,
                credit,
                line.description()
        );
    }

    complianceEngine.validateJournalEntry(entry);
    entry.post();

    LocalDate transactionDate =
            LocalDate.ofInstant(
                    entry.getEntryDate(),
                    ZoneOffset.UTC
            );

    return fiscalPeriodRepository
            .findActivePeriod(transactionDate)
            .chain(period -> {

                if (period.isPresent()
                        && !period.get().isOpen()
                        && complianceEngine
                            .getConfiguration()
                            .strictPeriodLocking()) {

                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Cannot post entry into closed or locked fiscal period: "
                                            + period.get().getPeriodName()
                            )
                    );
                }

                return applyAccountEffects(entry)
                        .chain(() ->
                                journalEntryRepository.save(entry))
                        .replaceWith(entryId);
            });
}
```

---

# 5. But there is still a concurrency problem

The following is **not enough**:

```text
Worker A: findBySource → empty
Worker B: findBySource → empty

Worker A: mutate accounts
Worker B: mutate accounts

Worker A: save journal
Worker B: save journal
```

That can double-apply account balances.

Therefore the real persistence boundary needs:

```text
BEGIN TRANSACTION

  verify source uniqueness
  create journal
  update account balances
  persist journal

COMMIT
```

with a database constraint:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id
)
```

The application-level lookup is useful for normal retries; the database constraint is the final concurrency protection.

---

# 6. Introduce a posting transaction port

Don't make `GeneralLedgerService` depend directly on a specific database.

```java id="q7c1vd"
public interface JournalPostingTransaction {

    Uni<JournalEntryId> post(
            PostJournalEntryCommand command,
            JournalEntry entry);
}
```

A database adapter can then guarantee:

```text
source uniqueness
+
account mutations
+
journal persistence
+
transaction commit
```

This also makes the Syirkah posting service independent of whether accounting eventually runs:

- in-memory,
- PostgreSQL,
- another relational database,
- a service boundary.

---

# 7. Syirkah distribution command

Now the Syirkah posting service becomes very clean:

```java id="7l5c1a"
public Uni<JournalEntryId> post(
        SyirkahCloseProcessor.CloseRequest request,
        String calculationId,
        List<PartnerDistributionObligation> obligations,
        String postedBy) {

    requireApproved(obligations);

    SyirkahDistributionAccountingPolicy.JournalMapping mapping =
            accountingPolicy.map(obligations);

    List<PostJournalEntryCommand.JournalLineCommand> lines =
            mapping.lines()
                    .stream()
                    .map(line ->
                            new PostJournalEntryCommand.JournalLineCommand(
                                    UUID.fromString(line.accountId()),
                                    line.type().name(),
                                    line.amount(),
                                    line.currencyCode(),
                                    line.description()
                            ))
                    .toList();

    PostJournalEntryCommand command =
            PostJournalEntryCommand.builder()
                    .tenantId(request.tenantId())
                    .ledgerId(request.ledgerId())
                    .description(mapping.description())
                    .referenceNumber("SYIRKAH-" + calculationId)
                    .lines(lines)
                    .sourceType("SYIRKAH_DISTRIBUTION")
                    .sourceId(calculationId)
                    .postedBy(postedBy)
                    .build();

    return generalLedgerService
            .postJournalEntry(command)
            .flatMap(journalEntryId ->
                    markPosted(
                            obligations,
                            journalEntryId,
                            postedBy
                    )
                    .replaceWith(journalEntryId)
            );
}
```

Notice the critical identity:

```text
sourceType = SYIRKAH_DISTRIBUTION
sourceId   = calculationId
```

not the close-cycle ID.

That means retrying the same calculation cannot create a second distribution journal.

---

# 8. Close task must be completed after posting

The final sequencing should be:

```text
prepare calculation
       │
       ▼
persist calculation
       │
       ▼
TASK_SYIRKAH_RESULT = COMPLETED
       │
       ▼
allocate result
       │
       ▼
persist allocations
       │
       ▼
TASK_SYIRKAH_ALLOCATION = COMPLETED
       │
       ▼
create obligations
       │
       ▼
maker/checker approval
       │
       ▼
post journal
       │
       ├──── failure ────► task remains IN_PROGRESS
       │
       ▼
mark obligations POSTED
       │
       ▼
TASK_SYIRKAH_DISTRIBUTION = COMPLETED
```

This is the correct accounting invariant.

---

## 9. One issue with the previous orchestrator

I would **not** keep the earlier `execute()` implementation exactly as written.

It combined:

```text
prepare distribution
+
approval
+
posting
```

too closely.

For a production accounting platform, use three explicit application operations:

```java id="x6k9w2"
public Uni<CloseCalculationView> calculate(
        CloseRequest request,
        String operator);

public Uni<List<PartnerDistributionObligation>> prepareDistribution(
        CloseRequest request,
        String operator);

public Uni<List<PartnerDistributionObligation>> approveDistribution(
        String calculationId,
        String approver);

public Uni<JournalEntryId> postDistribution(
        CloseRequest request,
        String calculationId,
        String poster);
```

This gives you proper **maker-checker separation of duties**.

The close cycle itself then remains responsible only for:

```text
OPEN
  ↓
VALIDATING
  ↓
APPROVED
  ↓
LOCKED
```

which matches the existing `CloseCycle` design. syirkah-framework(1)

### Next tranche

The next step should be the **Syirkah result/allocation persistence model and immutable close snapshot**.

That is important because once `tutup buku` reaches `VALIDATING`, the result must represent the exact financial state and contract version used for the calculation—not something that can silently change if journal data or a Syirkah agreement is modified afterward.

///////////

Yes. The next tranche should make **`tutup buku` deterministic**: after Syirkah calculation, we persist an immutable snapshot containing the exact financial result, contract version, partner agreements, and allocations used.

The existing `CloseCycle` already provides the correct close lifecycle, so this is an extension rather than a replacement. syirkah-framework(1)

## 1. Add immutable `SyirkahCloseSnapshot`

Create:

`finance/accounting/syirkah-accounting-domain/src/main/java/tech/kayys/syirkah/accounting/domain/islamic/syirkah/SyirkahCloseSnapshot.java`

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import tech.kayys.syirkah.accounting.domain.islamic.syirkah.SyirkahResultType;

public final class SyirkahCloseSnapshot {

    private final SyirkahCalculationId calculationId;
    private final String closeCycleId;

    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;

    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    private final String contractId;
    private final int contractVersion;

    private final BigDecimal revenue;
    private final BigDecimal directCosts;
    private final BigDecimal operatingCosts;
    private final BigDecimal otherCosts;
    private final BigDecimal netResult;

    private final SyirkahResultType resultType;

    private final List<PartySnapshot> parties;
    private final List<AllocationSnapshot> allocations;
    private final List<AllocationSnapshot> lossAllocations;

    private final String calculationReference;

    public SyirkahCloseSnapshot(
            SyirkahCalculationId calculationId,
            String closeCycleId,
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String contractId,
            int contractVersion,
            BigDecimal revenue,
            BigDecimal directCosts,
            BigDecimal operatingCosts,
            BigDecimal otherCosts,
            BigDecimal netResult,
            SyirkahResultType resultType,
            List<PartySnapshot> parties,
            List<AllocationSnapshot> allocations,
            List<AllocationSnapshot> lossAllocations,
            String calculationReference) {

        this.calculationId = Objects.requireNonNull(calculationId, "calculationId");
        this.closeCycleId = requireText(closeCycleId, "closeCycleId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.fiscalPeriodId = requireText(fiscalPeriodId, "fiscalPeriodId");

        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd cannot be before periodStart");
        }

        this.contractId = requireText(contractId, "contractId");

        if (contractVersion <= 0) {
            throw new IllegalArgumentException("contractVersion must be positive");
        }

        this.contractVersion = contractVersion;

        this.revenue = nonNegative(revenue, "revenue");
        this.directCosts = nonNegative(directCosts, "directCosts");
        this.operatingCosts = nonNegative(operatingCosts, "operatingCosts");
        this.otherCosts = nonNegative(otherCosts, "otherCosts");

        this.netResult = Objects.requireNonNull(netResult, "netResult");
        this.resultType = Objects.requireNonNull(resultType, "resultType");

        this.parties = List.copyOf(
                Objects.requireNonNull(parties, "parties"));

        this.allocations = List.copyOf(
                Objects.requireNonNull(allocations, "allocations"));

        this.lossAllocations = List.copyOf(
                Objects.requireNonNull(lossAllocations, "lossAllocations"));

        this.calculationReference =
                requireText(calculationReference, "calculationReference");

        validateResult();
    }

    private void validateResult() {
        BigDecimal expected = revenue
                .subtract(directCosts)
                .subtract(operatingCosts)
                .subtract(otherCosts);

        if (expected.compareTo(netResult) != 0) {
            throw new IllegalArgumentException(
                    "netResult does not match financial result components");
        }

        if (resultType == SyirkahResultType.PROFIT && netResult.signum() <= 0) {
            throw new IllegalArgumentException(
                    "PROFIT snapshot must have a positive netResult");
        }

        if (resultType == SyirkahResultType.LOSS && netResult.signum() >= 0) {
            throw new IllegalArgumentException(
                    "LOSS snapshot must have a negative netResult");
        }

        if (resultType == SyirkahResultType.BREAK_EVEN
                && netResult.signum() != 0) {
            throw new IllegalArgumentException(
                    "BREAK_EVEN snapshot must have zero netResult");
        }
    }

    private static BigDecimal nonNegative(
            BigDecimal value,
            String field) {

        Objects.requireNonNull(value, field);

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }

        return value;
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }

        return value;
    }

    public SyirkahCalculationId calculationId() {
        return calculationId;
    }

    public String closeCycleId() {
        return closeCycleId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String ledgerId() {
        return ledgerId;
    }

    public String fiscalPeriodId() {
        return fiscalPeriodId;
    }

    public LocalDate periodStart() {
        return periodStart;
    }

    public LocalDate periodEnd() {
        return periodEnd;
    }

    public String contractId() {
        return contractId;
    }

    public int contractVersion() {
        return contractVersion;
    }

    public BigDecimal revenue() {
        return revenue;
    }

    public BigDecimal directCosts() {
        return directCosts;
    }

    public BigDecimal operatingCosts() {
        return operatingCosts;
    }

    public BigDecimal otherCosts() {
        return otherCosts;
    }

    public BigDecimal netResult() {
        return netResult;
    }

    public SyirkahResultType resultType() {
        return resultType;
    }

    public List<PartySnapshot> parties() {
        return parties;
    }

    public List<AllocationSnapshot> allocations() {
        return allocations;
    }

    public List<AllocationSnapshot> lossAllocations() {
        return lossAllocations;
    }

    public String calculationReference() {
        return calculationReference;
    }

    public record PartySnapshot(
            String partyId,
            String role,
            BigDecimal capitalContribution,
            BigDecimal profitSharingRatio) {

        public PartySnapshot {
            if (partyId == null || partyId.isBlank()) {
                throw new IllegalArgumentException("partyId cannot be blank");
            }
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(capitalContribution, "capitalContribution");
            Objects.requireNonNull(profitSharingRatio, "profitSharingRatio");

            if (capitalContribution.signum() < 0) {
                throw new IllegalArgumentException(
                        "capitalContribution cannot be negative");
            }

            if (profitSharingRatio.signum() < 0
                    || profitSharingRatio.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(
                        "profitSharingRatio must be between 0 and 100");
            }
        }
    }

    public record AllocationSnapshot(
            String partyId,
            BigDecimal amount,
            String basis) {

        public AllocationSnapshot {
            if (partyId == null || partyId.isBlank()) {
                throw new IllegalArgumentException("partyId cannot be blank");
            }

            Objects.requireNonNull(amount, "amount");

            if (amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "allocation amount cannot be negative");
            }

            if (basis == null || basis.isBlank()) {
                throw new IllegalArgumentException("basis cannot be blank");
            }
        }
    }
}
```

The important part is that this is **not a live reference to the contract**.

If the partner agreement later changes from:

```text
Partner A = 40%
Partner B = 60%
```

to:

```text
Partner A = 50%
Partner B = 50%
```

the already-calculated close snapshot still contains `40/60`.

---

# 2. Make the result type explicit

If the current implementation already has a `ResultType` nested in `ProfitSharingResult`, don't introduce a second competing enum. Extract/reuse it.

If extraction is appropriate in the current module:

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

public enum SyirkahResultType {
    PROFIT,
    LOSS,
    BREAK_EVEN
}
```

Then change the existing result model to use this common type.

This avoids eventually having:

```text
ProfitSharingResult.ResultType
SyirkahCloseProcessor.ResultType
SyirkahCloseSnapshot.ResultType
```

representing the same business concept.

---

# 3. Add a repository specifically for the immutable snapshot

Create:

`finance/accounting/syirkah-accounting-domain/src/main/java/tech/kayys/syirkah/accounting/domain/islamic/syirkah/SyirkahCloseSnapshotRepository.java`

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findByCalculationId(
            String tenantId,
            String ledgerId,
            String calculationId);

    Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycleId(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<Void> save(SyirkahCloseSnapshot snapshot);
}
```

Notice that every lookup is tenant + ledger scoped.

That is important because a calculation ID must never become a cross-tenant lookup key.

---

# 4. Add an immutable creation service

Create:

`SyirkahCloseSnapshotFactory.java`

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class SyirkahCloseSnapshotFactory {

    public SyirkahCloseSnapshot create(
            SyirkahCloseProcessor.CloseRequest request,
            SyirkahCloseProcessor.CloseCalculation calculation,
            List<SyirkahCloseSnapshot.PartySnapshot> parties,
            List<SyirkahCloseSnapshot.AllocationSnapshot> allocations,
            List<SyirkahCloseSnapshot.AllocationSnapshot> lossAllocations) {

        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(calculation, "calculation");

        return new SyirkahCloseSnapshot(
                SyirkahCalculationId.of(calculation.calculationId()),
                request.closeCycleId(),
                request.tenantId(),
                request.ledgerId(),
                request.fiscalPeriodId(),
                request.periodStart(),
                request.periodEnd(),
                calculation.contractId(),
                calculation.contractVersion(),
                calculation.revenue(),
                calculation.directCosts(),
                calculation.operatingCosts(),
                calculation.otherCosts(),
                calculation.netResult(),
                calculation.resultType(),
                parties,
                allocations,
                lossAllocations,
                calculation.calculationReference()
        );
    }
}
```

This exposes an issue in the current `CloseCalculation`: it needs to carry the full financial components.

So extend it from the current shape:

```java
CloseCalculation {
    calculationId
    contractId
    contractVersion
    netResult
    resultType
    allocations
    calculationReference
}
```

to:

```java
CloseCalculation {
    calculationId
    contractId
    contractVersion

    revenue
    directCosts
    operatingCosts
    otherCosts
    netResult

    resultType
    allocations
    calculationReference
}
```

Do this as an additive extension rather than creating another financial-result object.

---

# 5. Make `calculate()` idempotent

The processor should no longer recalculate an already persisted close.

The important behavior is:

```text
calculate
   |
   +-- snapshot already exists?
   |       |
   |       +-- YES -> return existing snapshot
   |
   +-- NO
        |
        +-- resolve contract version
        +-- calculate financial result
        +-- calculate allocation
        +-- create immutable snapshot
        +-- persist snapshot
        +-- return snapshot
```

Application service:

```java
public Uni<SyirkahCloseSnapshot> calculate(
        SyirkahCloseProcessor.CloseRequest request) {

    return snapshotRepository
            .findByCloseCycleId(
                    request.tenantId(),
                    request.ledgerId(),
                    request.closeCycleId())
            .flatMap(existing -> {
                if (existing.isPresent()) {
                    return Uni.createFrom().item(existing.get());
                }

                return calculateAndPersist(request);
            });
}
```

Then:

```java
private Uni<SyirkahCloseSnapshot> calculateAndPersist(
        SyirkahCloseProcessor.CloseRequest request) {

    return processor.calculateResult(request)
            .flatMap(calculation -> {

                SyirkahCloseSnapshot snapshot =
                        snapshotFactory.create(
                                request,
                                calculation,
                                buildPartySnapshots(calculation),
                                buildProfitAllocations(calculation),
                                buildLossAllocations(calculation));

                return snapshotRepository
                        .save(snapshot)
                        .replaceWith(snapshot);
            });
}
```

However, **this lookup-before-save is not enough for concurrency**.

Two workers can both see no snapshot.

Therefore the database needs a unique constraint:

```sql
create unique index ux_syirkah_close_snapshot_cycle
    on syirkah_close_snapshot (
        tenant_id,
        ledger_id,
        close_cycle_id
    );

create unique index ux_syirkah_close_snapshot_calculation
    on syirkah_close_snapshot (
        tenant_id,
        ledger_id,
        calculation_id
    );
```

Then a concurrent insert must be handled as an idempotent conflict.

---

# 6. Freeze the distribution source

This is the important change to `SyirkahDistributionPreparationService`.

Currently it can derive obligations from the calculation.

Instead:

```java
public Uni<List<PartnerDistributionObligation>> prepare(
        String tenantId,
        String ledgerId,
        String calculationId) {

    return snapshotRepository
            .findByCalculationId(
                    tenantId,
                    ledgerId,
                    calculationId)
            .flatMap(snapshot -> {

                if (snapshot.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Syirkah close snapshot not found: "
                                            + calculationId));
                }

                return prepareFromSnapshot(snapshot.get());
            });
}
```

Then:

```java
private Uni<List<PartnerDistributionObligation>> prepareFromSnapshot(
        SyirkahCloseSnapshot snapshot) {

    return distributionRepository
            .findByCalculationId(
                    snapshot.calculationId().value())
            .flatMap(existing -> {

                if (!existing.isEmpty()) {
                    return Uni.createFrom().item(existing);
                }

                return createObligations(snapshot);
            });
}
```

And:

```java
private Uni<List<PartnerDistributionObligation>> createObligations(
        SyirkahCloseSnapshot snapshot) {

    List<SyirkahCloseSnapshot.AllocationSnapshot> allocations =
            snapshot.resultType() == SyirkahResultType.PROFIT
                    ? snapshot.allocations()
                    : snapshot.lossAllocations();

    List<PartnerDistributionObligation> obligations =
            allocations.stream()
                    .filter(allocation ->
                            allocation.amount().signum() > 0)
                    .map(allocation ->
                            new PartnerDistributionObligation(
                                    "SDO-" + java.util.UUID.randomUUID(),
                                    snapshot.calculationId().value(),
                                    snapshot.contractId(),
                                    snapshot.contractVersion(),
                                    allocation.partyId(),
                                    allocation.amount(),
                                    snapshot.periodEnd()))
                    .toList();

    return saveSequentially(
            snapshot.calculationId().value(),
            obligations);
}
```

Now the chain becomes:

```text
GL journals
      ↓
financial result
      ↓
Syirkah contract version
      ↓
immutable close snapshot
      ↓
distribution obligations
      ↓
maker-checker approval
      ↓
journal posting
      ↓
settlement
```

That is much safer than recalculating each downstream stage.

---

# 7. Close task completion must use the snapshot

The `TASK_SYIRKAH_RESULT` task should be completed only after snapshot persistence succeeds.

```java
public Uni<SyirkahCloseSnapshot> calculate(
        SyirkahCloseProcessor.CloseRequest request,
        String operator) {

    return calculateSnapshot(request)
            .flatMap(snapshot ->
                    closeCycleService.completeTask(
                            CloseCycleId.of(request.closeCycleId()),
                            CloseTaskCodes.SYIRKAH_RESULT,
                            operator)
                            .replaceWith(snapshot));
}
```

Then allocation:

```java
public Uni<List<PartnerDistributionObligation>> prepareDistribution(
        SyirkahCloseProcessor.CloseRequest request,
        String operator) {

    return distributionPreparationService
            .prepare(
                    request.tenantId(),
                    request.ledgerId(),
                    request.closeCycleId())
            .flatMap(obligations ->
                    closeCycleService.completeTask(
                            CloseCycleId.of(request.closeCycleId()),
                            CloseTaskCodes.SYIRKAH_ALLOCATION,
                            operator)
                            .replaceWith(obligations));
}
```

And posting:

```java
public Uni<JournalEntryId> postDistribution(
        SyirkahCloseProcessor.CloseRequest request,
        String calculationId,
        String poster) {

    return distributionRepository
            .findByCalculationId(calculationId)
            .flatMap(obligations ->
                    distributionPostingService.post(
                            request,
                            calculationId,
                            obligations,
                            poster))
            .flatMap(journalEntryId ->
                    closeCycleService.completeTask(
                            CloseCycleId.of(request.closeCycleId()),
                            CloseTaskCodes.SYIRKAH_DISTRIBUTION,
                            poster)
                            .replaceWith(journalEntryId));
}
```

This preserves the critical invariant:

```text
TASK_SYIRKAH_DISTRIBUTION
        ↓
only completed after
        ↓
GL journal successfully POSTED
```

---

# 8. Add explicit close task constants

Avoid scattering strings such as:

```java
"TASK_SYIRKAH_RESULT"
```

through application services.

Create:

```java
package tech.kayys.syirkah.accounting.domain.close;

public final class CloseTaskCodes {

    private CloseTaskCodes() {
    }

    public static final String SYIRKAH_RESULT =
            "TASK_SYIRKAH_RESULT";

    public static final String SYIRKAH_ALLOCATION =
            "TASK_SYIRKAH_ALLOCATION";

    public static final String SYIRKAH_DISTRIBUTION =
            "TASK_SYIRKAH_DISTRIBUTION";
}
```

Then `CloseCycle.open(..., syirkahEnabled)` uses these constants.

---

# 9. The snapshot should be the audit boundary

I would deliberately **not** make the snapshot mutable with methods such as:

```java
changeContractVersion(...)
changeAllocation(...)
recalculate(...)
```

None should exist.

If the result is wrong before approval, the business operation should be:

```text
invalidate/reject close calculation
        ↓
create a new calculation
        ↓
new immutable snapshot
```

rather than:

```text
UPDATE existing snapshot
```

That gives us a clean audit trail:

```text
CALC-001
contract v3
profit 5,000,000
A 2,000,000
B 3,000,000

       ↓ rejected

CALC-002
contract v4
profit 5,000,000
A 2,500,000
B 2,500,000
```

rather than silently changing `CALC-001`.

---

# 10. Tests to add immediately

### Snapshot mathematical integrity

```java
@Test
void shouldRejectSnapshotWhenNetResultDoesNotMatchComponents() {

    assertThrows(
            IllegalArgumentException.class,
            () -> new SyirkahCloseSnapshot(
                    SyirkahCalculationId.generate(),
                    "CLOSE-001",
                    "TENANT-001",
                    "LEDGER-001",
                    "PERIOD-2026-09",
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 30),
                    "CONTRACT-001",
                    3,
                    new BigDecimal("25000000"),
                    new BigDecimal("15000000"),
                    new BigDecimal("3000000"),
                    new BigDecimal("2000000"),
                    new BigDecimal("6000000"), // wrong; expected 5m
                    SyirkahResultType.PROFIT,
                    List.of(),
                    List.of(),
                    List.of(),
                    "SYIRKAH-2026-09"));
}
```

### Snapshot cannot be modified through lists

```java
@Test
void shouldDefensivelyCopySnapshotLists() {

    List<SyirkahCloseSnapshot.AllocationSnapshot> allocations =
            new ArrayList<>();

    allocations.add(
            new SyirkahCloseSnapshot.AllocationSnapshot(
                    "PARTY-A",
                    new BigDecimal("2000000"),
                    "PROFIT_SHARING"));

    SyirkahCloseSnapshot snapshot =
            createValidSnapshot(allocations);

    allocations.clear();

    assertEquals(1, snapshot.allocations().size());

    assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.allocations().add(
                    new SyirkahCloseSnapshot.AllocationSnapshot(
                            "PARTY-B",
                            new BigDecimal("3000000"),
                            "PROFIT_SHARING")));
}
```

### Contract version is frozen

```java
@Test
void shouldPreserveContractVersionUsedForCalculation() {

    SyirkahCloseSnapshot snapshot = createValidSnapshot(
            List.of(
                    new SyirkahCloseSnapshot.AllocationSnapshot(
                            "PARTY-A",
                            new BigDecimal("2000000"),
                            "PROFIT_SHARING")));

    assertEquals(3, snapshot.contractVersion());
    assertEquals("CONTRACT-001", snapshot.contractId());
}
```

### Calculation retry returns same snapshot

```java
@Test
void shouldNotRecalculateExistingCloseSnapshot() {

    // Given an existing snapshot for CLOSE-001
    // When calculate is called again
    // Then the existing snapshot is returned
    // And financialResultProvider is not called again.
}
```

### Tenant isolation

```java
@Test
void shouldNotReturnSnapshotFromAnotherTenant() {

    // Given snapshot:
    // tenant = TENANT-A
    // ledger = LEDGER-A
    //
    // When TENANT-B requests the same calculation ID
    //
    // Then no snapshot is returned.
}
```

### Distribution uses snapshot rather than current contract

```java
@Test
void shouldCreateDistributionFromFrozenSnapshot() {

    // Snapshot:
    // contractVersion = 3
    // PARTY-A = 40%
    // PARTY-B = 60%

    // Current contract repository is changed to:
    // PARTY-A = 50%
    // PARTY-B = 50%

    // Distribution must still be:
    // PARTY-A = 40%
    // PARTY-B = 60%
}
```

That last test is particularly important.

---

## Resulting `tutup buku` state machine

After this tranche, the Syirkah-specific close becomes:

```text
                    OPEN
                      |
              calculate result
                      |
                      v
          SYIRKAH_RESULT completed
                      |
            prepare allocation
                      |
                      v
        SYIRKAH_ALLOCATION completed
                      |
               approve/check
                      |
                      v
             APPROVED obligations
                      |
              post distribution
                      |
                      v
        GL journal = POSTED
                      |
                      v
       SYIRKAH_DISTRIBUTION completed
                      |
                      v
                VALIDATING
                      |
                CloseCycle.approve()
                      |
                      v
                 APPROVED
                      |
                 CloseCycle.lock()
                      |
                      v
                  LOCKED
```

The key design decision here is that **the immutable Syirkah snapshot becomes the accounting truth for that close cycle**. Journal data and contracts remain the sources used to calculate it, but after calculation succeeds, downstream distribution must consume the frozen snapshot.

Next I would implement the **persistence adapter + migration/schema + repository tests**, because that is where immutability and the tenant/ledger uniqueness guarantees become real rather than merely domain-level guarantees.


/////////////