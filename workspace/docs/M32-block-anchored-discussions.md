# M32 — Block-anchored discussions

M32 completes the existing stable block-anchor contract across REST, application,
PostgreSQL persistence, and the Workspace web client.

## Behavior

- A thread may be created with an optional `anchorBlockId`.
- The application validates that a supplied block ID belongs to the same tenant-owned page.
- The anchor is stored on `workspace_page_comment_threads.anchor_block_id` (the existing
  V24 migration); no duplicate anchor table or new migration is introduced.
- Thread reads and list responses return the persisted anchor.
- Moving a block preserves its ID and therefore preserves its discussion anchor.
- Removing a block does not delete or retarget the discussion. The original anchor ID remains
  as historical context; the client labels it as an orphaned anchor and keeps the discussion
  readable.
- The anchor is page-scoped, not a text offset. Text edits within the same block keep the
  anchor; splitting content into a new block does not silently move existing discussions.

## Compatibility

The REST create-thread request accepts an optional `anchorBlockId`; legacy requests without
it remain valid. The application request and response records retain compatibility
constructors for existing Java callers.

## Verification coverage

- Application test: valid page-owned block accepted; unknown/foreign block rejected.
- PostgreSQL integration test: anchor round-trips through create/read/list and remains
  unchanged after the referenced block is removed in a later page revision.
- REST contract test: request and response expose the optional anchor without accepting
  tenant or actor identity from the client.
- Web API test: anchor is preserved in the create-thread request body.
