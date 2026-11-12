# Maven Parent and Cross-Repository Extraction Strategy

**Status:** Implemented in source branch; CI validation and first publication pending  
**Applies to:** The ten initial capability repositories in `DOMAIN-CAPABILITY-MATRIX.md`  
**Shared artifact source:** `andalus-ai/numu`  
**Version baseline:** `0.1.0-SNAPSHOT`, Java 21

## 1. Decision

Use one repository-neutral Maven parent artifact, one platform dependency BOM, and separately released capability BOMs.

| Artifact | Owner | Responsibility |
|---|---|---|
| `tech.kayys.numou:numou-parent` | `andalus-ai/numu` initially | Java/toolchain defaults, plugin versions, common test-library versions and Maven publication endpoint |
| `tech.kayys.numou:numou-bom` | `andalus-ai/numu` initially | Version alignment for published shared Foundation/platform artifacts and compatible capability artifacts |
| `tech.kayys.numou:numou-foundation-*` | Foundation source in `andalus-ai/numu` | Neutral kernel, Money and shared domain/application/adapter/testing primitives |
| `numou-ecosystem-*`, `numou-cache-*`, `numou-observability-*`, `numou-social-graph-*` | Shared platform modules in `andalus-ai/numu` | Public cross-capability contracts required by the first platform extraction; remain in the monorepo for now |
| `tech.kayys.numou:numou-{capability}-bom` | Each capability repository | Versions of that repository's own artifacts and only the public cross-repository contracts it consumes |

Do not create a new repository for the parent or BOM during this wave. The selected ten destination repositories remain the only capability destinations.

The shared parent is deliberately not the business BOM. It must not add Foundation, Security, Quarkus runtime, database drivers, Kafka or other implementation libraries as ordinary dependencies. It may manage optional dependency versions without adding them to consumer classpaths.

## 2. Source implementation

The source monorepo now contains:

- `core/framework/parent/pom.xml`: standalone `numou-parent` artifact;
- `core/framework/pom.xml`: framework reactor consumes the shared parent and includes the parent POM as a reactor module;
- `core/framework/bom/pom.xml`: platform BOM inherits directly from `numou-parent`, not the framework aggregator;
- `.github/workflows/shared-maven-parent-validation.yml`: validates the parent, BOM, Foundation, Ecosystem, Cache, Observability and Social Graph reactors;
- `.github/workflows/publish-shared-foundation.yml`: manually publishes the parent, BOM and shared platform dependency reactors to GitHub Packages.

The Maven parent and platform BOM can now be published without requiring capability repositories to inherit from the framework aggregator POM.

## 3. Repository POM pattern after shared artifact publication

An extracted capability root aggregator should use the published parent and import the published platform BOM. Example for Identity:

