# Numou Omnichannel — Milestone 35: Webhook Security Conformance

## Objective

Tighten the shared verification-result boundary so provider adapters expose stable, machine-readable rejection codes rather than arbitrary text that could accidentally contain request or secret material.

## Delivered

- Constrained `WebhookVerificationResult.reasonCode` to a stable uppercase token format: `[A-Z][A-Z0-9_]{0,99}`.
- Preserved the existing invariant that successful verification must use `VERIFIED`, and rejected verification must not use it.
- Added tests for known rejection tokens, free-text rejection, punctuation, overlong values, and whitespace normalization.
- Kept provider-specific signature, timestamp, account identity, and replay semantics inside the provider adapter/security composition boundary; this milestone does not claim that generic tests prove a provider's protocol implementation is correct.

## Security rationale

The ingestion service can surface a verifier's reason code as an externally observable machine-readable outcome. Restricting the value to a token prevents accidental inclusion of exception messages, raw headers, signatures, or payload excerpts in this field. This is defense in depth, not a substitute for safe logging, generic HTTP error responses, or provider protocol test vectors.

Provider integrations should use stable codes such as `INVALID_SIGNATURE`, `TIMESTAMP_EXPIRED`, `REPLAY_DETECTED`, and `REPLAY_PROTECTION_UNAVAILABLE`. Do not put dynamic values into a reason code. Correlation IDs and diagnostic details belong in appropriately protected observability fields, not in this contract.

## Compatibility

This tightens runtime validation for rejection codes. Existing verifier implementations must use uppercase ASCII letters, digits, and underscores, starting with a letter, and no more than 100 characters. Audit provider adapters before rollout; previously accepted free-form values will now throw at result construction. The ingestion boundary catches verifier runtime failures and fails closed, but adapters should be updated rather than relying on that fallback.

## Validation

Suggested command:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests have been added but have not been executed in this authoring session. Confirm the focused Maven reactor and CI results before treating the change as verified.
