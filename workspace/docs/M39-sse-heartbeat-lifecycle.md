# M39 — SSE heartbeat and connection liveness

## Objective

Keep long-lived page-change SSE connections alive across idle periods and common proxy timeouts without introducing a thread per connection or weakening the M37 single-in-flight-write rule.

## Implementation

- Add a transport-level `WorkspaceSseHeartbeatScheduler` port. The REST resource depends on this host-provided scheduler rather than creating threads or timers.
- The optional Quarkus collaboration runtime provides one shared daemon scheduler for all streams.
- Emit SSE comment frames (`:` keep-alive) on a configurable interval. Comments do not dispatch browser message events and carry no workspace/page content.
- Heartbeats share the same per-connection in-flight send guard as page-change events. A heartbeat is skipped while a data frame is being written; a page-change event that arrives while another frame is still pending closes the slow connection so the client can replay through M35.
- Cancel each connection's recurring task when its sink closes, its subscription is revoked, a send fails, or scheduler setup fails.
- Close the shared scheduler on CDI shutdown.
- Expose aggregate scheduler health: active stream tasks, heartbeat callbacks, callback failures, and closed state.

## Configuration

`numou.workspace.collaboration.heartbeat-interval-seconds` defaults to 25 and accepts 5–300 seconds. The Quarkus scheduler uses one shared daemon thread; it does not allocate a thread per SSE connection.

## Operational semantics

- Heartbeats are liveness comments, not business events or revision cursors.
- They do not extend authorization. M38 still rechecks PAGE/READ on delivery and supports explicit actor/page/tenant revocation.
- No page content or tenant identifiers are included.
- On transport failure, the connection is closed and clients recover through the durable M35 revision changes endpoint.
- Hosts using another runtime must implement `WorkspaceSseHeartbeatScheduler` with bounded shared scheduling and connection-close cancellation.
