# Survey Platform (Java 21, hexagonal architecture)

Multi-tenant platform to **author** surveys and quizzes (11 question formats, branching logic, scoring),
**publish** them (versioned lifecycle), **collect** responses and **analyse** results.

## Architecture

```
            inbound adapters                 application core                    outbound adapters
        ┌───────────────────────┐      ┌─────────────────────────────┐      ┌────────────────────────┐
HTTP ──▶│ survey-adapter-rest   │─────▶│ port.in  (use cases)        │      │ survey-adapter-memory  │
        │ (JAX-RS, JSON codec)  │      │ service  (orchestration)    │─────▶│ (repos, events, ids)   │
        └───────────────────────┘      │ port.out (repos, events…)   │◀─────│ ← swap: Panache/JDBC,  │
                                       │            │                │      │   outbox, Kafka …      │
        survey-bootstrap (Quarkus      │      survey-domain          │      └────────────────────────┘
        composition root, CDI wiring)  │ (aggregates, rules, no deps)│
                                       └─────────────────────────────┘
```

Dependency rule, enforced by Maven module boundaries: `domain ← application ← adapters ← bootstrap`.
The domain has **zero** dependencies; the application layer sees only the domain; adapters see the
application layer; only `survey-bootstrap` knows which adapter implements which port.

| Module | Role |
|---|---|
| `survey-domain` | `Survey` / `Response` aggregates, `Tenant`+`Plan`, 11 `QuestionType`s, `Condition` (skip logic), `Scoring` (quiz), `ResponseEvaluator`, `ResultsAggregator`, domain events |
| `survey-application` | Inbound ports (`SurveyAuthoringUseCase`, `SurveyLifecycleUseCase`, `ResponseUseCase`, `ResultsUseCase`, `TenantAdminUseCase`), outbound ports, services |
| `survey-adapter-memory` | In-memory repositories (tenant-partitioned, optimistic locking), event collector, UUID ids |
| `survey-adapter-rest` | Jakarta REST resources, tenant filter, error mapper, hand-written JSON codec |
| `survey-bootstrap` | Quarkus app: CDI producers wire ports to adapters, config |
| `survey-demo` | Framework-free walkthrough that doubles as a smoke test |

## Features

**Question formats** (`QuestionType`): `short_text` (max length, regex), `long_text`, `number` (min/max/integer),
`date` (min/max), `yes_no`, `single_choice`, `multi_choice` (min/max selections, "other"), `rating` (1..N),
`scale` (any range; NPS = 0..10, NPS score computed in results), `ranking` (all or top N), `matrix` (rows × columns,
single/multi per row, require all rows).

**Logic**: every question may carry `visibleIf` (`answered`, `choice_selected`, `compare`, `bool_is`, `text_contains`,
`and`/`or`/`not`). Conditions may only reference *earlier* questions (checked at publish time), and answers to hidden
questions never feed later conditions or get stored on submit.

**Quiz mode** (`kind: QUIZ`): per-question `scoring` (`exact_choice`, `partial_choices` with optional wrong-pick penalty,
`text_match`, `numeric_match` with tolerance, `bool_match`, `order_match`, `matrix_match` with per-row credit), plus
passing percentage, time limit (enforced server-side with a 5 s grace), max attempts, results visibility
(`IMMEDIATE` / `AFTER_CLOSE` / `NEVER`) and whether correct answers are shown.

**Lifecycle**: `DRAFT → PUBLISHED → CLOSED → ARCHIVED`. Only drafts are editable; `revise` clones a published/closed survey
into a new draft (`version + 1`) so collected responses stay tied to the exact version they answered. Publishing runs a
readiness check (`GET /surveys/{id}/readiness`): no questions, bad options, scoring that doesn't fit the type, forward
references in logic, unscored quizzes, …

