# M6 Pricing, Quotes and COD — PR Implementation Notes

This change set adds tenant-explicit JDBC persistence for immutable versioned courier rate cards, quote snapshots and guarded COD state transitions. It also includes domain tests for chargeable weight, tax rounding, tariff tenant/effective-window validation and explicit settlement variance.

M6 domain primitives and V8 schema are already present on main. This PR adds the persistence adapter and regression tests. The tests have not yet been executed in this environment.