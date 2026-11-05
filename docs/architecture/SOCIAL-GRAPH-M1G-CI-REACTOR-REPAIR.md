# Social Graph M1g — CI reactor verification repair

- **Date:** 2026-11-04
- **Repository:** `andalus-ai/numu`
- **Base:** `main`
- **Working branch:** `fix/social-graph-ci-reactor-verification-m1g`
- **Scope:** Make the Social Graph workflow compile and test actual implementation modules before verifying the SecurityContext adapter.

## 1. Evidence from the merged M1f workflow

The authenticated command gateway milestone, [PR #1709](https://github.com/andalus-ai/numu/pull/1709), is merged. Its Social Graph CI run was:

- Workflow run: `41791498696`
- Job: `Social Graph Maven and PostgreSQL verification`
- Result: `failure`

The module-topology validation passed. The next Maven command was:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph,cache -am install
```

The reactor summary contained only these three POM projects:

- `numou-framework`
- `numou-social-graph`
- `numou-cache`

No Social Graph domain, SPI, application, or JDBC implementation artifact was compiled or tested. The command returned `BUILD SUCCESS`, but that was an aggregator-only success and did not constitute implementation verification.

The next command was:

```bash
mvn -B -ntp -f security/pom.xml -pl social-graph-adapter -am verify
```

It failed while resolving `numou-security-domain` dependencies because the local Maven repository did not contain:

- `tech.kayys.numou:numou-foundation:0.1.0-SNAPSHOT`
- `tech.kayys.numou:numou-foundation-testing:0.1.0-SNAPSHOT`

The Social Graph workflow therefore had a real reactor-selection defect. The failure is not evidence of a Social Graph Java compilation defect; the implementation compilation had not started.

## 2. Workflow repair

The workflow now selects implementation leaf modules and the foundation artifacts needed by the separate Security reactor:

```bash
mvn -B -ntp -f core/framework/pom.xml \
  -pl foundation/numou-foundation-domain,foundation/numou-foundation-testing,social-graph/social-graph-persistence-jdbc \
  -am install
```

Selecting `social-graph-persistence-jdbc` brings its Social Graph application/domain/SPI and Ecosystem application/domain dependency closure into the framework reactor. Explicitly selecting the foundation domain and testing artifacts makes them available to the subsequent Security reactor. The install lifecycle runs the selected modules' test phases before installing the artifacts.

The Security adapter remains verified separately:

```bash
mvn -B -ntp -f security/pom.xml -pl social-graph-adapter -am verify
```

The workflow path filters now include the framework foundation and Ecosystem directories because changes there can affect the Social Graph build and authenticated Party resolution. The unrelated Cache aggregator is no longer selected as a proxy for Social Graph implementation verification.

## 3. Current implementation boundary

Current `main` contains the Social Graph domain, SPI, application, JDBC persistence and Security adapter. The command path now includes:

1. Verified SecurityContext and tenant-membership check.
2. Issuer/subject obtained from the verified identity provider, not request fields.
3. Tenant-scoped Ecosystem Party association resolution.
4. Active Party validation.
5. Social Graph capability authorization.
6. Connection lifecycle command with tenant-scoped idempotency, audit and outbox writes.

The PostgreSQL connection aggregate remains distinct from Ecosystem's commercial/organizational `PartyRelationship`. No Neo4j dependency is introduced.

## 4. Verification status

- [x] Root cause confirmed from the failed GitHub Actions job log.
- [x] Workflow changed to select implementation leaf modules and required framework artifacts.
- [x] Workflow path filters include foundation and Ecosystem dependencies.
- [ ] Corrected workflow executed by GitHub Actions.
- [ ] Social Graph domain/application tests pass.
- [ ] PostgreSQL Testcontainers migration, transaction, rollback, uniqueness, tenant and audit tests pass (and are not silently skipped).
- [ ] Security adapter compiles and its gateway/authorization tests pass.
- [ ] Broader CI failures are triaged separately from Social Graph.

No Maven or PostgreSQL test result is claimed by this change. The branch has not been run by GitHub Actions yet. A pull request has not been opened; repository `AGENTS.md` requires explicit approval before creating PRs.

## 5. Next gate

After the corrected workflow is run, fix any actual compilation or test failures it exposes. Do not add follow/following, blocks, read APIs, profile discovery, communities, graph traversal or Neo4j until the M1 command boundary and PostgreSQL transaction tests are green. Privacy and visibility semantics remain prerequisites for discovery and graph reads.
