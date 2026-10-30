# Ecosystem Party–Participant Associations

## Purpose

A Party is a canonical person or organization identity. A Participant is an actor in the Numou ecosystem. A tenant-scoped Party–Participant association records the business role through which a Party is known to a Participant.

This is intentionally different from:

- **Party identity association**: links an external issuer + subject to a Party; it does not prove a business role.
- **Party relationship**: relates one Party to another Party.
- **Participant relationship**: relates one Participant to another Participant.
- **Tenant membership or authorization**: controls access and is owned by Identity/Security, not Ecosystem.

## Role semantics

The initial generic relationship roles are CUSTOMER, PARTNER, SUPPLIER, SERVICE_PROVIDER, MEMBER, EMPLOYEE, and OTHER. They classify a business relationship only. They do not grant permissions or tenant membership. Domain modules may map a role into their own vocabulary only at an explicit adapter boundary.

For Support portal resolution, a future Support adapter may map CUSTOMER and PARTNER to Support requester roles after it has independently verified the authenticated Party identity association and current tenant context. Other roles must remain unsupported unless an explicit Support policy is introduced.

## Lifecycle and persistence

Associations are tenant-scoped, immutable in identity fields, and revocable without deleting history. The PostgreSQL migration adds:

- a foreign key to canonical `ecosystem_party`;
- database checks for role and lifecycle consistency;
- a partial unique index preventing duplicate active (tenant, Party, Participant, role) associations;
- tenant-first lookup indexes for Party and Participant queries.

The repository requires tenant context on every lookup. The association keeps PartyId and ParticipantId as separate strongly typed identifiers; it never assumes that their UUID values are interchangeable.

## Migration

Apply `db/migration/V3__party_participant_associations.sql` after Ecosystem migrations V1 and V2. Existing rows are not backfilled: deployments must create relationships from authoritative business records through an explicit, audited migration or application workflow.
