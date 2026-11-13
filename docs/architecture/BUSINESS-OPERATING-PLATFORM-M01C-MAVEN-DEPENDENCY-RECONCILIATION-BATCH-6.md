# Business Operating Platform M01-C — Maven dependency reconciliation (Batch 6)

## Purpose

Compare Batch 4's source-declared dependency graph with Batch 5's Maven effective-POM graph. This separates source declarations from dependencies Maven resolves after inheritance, properties, dependency management, BOMs and the supplied profile/settings environment.

This is an analysis utility only. It does not change module dependencies, runtime code, schemas or generated artifacts.

## Run

Generate both inputs from the **same repository revision**:

```bash
python3 scripts/architecture/maven-dependency-graph.py --root . --output-dir target/declared-graph

python3 scripts/architecture/maven-effective-dependency-graph.py \
  --root . --output-dir target/effective-graph --strict

python3 scripts/architecture/maven-dependency-reconciliation.py \
  --declared target/declared-graph/maven-dependency-graph.json \
  --effective target/effective-graph/maven-effective-dependency-graph.json \
  --output-dir target/dependency-reconciliation
```

Use the same Maven executable, settings, profiles, JDK and repository revision as the environment being assessed.

## Outputs

- `maven-dependency-reconciliation.json`: machine-readable comparison.
- `maven-dependency-reconciliation.csv`: matched declaration/effective edges.
- `maven-dependency-reconciliation.md`: summary, cycles and evaluation failures.

## Classification

- **Matched**: source path, groupId, artifactId, type and classifier match; version and scope are then compared.
- **Version managed or inherited**: source declaration omits a version but effective POM supplies one.
- **Version changed**: both sides specify a version and Maven resolves a different version.
- **Scope changed**: effective scope differs; absent source scope is treated as Maven's compile default.
- **Profile not in effective model**: profile declaration was not present in this effective-POM run. This is not proof that the profile is obsolete.
- **Declared not in effective model**: a project-level declaration was not matched. Investigate input revision, scanner coverage and Maven evaluation.
- **Effective-only origin unattributed**: present in the effective model but not matched to scanned declarations. It may be inherited, profile-contributed or outside scanner coverage; the tool does not guess.
- **Cycles**: carried from the effective graph and its configured cycle scopes.

## Limitations

The tool compares direct effective dependencies only. It does not compute transitive classpaths, plugin dependencies, dependency convergence, binary compatibility or runtime reachability. Both input graphs must describe the same revision. Cycles and version/scope differences are diagnostics, not automatic defects.

## Verification

Unit tests cover managed versions, scope changes, version differences, missing declarations, profile declarations, effective-only edges and schema validation. Run:

```bash
python3 -m unittest tests/architecture/test_maven_dependency_reconciliation.py
```

No full repository scan or CI result is implied by adding this tool.
