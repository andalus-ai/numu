# Numou Omnichannel — Milestone 26: Bounded Webhook Headers

## Objective

Reject malformed or unbounded header maps before provider-specific signature verification. This is defense in depth for adapters that already receive a parsed header map.

## Delivered

- Shared header validation used by inbound webhook requests and verifier requests.
- Limits: 100 entries, 256 characters per header name, 8,192 characters per value, and 65,536 aggregate name/value characters.
- Header names must use HTTP token characters; values reject control characters except horizontal tab.
- Case-insensitive duplicate names are rejected so map-based adapters cannot accidentally supply competing spellings such as `X-Signature` and `x-signature`.
- Header maps are defensively copied and immutable.

## Security boundary

This validation runs after an HTTP adapter has parsed headers into a Java map. It cannot recover duplicate values that the adapter already merged or discarded, nor prevent oversized network input before parsing. HTTP servers, gateways, and reverse proxies must enforce request-line/header count/header byte limits at the transport boundary and preserve enough header information for the adapter to reject duplicate signature headers according to the provider protocol.

Limits are deliberately conservative defaults, not claims about every provider's maximum valid header size. Deployments must validate them against official provider requirements before enabling an adapter.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests are authored in this milestone but have not been executed in this session. No CI result or provider-specific protocol compliance is claimed.
