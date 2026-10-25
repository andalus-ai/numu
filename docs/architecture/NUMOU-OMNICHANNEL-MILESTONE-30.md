# Numou Omnichannel — Milestone 30: Fail-Closed Provider Verification

## Goal

Keep provider authenticity verification a strict trust boundary even when a provider adapter throws unexpectedly or returns no result.

## Behavior

- A runtime failure from `ProviderWebhookVerifier.verify`, including a null result, returns the payload-free disposition `REJECTED` with reason code `VERIFICATION_FAILED`.
- The exception message and implementation details are not propagated through the ingestion result.
- Inbox claiming, payload persistence, and the VERIFIED transition do not run after a verifier failure.
- Explicit negative verification results retain their existing safe provider reason code.

## Scope and limitations

This is a core-domain hardening measure, not a substitute for provider-specific signature validation. Each verifier must still implement the provider's documented signing bytes, timestamp/replay policy, secret rotation, and comparison requirements. Transport adapters should map the stable result to an appropriate external response without exposing exception details.

## Validation

A regression test covers a verifier throwing an exception with a deliberately sensitive message and asserts that the result is rejected, contains only the stable failure code, has no payload reference, and causes no inbox claim or payload write.

Suggested command:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests were authored for this milestone; a passing local or CI run must be confirmed separately.
