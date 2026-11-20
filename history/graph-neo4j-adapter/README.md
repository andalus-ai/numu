# History Graph Neo4j Adapter

This is an optional read/projection adapter. It is not required by History, the PostgreSQL adapter, or the History application module.

## Data model
- A `:HistoricalRelationship` node represents one relationship identity in one scope.
- `:HistoricalEntity` nodes represent typed entity references.
- Directed `:SUBJECT` and `:OBJECT` edges preserve the relationship's direction.
- The relationship node carries revision, event key, relationship kind, description, validity JSON, and event timestamp.
- Public records use scope key `public`; tenant-private records use their tenant UUID. Tenant-private entities are also scoped, preventing accidental cross-tenant node reuse.

## Correctness
- The adapter accepts only `history.relationship.revision-appended` schema version 1 events.
- Envelope aggregate ID/revision must match the immutable event payload.
- A per-relationship write lock is acquired before checking revision. Older or duplicate revisions are ignored.
- Endpoint edges are replaced in the same Neo4j transaction as the revision advance.
- Unique constraints protect relationship and entity identity under concurrent writers.
- Event delivery is at-least-once. Reprocessing an event is safe; a failed transaction leaves the previous projection intact.

## Operations
Call `Neo4jHistoricalGraphSchema.ensure(driver, database)` during controlled deployment/bootstrap with a principal allowed to create constraints. Do not grant schema privileges to the runtime projection principal if deployment policy separates them.
Provide a dedicated Neo4j database and credentials through the host runtime's secret/configuration facilities. The adapter does not create drivers, own credentials, or start background workers.
Rebuild by clearing only this adapter's projection labels in a dedicated database and replaying all retained PostgreSQL outbox events in aggregate-revision order. Never delete or modify PostgreSQL canonical records to repair Neo4j.
