# M01-C Batch 4 — Reproducible Maven dependency graph

## What was added

- `scripts/architecture/maven-dependency-graph.py`: standard-library-only scanner for every `pom.xml` below a repository root.
- `tests/architecture/test_maven_dependency_graph.py`: focused tests for local property resolution, profile labeling, dependencyManagement exclusion, and strongly connected components.

## Run locally

From the repository root:

```bash
python3 scripts/architecture/maven-dependency-graph.py \
  --root . \
  --output-dir target/architecture-dependency-graph
```

The tool writes:

| Artifact | Purpose |
|---|---|
| `maven-dependency-graph.json` | Machine-readable project inventory, dependency declarations, resolution diagnostics, duplicate coordinates and SCCs |
| `maven-dependency-edges.csv` | Spreadsheet-friendly direct dependency edges with scopes and declaration context |
| `maven-dependency-graph.dot` | Graphviz DOT of resolved internal edges in the selected cycle scopes |

To make a selected-scope cycle fail a local/CI invocation:

```bash
python3 scripts/architecture/maven-dependency-graph.py --root . --fail-on-cycle
```

The default cycle scopes are `compile,runtime`. Change them with `--cycle-scopes compile,runtime,test`. The default internal group prefix is `tech.kayys.numou`.

Run the focused tests with:

```bash
python3 -m unittest tests/architecture/test_maven_dependency_graph.py
```

## Interpretation rules

1. Only dependencies declared directly under a project's top-level `dependencies` are treated as project declarations.
2. Dependencies inside profiles are emitted separately with their profile ID and `activeByDefault` marker. The scanner does not guess which profiles Maven activates for a particular machine or CI invocation.
3. `dependencyManagement`, parent relationships, imported BOMs, plugins, and transitive dependencies are not emitted as direct module edges.
4. The scanner resolves literal values and common local `<properties>` aliases, including project/pom group, artifact and version aliases. It does not synthesize inherited effective-model properties from parent POMs.
5. Each declaration is classified as resolved internal, unmatched internal, unresolved property, or external. Internal declarations with no matching scanned coordinate are retained as diagnostics, not silently dropped.
6. SCC/cycle analysis operates on resolved internal coordinates in the configured scopes. Duplicate Maven coordinates are reported because coordinate-level graph nodes cannot distinguish duplicate project paths safely.
7. A detected SCC is a dependency-cycle candidate for the declared graph, not proof of a runtime cycle. Review scope, profile, optionality, duplicate coordinates and composition-root intent before remediation.

## Important limitation

This is a deterministic **POM-declared direct-dependency inventory**, not Maven's fully evaluated effective model. It does not resolve active profiles, settings/mirrors, parent inheritance, imported BOMs, reactor activation, dependency mediation or transitive dependencies. Therefore a clean SCC report must not be described as proof that the complete Maven build graph is acyclic.

For release or architectural-gate decisions, compare this inventory with Maven's own effective model and reactor/dependency output under the same JDK, Maven version, settings, profiles and command line used by CI. The script is intended to make repository-wide declared-edge inspection reproducible and to expose where that deeper Maven evaluation is required.

## Scope of this batch

This batch adds analysis tooling and tests only. It does not change production dependencies, module boundaries, runtime wiring, schemas, APIs, or build behavior. No PR has been opened.
