# Numou Omnichannel — Milestone 29: Webhook Framing Integrity

## Objective

Treat the transport's declared content length as an integrity constraint, not only as an early size hint.

## Delivered

- Reject a body when the number of bytes actually read differs from a non-negative declared content length.
- Perform the framing check after bounded reading but before request construction, provider verification, inbox claims, or persistence.
- Preserve support for unknown-length streams (`-1`), which remain subject to the configured stream bound.
- Validate required callbacks before consuming the request stream.
- Add regression tests for short bodies, longer-than-declared bodies, exact lengths, unknown lengths, and callback validation ordering.

## Error handling

The transport adapter should map `ContentLengthMismatchException` to a client framing error (typically HTTP 400), and `PayloadTooLargeException` to a payload-too-large response (typically HTTP 413). Do not include body contents, signature values, or secrets in responses or logs.

## Remaining transport responsibilities

The server/framework and reverse proxy must enforce framing and size rules before buffering where possible. This pipeline cannot repair ambiguity already introduced by a proxy or a parser that normalizes conflicting Content-Length headers. Adapters must reject conflicting framing headers at the HTTP boundary.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests were authored but not executed during authoring; no CI pass is claimed.