**Multitenancy**: every repository call is tenant-scoped (another tenant's data is simply *not found*); `Plan` quotas
(published surveys, questions per survey, responses per survey, quiz on/off) are enforced in the domain; tenants can be
deactivated. Tenant resolution is an adapter concern (`TenantRequestFilter`, currently the `X-Tenant-Id` header).

**Concurrency**: optimistic locking via a persisted version on aggregates (`CONFLICT` on stale writes).

**Events**: `SurveyPublished`, `SurveyClosed`, `ResponseSubmitted` go through the `EventPublisher` port.

## Run

```bash
# 1) framework-free walkthrough / smoke test of the whole core
mvn -pl survey-demo -am compile exec:java

# 2) the HTTP API (dev mode)
mvn -pl survey-bootstrap -am quarkus:dev          # seeds tenant "demo" (PRO plan)
```

## API walkthrough

Every tenant-scoped call needs `-H 'X-Tenant-Id: demo'` (optional `X-Actor-Id`).

```bash
H='-H Content-Type:application/json -H X-Tenant-Id:demo'

# create a survey and a section
curl -s $H -X POST localhost:8080/api/v1/surveys -d '{"title":"Customer feedback","kind":"SURVEY"}'       # → survey id
curl -s $H -X POST localhost:8080/api/v1/surveys/$SID/sections -d '{"title":"Experience"}'                 # → section id in response

# add questions of different formats
curl -s $H -X POST localhost:8080/api/v1/surveys/$SID/sections/$SEC/questions -d '{
  "id":"nps","title":"How likely are you to recommend us?","required":true,
  "type":{"kind":"scale","min":0,"max":10,"minLabel":"Never","maxLabel":"Definitely"}}'
curl -s $H -X POST localhost:8080/api/v1/surveys/$SID/sections/$SEC/questions -d '{
  "id":"why_low","title":"What went wrong?","required":true,"type":{"kind":"long_text"},
  "visibleIf":{"op":"compare","question":"nps","cmp":"lte","value":6}}'

curl -s $H localhost:8080/api/v1/surveys/$SID/readiness
curl -s $H -X POST localhost:8080/api/v1/surveys/$SID/publish

# respondent flow
curl -s $H -X POST localhost:8080/api/v1/surveys/$SID/responses -d '{"respondentId":"u1"}'                 # → response id
curl -s $H -X PUT  localhost:8080/api/v1/responses/$RID/answers/nps -d '{"number":3}'                      # → why_low now visible
curl -s $H -X PUT  localhost:8080/api/v1/responses/$RID/answers/why_low -d '{"text":"Too slow"}'
curl -s $H -X POST localhost:8080/api/v1/responses/$RID/submit
curl -s $H localhost:8080/api/v1/surveys/$SID/results
```

Operator API (`X-Admin-Key`, default `dev-admin-key`): `POST /api/v1/admin/tenants {"id","name","plan":"free|pro|enterprise"}`,
`GET /api/v1/admin/tenants`, `PUT /api/v1/admin/tenants/{id}/plan`, `POST /api/v1/admin/tenants/{id}/deactivate`.

Endpoints: `POST/GET /surveys`, `GET/PUT /surveys/{id}`, `POST /surveys/{id}/sections`,
`POST /surveys/{id}/sections/{sec}/questions`, `PUT/DELETE /surveys/{id}/questions/{qid}`,
`POST /surveys/{id}/questions/{qid}/move`, `POST /surveys/{id}/{publish|close|archive|revise}`,
`GET /surveys/{id}/{readiness|results}`, `POST /surveys/{id}/responses`, `GET /responses/{rid}`,
`PUT /responses/{rid}/answers/{qid}` (empty body clears), `POST /responses/{rid}/submit`.

Errors are `{code, message, details[]}` with `NOT_FOUND→404`, `INVALID_STATE/CONFLICT→409`, `VALIDATION_FAILED→422`,
`LIMIT_EXCEEDED→402`, `FORBIDDEN→403`, `TIME_LIMIT_EXCEEDED→410`.

### Wire format cheat-sheet

* **Question**: `{id?, title, description?, required?, type, visibleIf?, scoring?, explanation?}` — give option `id`s explicitly
  when scoring or logic refers to them.
* **Type**: `{"kind":"single_choice","options":[{"id":"a","label":"A"}],"allowOther":true}`,
  `{"kind":"matrix","rows":[…],"columns":[…],"multiplePerRow":false,"requireAllRows":true}`, `{"kind":"rating","max":5}`, …
* **Scoring**: `{"kind":"partial_choices","correct":["a","c"],"points":4,"penalizeWrong":true}`,
  `{"kind":"text_match","accepted":["final"],"ignoreCase":true,"points":1}`, `{"kind":"numeric_match","expected":3,"tolerance":0,"points":1}`, …
* **Answer**: `{"text":"…"}` · `{"number":4}` · `{"date":"2026-10-03"}` · `{"bool":true}` · `{"choice":["a"],"other":"…"}` ·
  `{"ranking":["b","a"]}` · `{"matrix":{"row1":["col2"]}}`
* **Quiz settings** (`PUT /surveys/{id}`): `{"quiz":{"passingPercent":60,"timeLimitSeconds":600,"maxAttempts":2,"resultsVisibility":"IMMEDIATE","showCorrectAnswers":true}}`

## Extending

* **New question format**: add a record to `QuestionType` (+ a `Scoring` if gradable). The sealed hierarchies make the
  compiler flag every exhaustive `switch` to update (`ResultsAggregator`, `SurveyJson`).
* **Real database**: implement `SurveyRepository` / `ResponseRepository` / `TenantRepository` in a new module (e.g.
  `survey-adapter-persistence-panache`), keep `tenant_id` in every key/index (or use Postgres RLS), store surveys as JSONB
  and answers as JSONB or rows, map the optimistic `persistedVersion` to a version column, then swap the producers in `Wiring`.
* **Reactive (Mutiny)**: the core is synchronous by design (no framework types in ports). Quarkus runs blocking REST
  methods on worker threads; if you need a fully reactive stack, add a reactive variant of the ports in the application
  layer and a Panache-reactive adapter rather than leaking `Uni` into the domain.
* **Auth**: replace `TenantRequestFilter`'s header with JWT claims (tenant + roles) and add per-role checks in the REST
  adapter (or an `Authorizer` outbound port if rules become domain-specific).
* **Transactions**: put `@Transactional` / unit-of-work around the use-case beans in `Wiring` (e.g. a CDI interceptor
  decorator) so repository saves and the outbox write share a transaction.

## Status

* `survey-domain`, `survey-application`, `survey-adapter-memory`, `survey-demo` were compiled with JDK 21 and the demo runs
  green (branching, quiz grading, versioning, tenant isolation, plan limits).
* `SurveyJson` was type-checked against a stub of Jackson's `JsonNode`.
* The Quarkus pieces (`survey-adapter-rest` resources/filters, `survey-bootstrap`, poms) were written but **not built or
  run** (no Maven Central access in the authoring sandbox): expect to fix small things on first `mvn verify`
  (notably the `quarkus.platform.version`).
* Not included yet: JUnit test suite, persistent adapter, authN/Z, i18n of questions, file-upload questions, piping
  ("insert answer to Q1 into Q2's title"), question randomisation (`shuffleQuestions` is stored for the client to honour).
