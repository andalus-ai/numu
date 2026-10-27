# Transport PostgreSQL adapter

This optional adapter implements `DepartureInventoryRepository` with JDBC. Add it only to compositions that use PostgreSQL, configure a `DataSource`, and run the Flyway migration in `src/main/resources/db/migration`.

Callers must save a domain snapshot with the version originally loaded. If `InventoryConcurrencyException` is raised, reload inventory and re-run the domain command; never retry the stale snapshot as-is.

The adapter uses a single local database transaction for the inventory compare-and-set and all hold changes. It does not coordinate transactions with Payment, Reservation, ticketing, or other capabilities.