```xml
<parent>
    <groupId>tech.kayys.numou</groupId>
    <artifactId>numou-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath/>
</parent>

<properties>
    <numou.platform.version>0.1.0-SNAPSHOT</numou.platform.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>tech.kayys.numou</groupId>
            <artifactId>numou-bom</artifactId>
            <version>${numou.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

This is a migration template, not yet a replacement for the existing source POMs. Apply it only in a destination repository after the shared artifacts have been published and clean dependency resolution has been proven.

Child modules continue to inherit from their repository-local root aggregator with `<relativePath>../pom.xml</relativePath>`. Only the root aggregator has an empty parent relative path.

## 4. Artifact resolution and publication

The shared parent declares GitHub Packages distribution management for `maven.pkg.github.com/andalus-ai/numu`. The manual publish workflow configures the `github` Maven server from the workflow's `GITHUB_TOKEN`.

Consumers need a Maven repository entry for the shared package source and read credentials for GitHub Packages. Do not commit credentials. For public consumers, package visibility and anonymous-read behavior must be verified in GitHub before claiming the artifacts are publicly resolvable.

The initial publication command is:

```bash
mvn -B -ntp -f core/framework/parent/pom.xml deploy
mvn -B -ntp -N -f core/framework/pom.xml deploy
mvn -B -ntp -f core/framework/bom/pom.xml deploy
mvn -B -ntp -f core/framework/foundation/pom.xml deploy
mvn -B -ntp -f core/framework/ecosystem/pom.xml deploy
mvn -B -ntp -f core/framework/cache/pom.xml deploy
mvn -B -ntp -f core/framework/observability/pom.xml deploy
mvn -B -ntp -f core/framework/social-graph/pom.xml deploy
```

The pull-request validation command is:

```bash
mvn -B -ntp -f core/framework/parent/pom.xml install
mvn -B -ntp -N -f core/framework/pom.xml validate
mvn -B -ntp -f core/framework/bom/pom.xml install
mvn -B -ntp -f core/framework/foundation/pom.xml install
mvn -B -ntp -f core/framework/ecosystem/pom.xml install
mvn -B -ntp -f core/framework/cache/pom.xml install
mvn -B -ntp -f core/framework/observability/pom.xml install
mvn -B -ntp -f core/framework/social-graph/pom.xml install
```

The workflows execute these isolated commands. The first validation attempt used a single selection from the large framework aggregator and failed while Maven loaded the unrelated `survey/survey-adapter-rest` reactor: its parent declares `io.surveyhub:survey-platform` but its relative path resolves to `tech.kayys.numou:numou-survey`. The validation and publication workflows now avoid recursively loading unrelated capability reactors. The expanded validation must pass before the manual publication workflow is run. Do not mark the shared artifacts as published until publication succeeds and a clean consumer build resolves them from GitHub Packages.

## 5. Critical M0 findings from the first three capability reactors

### 5.1 Identity is not self-contained yet

The Identity adapter currently depends on:

- `numou-foundation-application`;
- `numou-security-spi` and `numou-security-application` for shared security contracts/throttling;
- `numou-observability-api` and `numou-observability-adapter`;
- `numou-cache-api` and `numou-cache-adapter`;
- Organization modules additionally depend on `numou-ecosystem-domain/application` and `numou-security-vault-spi`.
- Quarkus OIDC, REST, Hibernate Reactive, PostgreSQL, Flyway, Kafka, gRPC, JWT and scheduler integrations.

This is a valid reason to retain adapter modules, but the dependency graph must be made explicit. Identity domain/application must not acquire a runtime dependency on Security adapters, database drivers or Quarkus. The adapter can depend on published public Security and platform contracts; the monorepo source currently builds these capabilities through shared reactor/dependency management.

Identity also has three modules in its root reactor. Its existing `identity/bom/pom.xml` is not listed as a module in `identity/pom.xml`; do not assume it is currently built or published.

### 5.2 Tenancy and Security are separate reactors

Tenancy has four modules and its current declared modules depend only on Foundation and their own Tenancy modules, so it can migrate independently once the shared parent/Foundation artifacts are available. Security has ten declared modules, including security vault modules and a Social Graph adapter. Security's adapters depend on Foundation, Cache, Observability and Social Graph platform artifacts. Their roots currently inherit `numou-framework` through a relative path into the monorepo. Those paths cannot survive extraction.

The existing `tenancy/bom/pom.xml` is not listed in `tenancy/pom.xml`. The Identity, Organization and Tenancy BOM POMs also use the artifactId `numou-bom` and contain broad dependency lists that are not capability-local. Do not merely rename and publish these BOMs. Rebuild group BOMs from the audited artifact set, give each a unique artifactId (`numou-identity-bom`, `numou-tenancy-bom`, `numou-security-bom`) and include them in the correct root reactor.

### 5.3 Aggregator ownership is mixed today

`core/framework/pom.xml` includes `../../identity` as a module, while Tenancy and Security remain separate reactor roots. It also aggregates other business capabilities. This is an observed build layout, not the desired long-term ownership model.

The parent extraction must not silently remove these module entries. Remove or replace them only after their replacement build path and every in-repository consumer have been migrated.

## 6. M1 migration order and dependency rules

The POM dependency graph changes the safe extraction order. Do not migrate Identity first simply because its repository name sounds foundational.

1. **Publish shared platform artifacts:** parent, framework parent POM, platform BOM, Foundation, Ecosystem, Cache, Observability and Social Graph. Social Graph remains a shared platform module in the monorepo for this wave; it is not moved into Identity. Ecosystem must be available before Social Graph.
2. **Tenancy:** migrate its four modules after Foundation and parent publication. Its current declared modules depend only on Foundation and their own Tenancy artifacts, so this can proceed independently.
3. **Security:** migrate Security domain/SPI/application, security-vault modules and adapter after Foundation, Cache, Observability and Social Graph artifacts are published. Security's Social Graph adapter remains a Security-owned integration adapter.
4. **Identity and Organization:** migrate Identity and Organization together after Security is published. Identity adapter consumes Security SPI/application; Organization adapter consumes Security vault SPI and Ecosystem application. Organization domain also consumes Identity domain, so both roots belong in one Identity repository release train.
5. **Composition and source consumers:** update monorepo consumers to published public contracts, run clean destination builds, and only then remove duplicate source-reactor entries.

Tenancy and Security can be prepared in parallel after shared artifacts; Identity follows Security. Keep all three as independent repositories. Avoid direct persistence/database coupling.

## 7. Extraction acceptance gates

A source root is ready to transfer only when all of the following are true:

- [ ] Shared parent, platform BOM, Foundation, Ecosystem, Cache, Observability and Social Graph artifacts are published.
- [ ] Clean Maven settings can resolve those artifacts from GitHub Packages.
- [ ] Exact source files, tests, resources, SQL migrations, event schemas and API contracts are inventoried.
- [ ] Every dependency is classified as shared public API, same-repository module, optional adapter, test-only or forbidden private implementation.
- [ ] Destination root POM has no relative parent path into `andalus-ai/numu`.
- [ ] Capability BOM artifactId is unique and is included in the root reactor.
- [ ] Destination CI builds in a clean environment.
- [ ] Monorepo consumers use the released artifact or an explicitly maintained compatibility module.
- [ ] No source module is deleted until destination and monorepo consumers pass their required tests.

## 8. Current verification status

| Check | Status |
|---|---|
| Shared parent POM added | Implemented in branch |
| Framework aggregator consumes parent | Implemented in branch |
| Platform BOM detached from framework aggregator parent | Implemented in branch |
| PR validation workflow | Passed on PR #1988 (run 7) |
| Manual GitHub Packages publishing workflow | Added; not run |
| Shared parent/BOM/platform clean build | Passed: parent, framework parent model, BOM, Foundation, Ecosystem, Cache, Observability and Social Graph reactors |
| Published package read access from a destination repo | Pending publication and clean consumer test |
| Tenancy source transfer | Staged in [numou-tenancy PR #1](https://github.com/andalus-ai/numou-tenancy/pull/1): 75 source files; build waits for shared package publication |
| Security and Identity source transfer | Not started; Security precedes Identity based on actual POM dependencies |
| Source monorepo consumers updated | Not started |

The isolated Maven validation passed on PR #1988 (Shared Maven Parent Validation, run 7). The earlier broad-reactor attempt still documents a pre-existing Survey parent-POM issue; the passing workflow deliberately avoids that unrelated reactor. No package publication or clean consumer-repository resolution is claimed yet.
