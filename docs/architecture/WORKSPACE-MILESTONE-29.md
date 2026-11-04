# Workspace M29 — Block-anchored discussions

## Objective

Extend the existing page-comment lifecycle so a discussion can optionally be anchored to a stable page block UUID. Preserve page-level discussions and existing comment/reply/resolution semantics.

## Repository findings

The Workspace capability already owns page comment threads, append-only comments, replies, resolution/reopening, mention parsing, audited JDBC writes, and REST endpoints. Migration V23 introduced the tenant-scoped page comment schema. This milestone extends that implementation; it does not create a second Collaboration capability or duplicate comment storage.

## Contract

- `anchorBlockId` is optional on thread creation.
- When supplied, the application verifies that the block belongs to the requested tenant-scoped page before creating the thread.
- The JDBC adapter repeats the membership check inside the write transaction.
- The anchor is the stable block UUID, never its mutable position.
- Reordering blocks therefore does not move a thread to a different block.
- Existing page-level threads remain valid with a null anchor.
- A block may later be removed. The thread and append-only comment history must remain readable; clients should render such threads as orphaned/removed-block discussions.
- Tenant and actor identity continue to come from the trusted host identity provider. The request adds no caller-controlled identity fields.
- Thread creation, comment insertion, and audit insertion retain their existing transaction boundary.

## Persistence

Migration V24 adds nullable `anchor_block_id` and a tenant/page/anchor lookup index. It intentionally does not add a foreign key to current page blocks because deleting a block must not cascade into or prevent access to historical discussion.

## Verification added

- Application test: accepts a block from the target page, rejects a foreign/missing block, preserves page-level behavior, and retains the anchor through thread lifecycle changes.
- REST contract test: exposes the optional anchor without accepting tenant or actor identity.
- PostgreSQL store test: round-trips the anchor and verifies comment history remains readable after the anchored block is removed.
- PostgreSQL migration-chain test: applies V24 and verifies the anchor column and index.

## Not included

- General-purpose cross-capability collaboration/resource references.
- Inline rich-text range anchors.
- Block deletion/replacement UX or orphan-thread resolution UX.
- Comment edits/deletion, presence, live collaboration, or CRDTs.
- Reliable asynchronous mention delivery redesign.

## Verification status

The changes were authored through the GitHub repository API. No local Maven or PostgreSQL test execution is claimed. CI must verify the Workspace domain, application, persistence, REST, and web-client reactors before this milestone is considered complete.
