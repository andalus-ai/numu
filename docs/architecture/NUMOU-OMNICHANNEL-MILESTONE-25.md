# Numou Omnichannel — Milestone 25: Verifier Conformance Contract

## Objective

Provide a reusable behavioral test contract for provider webhook verifier implementations without guessing a provider's signature format or replay policy.

## Delivered

- Abstract JUnit contract covering stable provider identity, a valid protocol vector, invalid signature rejection, provider mismatch rejection, and non-contradictory verification results.
- Safe-result assertions ensure the result reason does not echo the request body or header values supplied by the test.
- A provider implementation can extend `ProviderWebhookVerifierContractTest` and supply:
  - `verifier()`: the implementation under test.
  - `expectedProviderId()`: its configured stable provider ID.
  - `validRequest()`: a request built from an official provider test vector.
  - `invalidSignatureRequest()`: the same vector with a deliberately invalid signature.

## Required provider-specific coverage

This generic contract is not a substitute for protocol-specific tests. For each real provider, add tests for all requirements in its official documentation, including as applicable:

- exact raw-body bytes versus canonical signing strings;
- signature encoding, prefix, header cardinality, and malformed values;
- timestamp tolerance and replay-window enforcement;
- key rotation and retired-key behavior;
- authenticated provider-account routing and tenant isolation;
- official positive and negative vectors;
- redaction of secrets, signatures, headers, and payloads in logs/errors.

Do not use invented fixtures as evidence that a provider protocol is correctly implemented. Keep official fixtures appropriately licensed and avoid embedding live secrets or customer payloads.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

The generic contract is authored but has not been executed in this session. No concrete provider adapter is declared production-ready by this milestone.
