# M01-C Batch 5 — Maven effective dependency graph

## Added

- `scripts/architecture/maven-effective-dependency-graph.py`: invokes Maven's `help:effective-pom` for every discovered POM, parses the resulting effective dependency list, and emits JSON/CSV plus internal-edge SCC diagnostics.
- `tests/architecture/test_maven_effective_dependency_graph.py`: tests effective-POM dependency parsing and strongly connected component behavior.

## Run

From a checkout with the repository's expected JDK and Maven installed:

```bash
python3 scripts/architecture/maven-effective-dependency-graph.py \
  --root . \
  --output-dir target/architecture-effective-graph
```

If CI uses explicit profiles or a custom settings file, pass the same values:

```bash
python3 scripts/architecture/maven-effective-dependency-graph.py \
  --root . \
  --profiles <comma-separated-profile-ids> \
  --settings ~/.m2/settings.xml \
  --output-dir target/architecture-effective-graph \
  --strict
```

For an offline run, use `--offline` only when the required Maven Help Plugin and all needed artifacts are already cached. Adjust the per-POM timeout with `--timeout-seconds`. The default internal group prefix is `tech.kayys.numou`; cycle analysis defaults to compile/runtime and can be changed with `--cycle-scopes`.

Outputs:

- `maven-effective-dependency-graph.json`: effective project coordinates, resolved direct dependency declarations, execution environment, duplicate coordinates, evaluation failures and SCCs.
- `maven-effective-dependency-edges.csv`: tabular edge export.

The script records Maven version, profile selection, settings path, offline mode and repository path. Do not compare reports as if they were equivalent when these inputs differ.

## Why this is a separate report

Batch 4 is a static source-POM inventory. Batch 5 delegates model construction to Maven. Maven resolves parent inheritance, property interpolation, dependencyManagement and BOM-managed versions, and evaluates profiles active under the supplied Maven invocation. The effective POM's top-level `dependencies` are treated as the project's effective direct dependencies. The script does not expand transitive dependencies.

This is still not the full runtime classpath or a complete reactor build graph. It does not invoke `package`/tests, model plugin dependencies as module edges, or guarantee that artifacts resolve in every downstream consumer. A successful `help:effective-pom` run is evidence that Maven could construct the model for that POM in this environment, not evidence that the project compiles or tests pass.

## Operational considerations

- This performs one Maven Help Plugin invocation per discovered POM. It is deliberately explicit and can be slow in a repository with hundreds of POMs.
- The first run may need network access to resolve the Help Plugin. Run it in the same controlled CI environment as the normal build where possible.
- The `--strict` flag fails the process when one or more POMs could not be evaluated. Without it, failures remain in the JSON report and the scan continues.
- Unmatched internal dependencies are reported; they may be externalized modules, parent/BOM-only artifacts, intentionally excluded projects, or missing checkout content. Investigate before classifying them as defects.
- Duplicate coordinates are surfaced because multiple project paths sharing one GAV make graph interpretation ambiguous.
- SCCs are candidates for dependency cycles in the selected effective direct-dependency scopes. Review test-only/optional edges and duplicate coordinates before changing module boundaries.

## Test and verification status

Run the focused unit tests with:

```bash
python3 -m unittest tests/architecture/test_maven_effective_dependency_graph.py
```

The tests added in this batch cover parsing of an effective dependency with a resolved version/scope and SCC behavior. The GitHub file writes and read-back are verifiable, but the test suite and repository-wide Maven scan have not been executed in this remote editing session. Do not treat the report as populated or CI-validated until it is run in a checkout with the repository's Maven/JDK/settings configuration.

## Scope

Analysis tooling, tests and documentation only. No production dependency declarations, runtime code, schemas or APIs were changed. No PR has been opened.
