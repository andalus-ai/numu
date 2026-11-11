# Numou Feedback — M5 Ratings, Reviews and Surveys

- **Status:** Initial implementation slice committed on `feedback/m5-ratings-reviews-surveys`
- **Parent architecture:** [ADR-003](ADR-003-reusable-capability-modules.md)
- **Predecessor:** Feedback M0 persistence foundation

## Capability boundaries

- Feedback owns submitted ratings, review content, review moderation, rating semantics and rating aggregation.
- Survey owns survey authoring, question definitions, conditional branching, publication/version lifecycle, respondent sessions and survey result collection.
- Feedback does not duplicate Survey's question engine. The `numou-feedback-survey-adapter` translates Feedback metric semantics into the existing Survey question types and leaves authoring/distribution to Survey.
- Rated entities are opaque references (`targetType`, `targetId`, optional `branchId`). Product, service, employee, branch and other source capabilities retain ownership of their records.
- Identity remains owned by the host. Respondents/authors are opaque actor references. Anonymous ratings are supported.

## M5 domain implementation

### Rating metrics

- NPS: integer 0–10, promoters 9–10, passives 7–8, detractors 0–6.
- CSAT: integer 1–5.
- CES: integer 1–7.
- STAR_RATING: integer 1–5.
- CUSTOM: configurable decimal scale with up to four decimal places.

The domain rejects mismatched standard scales, out-of-range values, fractional standard scores and mixed metrics in one aggregation. `RatingSummary` calculates response count, average, distribution and NPS score. NPS is calculated as percentage of promoters minus percentage of detractors and rounded to the nearest integer.

### Reviews

Reviews reference a rating response and the same opaque target. The application handler verifies that the rating exists in the same tenant and that its target matches the review target. Review lifecycle is `PENDING -> PUBLISHED | REJECTED` and `PUBLISHED -> HIDDEN`. A trusted commerce/service adapter may mark a pending review as verified; user-supplied data cannot self-assert verification.

### Survey reuse

The existing Survey capability already provides versioned surveys, 11 question formats, conditional logic, anonymous respondents, publication lifecycle and response collection. The new Survey adapter maps:
- NPS to a required 0–10 linear scale.
- CSAT and star rating to a required 1–5 rating question.
- CES to a required 1–7 linear scale.
- CUSTOM to an explicitly supplied integer scale.

No new survey engine or survey database is introduced by M5.

## Persistence

Migration `V2__feedback_ratings_reviews.sql` adds tenant-scoped rating, rating-idempotency and review tables, with composite tenant foreign keys and target/branch/metric indexes. PostgreSQL adapters implement idempotent rating submission, tenant-scoped lookup, aggregation input retrieval, review persistence and tenant-scoped moderation updates.

## Tests added

- Standard metric scale and value validation.
- Decimal custom scale.
- NPS promoter/passive/detractor aggregation.
- Review moderation and verified state transitions.
- Cross-tenant/cross-target review linkage rejection.
- Survey metric-to-question mapping.
- PostgreSQL Testcontainers tests for rating idempotency, tenant isolation, and review moderation round-trip.

Tests have been added but have not been executed in this session. No CI pass is claimed.

## Remaining M5 work

1. Complete survey distribution integration (QR/public links, email/notification events, embedded forms and event-triggered dispatch) using existing Survey and Notification contracts.
2. Add survey templates and reusable metric bundles without duplicating Survey's existing question authoring.
3. Add public review query/moderation list APIs and review helpful/report interactions.
4. Add CSAT/CES positive-response and top-box calculations with configurable thresholds.
5. Add reporting projections for time windows, branch/product/service segmentation, response rates and exports.
6. Add runtime/API composition, authorization policies, privacy/retention controls and end-to-end CI verification.

## Build command

Run from the repository root:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

The PostgreSQL adapter integration tests require Docker; they are configured to skip when Docker is unavailable. The survey adapter is part of the Feedback reactor and requires the Survey domain artifact in the framework reactor.
