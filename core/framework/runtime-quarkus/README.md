# Numou Runtime — Quarkus health transport

This module exposes the framework-neutral runtime health snapshot over Quarkus REST:

- `GET /health/live` — HTTP 200 while the process is live; HTTP 503 after it has stopped.
- `GET /health/ready` — HTTP 200 only after the application bootstrap reaches READY; HTTP 503 while starting, stopping, stopped, or failed.

The product owns the trusted composition root and must provide exactly one application-scoped
`ApplicationRuntimeBootstrap` bean. For example, a product-specific CDI producer constructs
its `RuntimeApplicationDefinition` from trusted code registrations and validated application
configuration, then starts it during application startup and closes it during shutdown.

This module deliberately does not:

- create a manifest or load provider classes from configuration;
- discover plugins from the classpath;
- start the global plugin manager;
- decide which business capabilities an application includes;
- create or own an HTTP server process; or
- expose dependency, configuration, or secret values in health responses.

The resource returns the immutable `RuntimeHealthSnapshot` as JSON. Its failure field is already
sanitized by the runtime host to an exception class name rather than an exception message.

## Dependency

Add `tech.kayys.numou:numou-runtime-quarkus` to a Quarkus application that also provides
`ApplicationRuntimeBootstrap`. Keep the Quarkus transport dependency out of domain and
application modules.
