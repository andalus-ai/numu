# Business Operating Platform M01-C — Remediation backlog (Batch 7)

## Purpose

Turn Batch 6's Maven dependency reconciliation report into a ranked investigation queue. This tool is intentionally conservative: graph signals are not automatically architecture violations. It does not edit POMs or recommend blind dependency removal.

## Run

```bash
python3 scripts/architecture/maven-architecture-remediation-backlog.py \
  --reconciliation target/dependency-reconciliation/maven-dependency-reconciliation.json \
  --output-dir target/architecture-remediation-backlog
```

Optional: `--fanout-threshold 12` (default 12) changes the compile-edge count at which a module receives a high-fan-out review item.

## Outputs

- `maven-architecture-remediation-backlog.json`: ranked findings with evidence and next actions.
- `maven-architecture-remediation-backlog.csv`: review-friendly export.
- `maven-architecture-remediation-backlog.md`: ranked queue and investigation detail.

## Heuristic priority

- **95 — dependency cycle:** investigate edge scopes and capability direction first.
- **90 — unmatched internal edge:** verify coordinates, reactor coverage, version and packaging.
- **75 — project declaration absent from effective model:** first verify same-revision inputs and scanner/Maven behavior.
- **60 — high compile fan-out:** review whether the module is a legitimate composition root; threshold is configurable.
- **55 — effective-only edge:** trace Maven origin; provenance is deliberately not guessed.
- **50 — scope difference:** inspect dependency management and runtime packaging.
- **35 — version difference:** commonly intentional dependency management; check compatibility policy.

Priority is an investigation order, not a severity verdict. Detection confidence describes confidence in the graph signal, not confidence that an architecture violation exists.

## Required human validation

Before confirming a finding, inspect `AGENTS.md`, ADR-003, the domain capability matrix, capability contracts, source imports, Maven reactor membership and consumer usage. Record the owning capability, public contract, affected consumers, persistence/data impact, tenant/security implications, deployment impact and compatibility plan. Do not split modules merely to reduce fan-out.

## Verification

Unit tests cover cycle ranking, unmatched internal declarations, fan-out review status, and version/scope diagnostics. Run:

```bash
python3 -m unittest tests/architecture/test_maven_architecture_remediation_backlog.py
```

The report can only be populated from an actual Batch 6 output generated from the same repository revision. No full scan or CI result is implied.
