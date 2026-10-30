# Numou — Product Vision and Engineering Guardrails

> Normative guidance for contributors and coding agents. This repository remains an **independent product** within the broader Andalus ecosystem.

## Mission
Numou is responsible for **reusable modular business capabilities**. Its mission is to provide dependable, reusable capabilities that are valuable outside any single consuming application.

## Non-negotiable principles
1. **Independent ownership:** Farabi and Toba may integrate through adapters; Numou must not depend on either.
2. **Strong contracts:** version public APIs, schemas and extension points; document compatibility and migrations.
3. **Replaceable implementations:** keep domain logic separate from transport, framework, persistence and vendor-specific adapters wherever practical.
4. **Security by default:** validate untrusted inputs; enforce tenant boundaries, access policy and action permissions at authoritative boundaries.
5. **Production quality:** deterministic tests, negative security cases, observability, performance limits, documentation and CI verification.
6. **No invented integrations:** verify real upstream/downstream APIs and dependency versions before claiming compatibility.
7. **No false completion claims:** committed, compiled, tested, integrated and production-ready are distinct statuses.

## Product focus
Prioritize **identity, tenancy, domain ownership, stable contracts and application-neutral modules**. Deliver coherent vertical capabilities, not disconnected scaffolding or demonstrations that bypass core invariants.

## Ecosystem relationship
- **Farabi:** governed conventional + agentic BI with dynamic-to-static dashboard promotion.
- **Batik:** agent-driven dynamic UI/A2UI.
- **Tenun:** independent chart rendering.
- **Toba:** independent multi-engine governed lakehouse.
- **Numou:** reusable identity, tenant and business capabilities.
Integration must use explicit, versioned contracts; no repository should acquire a reverse dependency merely to support Farabi.

## Engineering delivery policy
- Inspect existing code, docs, branch and CI before changing anything.
- State the product objective and acceptance criteria for each batch.
- Implement domain rules, adapters, tests and docs together where relevant.
- Prefer comprehensive batches; do not open a PR without user approval.
- Run tests and CI when possible; report actual evidence and unresolved gaps.
- Treat AI-produced plans, UI definitions, queries and actions as untrusted until validated and authorized.
- Never silently relax tenant isolation or authorization for integration convenience.

## Definition of done
The user-facing capability works through real supported contracts, tests and CI pass, security constraints are demonstrated, compatibility is documented, and no placeholder is described as production-ready.

## Farabi interoperability guardrail
The shared ecosystem goal is **Prompt → governed analytics → dynamic Batik dashboard → reviewed versioned static dashboard**. This is Farabi's application workflow, **not a mandate** for Numou to implement BI-specific behavior. Preserve Numou's own product boundaries.
