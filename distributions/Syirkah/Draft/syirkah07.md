## Next implementation slice: liquidation policy adapters and finalization gate

The next step is to connect the liquidation workflow to Syirkah’s existing accounting command path, while keeping profit/loss allocation rules separate from journal construction. The existing `PostJournalEntryCommand` is in `tech.kayys.syirkah.accounting.application.api.command` and already carries `sourceType`, `sourceId`, and journal lines.&#x20;

syirkah-framework(1).txt



The code below is an integration slice, not a claim that the repository has been patched or compiled. It deliberately avoids assuming unverified methods on your existing `Money`, contract, or allocation classes.

### 1. Define the liquidation accounting policy

Place this in the liquidation application module. Each method translates a validated liquidation artifact into one or more existing journal commands; it does not post the journal itself.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;

import java.util.List;

public interface SyirkahLiquidationAccountingPolicy {

    List<PostJournalEntryCommand> accountAssetRealization(
            LiquidationRealizationContext context);

    List<PostJournalEntryCommand> accountLiabilitySettlement(
            LiquidationLiabilitySettlementContext context);

    List<PostJournalEntryCommand> accountResultAllocation(
            LiquidationResultAllocationContext context);

    List<PostJournalEntryCommand> accountCapitalReturn(
            CapitalReturnContext context);
}
```

The context types should carry the minimum immutable information required to build the journal: tenant, ledger, contract, liquidation, artifact ID, amount, currency, effective date, account mappings, and operator/reference metadata. Use the existing project's actual identifier and amount types where available.

Keep the accounting source IDs stable and idempotent:

| Artifact             | `sourceType`                               | `sourceId`                     |
| -------------------- | ------------------------------------------ | ------------------------------ |
| Asset realization    | `SYIRKAH_LIQUIDATION_ASSET_REALIZATION`    | Realization ID                 |
| Liability settlement | `SYIRKAH_LIQUIDATION_LIABILITY_SETTLEMENT` | Settlement ID                  |
| Result allocation    | `SYIRKAH_LIQUIDATION_RESULT`               | Liquidation ID + allocation ID |
| Capital return       | `SYIRKAH_LIQUIDATION_CAPITAL_RETURN`       | Capital-return obligation ID   |

If one business artifact legitimately produces multiple journals, include a stable sub-artifact key in `sourceId`; do not generate a new random idempotency key on retry.

### 2. Resolve the policy explicitly

Do not silently fall back to a generic allocation policy when the contract type is unknown. A missing policy should block liquidation.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import java.util.Map;
import java.util.Objects;

public final class SyirkahLiquidationResultPolicyResolver {

    private final Map<String, SyirkahLiquidationResultPolicy> policies;

    public SyirkahLiquidationResultPolicyResolver(
            Map<String, SyirkahLiquidationResultPolicy> policies) {
        this.policies = Map.copyOf(
                Objects.requireNonNull(policies, "policies"));
    }

    public SyirkahLiquidationResultPolicy resolve(String syirkahType) {
        if (syirkahType == null || syirkahType.isBlank()) {
            throw new IllegalArgumentException(
                    "Syirkah type is required for liquidation");
        }

        SyirkahLiquidationResultPolicy policy = policies.get(syirkahType);

        if (policy == null) {
            throw new IllegalStateException(
                    "No liquidation result policy registered for: "
                            + syirkahType);
        }

        return policy;
    }
}
```

Use the existing `SyirkahType` enum directly instead of the `String` key once its package and module visibility are confirmed. Do not introduce another contract-type enum.

Implement the Mudharabah and Musyarakah policies as separate adapters over the project's existing result and loss-allocation rules. The adapter should receive an explicit liquidation context—including realized assets, settled liabilities, capital positions, and the applicable frozen policy version—and return allocations. It should not invent a new rule for distributing losses or residual assets.

### 3. Add a liquidation finalization gate

Finalization must not be allowed merely because the liquidation result was calculated. The gate must validate every financial stage and the evidence connecting it to accounting.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface SyirkahLiquidationFinalizationGate {

    Uni<LiquidationGateResult> validate(
            LiquidationFinalizationContext context);
}
```

```
package tech.kayys.syirkah.accounting.application.liquidation;

import java.util.List;

public record LiquidationGateResult(
        boolean passed,
        List<String> errors) {

    public LiquidationGateResult {
        errors = errors == null ? List.of() : List.copyOf(errors);

        if (passed && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A passed gate cannot contain errors");
        }

        if (!passed && errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A failed gate must explain why finalization is blocked");
        }
    }

    public static LiquidationGateResult passed() {
        return new LiquidationGateResult(true, List.of());
    }

    public static LiquidationGateResult failed(List<String> errors) {
        return new LiquidationGateResult(false, errors);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.liquidation;

public record LiquidationFinalizationContext(
        String tenantId,
        String ledgerId,
        String contractId,
        String liquidationId,
        String fiscalPeriodId,
        String operator) {
}
```

Use a repository-backed validator rather than passing a precomputed boolean into the gate. That lets the gate query persisted artifacts and reconciliation evidence.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface LiquidationFinalizationEvidence {

    Uni<List<String>> validateAssetRealizations(
            LiquidationFinalizationContext context);

    Uni<List<String>> validateLiabilitySettlement(
            LiquidationFinalizationContext context);

    Uni<List<String>> validateResultAndAllocation(
            LiquidationFinalizationContext context);

    Uni<List<String>> validateCapitalReturns(
            LiquidationFinalizationContext context);

    Uni<List<String>> validateAccountingReconciliation(
            LiquidationFinalizationContext context);

    Uni<List<String>> validateContractAndPeriodState(
            LiquidationFinalizationContext context);

    Uni<List<String>> validatePendingTransitions(
            LiquidationFinalizationContext context);
}
```

The implementation aggregates all errors so an operator can see the blockers in one pass.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;

public final class DefaultSyirkahLiquidationFinalizationGate
        implements SyirkahLiquidationFinalizationGate {

    private final LiquidationFinalizationEvidence evidence;

    public DefaultSyirkahLiquidationFinalizationGate(
            LiquidationFinalizationEvidence evidence) {
        this.evidence = evidence;
    }

    @Override
    public Uni<LiquidationGateResult> validate(
            LiquidationFinalizationContext context) {

        return evidence.validateAssetRealizations(context)
                .chain(assetErrors ->
                    evidence.validateLiabilitySettlement(context)
                        .chain(liabilityErrors ->
                            evidence.validateResultAndAllocation(context)
                                .chain(resultErrors ->
                                    evidence.validateCapitalReturns(context)
                                        .chain(capitalErrors ->
                                            evidence.validateAccountingReconciliation(
                                                    context)
                                                .chain(accountingErrors ->
                                                    evidence.validateContractAndPeriodState(
                                                            context)
                                                        .chain(stateErrors ->
                                                            evidence.validatePendingTransitions(
                                                                    context)
                                                                .map(transitionErrors -> {
                                                                    List<String> errors =
                                                                            new ArrayList<>();

                                                                    errors.addAll(assetErrors);
                                                                    errors.addAll(liabilityErrors);
                                                                    errors.addAll(resultErrors);
                                                                    errors.addAll(capitalErrors);
                                                                    errors.addAll(accountingErrors);
                                                                    errors.addAll(stateErrors);
                                                                    errors.addAll(transitionErrors);

                                                                    return errors.isEmpty()
                                                                            ? LiquidationGateResult.passed()
                                                                            : LiquidationGateResult.failed(errors);
                                                                }))))));
    }
}
```

The evidence implementation should enforce these conditions:

- All realizations and liability settlements are persisted and accounted for.
- Liabilities are settled or explicitly handled under an approved, documented disposition; they are not silently ignored.
- The liquidation result and partner allocations are approved and consistent with the applicable frozen policy.
- Every required capital-return obligation has reached the required posting and settlement state.
- Expected accounting effects reconcile to posted journals, with no duplicate source postings.
- The contract is eligible for termination, the fiscal period is compatible with finalization, and no conflicting partner transition is pending.
- The liquidation is not already completed in a way that would cause a duplicate finalization.

### 4. Make completion one atomic transaction

The gate is not a substitute for concurrency control. Revalidate it inside the transaction that changes the final state.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

public interface SyirkahLiquidationCompletionTransaction {

    Uni<Void> complete(
            LiquidationFinalizationContext context);
}
```

The transaction implementation should follow this sequence:

```
BEGIN TRANSACTION

  Lock liquidation
  Lock fiscal period
  Lock Syirkah contract
  Lock capital-return obligations

  Verify liquidation is not already completed
  Run finalization gate against persisted evidence
  Abort if any gate fails

  Mark liquidation COMPLETED
  Mark contract TERMINATED
  Append immutable audit evidence
  Persist transactional outbox events

COMMIT
```

Any failure rolls back all state changes. Publish external events only after commit through the outbox dispatcher.

Use conditional state updates or row locks to prevent two workers from completing the same liquidation. Repeated requests should return the existing completion result where safe, rather than posting new journals or duplicating settlement artifacts.

### 5. Add tests before wiring the production adapter

| Test                                              | Expected outcome                         |
| ------------------------------------------------- | ---------------------------------------- |
| All stages reconciled                             | Completion succeeds                      |
| Liability remains unexplained                     | Completion blocked                       |
| Capital-return obligation is unposted             | Completion blocked                       |
| Accounting total differs from allocation evidence | Completion blocked                       |
| A partner transition is pending                   | Completion blocked                       |
| Journal source already exists                     | No duplicate journal                     |
| Two workers complete concurrently                 | One final state and one completion event |
| Failure while persisting final audit/outbox       | Entire transaction rolls back            |

One important accounting distinction: capital returned to partners is not necessarily equal to the net accounting movement across all liquidation journals. Reconcile each artifact category against its expected postings, then reconcile the overall liquidation position. Do not use a single equality between capital returns and net journal movement as the completion criterion.

Next after this slice: connect `LiquidationFinalizationEvidence` to the existing repository interfaces, implement the Mudharabah/Musyarakah policy adapters against the actual project APIs, and run the golden liquidation scenarios through the real transaction boundary. The code here remains a proposed integration design; it has not been compiled against your repository.

//////////

## Next slice: freeze the Syirkah policy used for each financial result

Before expanding further into liquidation, make the result, allocation, and close-correction paths reproducible. If a policy changes after a period has been closed, a correction must be able to explain which rules were used for the original calculation and which approved rules were used for the corrected calculation.

The key design decision is to snapshot the policy inputs and version references at calculation time, rather than resolving “the current policy” again during a later correction.

### 1. Define an immutable policy snapshot

Keep this in the Syirkah domain or policy module, not inside the accounting journal model.

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SyirkahPolicySnapshot(
        String snapshotId,
        String tenantId,
        String ledgerId,
        String contractId,
        int contractVersion,
        String policySetVersion,
        String resultPolicyVersion,
        String profitAllocationPolicyVersion,
        String lossAllocationPolicyVersion,
        String distributionPolicyVersion,
        String settlementCorrectionPolicyVersion,
        String currencyPolicyVersion,
        String canonicalConfiguration,
        String configurationHash,
        Instant capturedAt,
        String capturedBy) {

    public SyirkahPolicySnapshot {
        requireText(snapshotId, "snapshotId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(policySetVersion, "policySetVersion");
        requireText(resultPolicyVersion, "resultPolicyVersion");
        requireText(profitAllocationPolicyVersion,
                "profitAllocationPolicyVersion");
        requireText(lossAllocationPolicyVersion,
                "lossAllocationPolicyVersion");
        requireText(distributionPolicyVersion,
                "distributionPolicyVersion");
        requireText(settlementCorrectionPolicyVersion,
                "settlementCorrectionPolicyVersion");
        requireText(currencyPolicyVersion, "currencyPolicyVersion");
        requireText(canonicalConfiguration, "canonicalConfiguration");
        requireText(configurationHash, "configurationHash");
        Objects.requireNonNull(capturedAt, "capturedAt");
        requireText(capturedBy, "capturedBy");

        if (contractVersion < 1) {
            throw new IllegalArgumentException(
                    "contractVersion must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }
    }
}
```

`canonicalConfiguration` must be a deterministic serialization of the effective configuration—not an arbitrary map serialization whose key ordering may change. Compute `configurationHash` from that canonical representation in the snapshot factory, using a cryptographic hash such as SHA-256. The factory should verify the hash before persistence or reuse.

The snapshot is immutable evidence. If a policy changes, create a new policy version and a new snapshot; do not update the old one.

### 2. Resolve effective policies at the calculation boundary

The resolver should return an explicit, versioned policy set. It must fail closed if any required policy is missing, disabled, or incompatible with the contract.

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

import io.smallrye.mutiny.Uni;

import java.time.LocalDate;

public interface SyirkahPolicySetResolver {

    Uni<ResolvedSyirkahPolicySet> resolveForCalculation(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate effectiveDate);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

public record ResolvedSyirkahPolicySet(
        VersionedPolicy resultPolicy,
        VersionedPolicy profitAllocationPolicy,
        VersionedPolicy lossAllocationPolicy,
        VersionedPolicy distributionPolicy,
        VersionedPolicy settlementCorrectionPolicy,
        VersionedPolicy currencyPolicy) {
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

public record VersionedPolicy(
        String policyId,
        String version,
        String configurationHash) {

    public VersionedPolicy {
        if (policyId == null || policyId.isBlank()
                || version == null || version.isBlank()
                || configurationHash == null
                || configurationHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Policy identity, version and configuration hash are required");
        }
    }
}
```

The resolver should use the contract's effective date and the tenant's policy scope. It should not assume that the latest version is always effective for a historical period.

At the application boundary, the flow becomes:

```
Load effective contract version
        ↓
Resolve versioned policy set for effective date
        ↓
Capture immutable policy snapshot
        ↓
Calculate financial result
        ↓
Allocate profit / loss under captured policies
        ↓
Persist result + allocation + snapshot references
        ↓
Reconcile and continue close
```

Persist the `policySnapshotId` on every material calculation artifact: financial result, allocation, distribution obligation, and close snapshot. Where a result is composed from several calculations, retain the same snapshot reference unless a documented policy boundary requires otherwise.

### 3. Enforce policy consistency during correction

A correction should distinguish two cases:

- Reproduction: rerun the original calculation under its original snapshot to verify the original evidence.
- Correction: calculate the revised result using the explicitly approved policy snapshot for the correction.

Do not silently use the current policy in either case.

```
package tech.kayys.syirkah.accounting.application.syirkah.correction;

public record SyirkahCorrectionPolicyContext(
        String correctionId,
        String originalPolicySnapshotId,
        String correctedPolicySnapshotId,
        String approvedBy,
        String approvalReference) {

    public SyirkahCorrectionPolicyContext {
        requireText(correctionId, "correctionId");
        requireText(originalPolicySnapshotId,
                "originalPolicySnapshotId");
        requireText(correctedPolicySnapshotId,
                "correctedPolicySnapshotId");
        requireText(approvedBy, "approvedBy");
        requireText(approvalReference, "approvalReference");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }
    }
}
```

The policy gate should verify that both snapshots exist, belong to the same tenant and ledger as the correction, and refer to the expected contract and period. It should also verify the approval record for using the corrected policy. A non-null `approvedBy` string is not sufficient proof of authorization; validate it against the persisted approval workflow.

### 4. Persist policy snapshots and prevent accidental mutation

Use a dedicated table instead of embedding the only copy in a mutable configuration record.

```
CREATE TABLE syirkah_policy_snapshot (
    tenant_id                         VARCHAR(128) NOT NULL,
    ledger_id                         VARCHAR(128) NOT NULL,
    snapshot_id                       VARCHAR(128) NOT NULL,
    contract_id                       VARCHAR(128) NOT NULL,
    contract_version                  INTEGER NOT NULL,
    policy_set_version                VARCHAR(128) NOT NULL,
    result_policy_version             VARCHAR(128) NOT NULL,
    profit_allocation_policy_version  VARCHAR(128) NOT NULL,
    loss_allocation_policy_version    VARCHAR(128) NOT NULL,
    distribution_policy_version       VARCHAR(128) NOT NULL,
    settlement_policy_version         VARCHAR(128) NOT NULL,
    currency_policy_version           VARCHAR(128) NOT NULL,
    canonical_configuration           TEXT NOT NULL,
    configuration_hash                VARCHAR(128) NOT NULL,
    captured_at                       TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_by                       VARCHAR(128) NOT NULL,

    PRIMARY KEY (tenant_id, ledger_id, snapshot_id),

    CHECK (contract_version > 0)
);
```

Recommended persistence rules:

- Grant the application role `INSERT` and `SELECT` for snapshots, but no ordinary `UPDATE` or `DELETE`.
- Recompute the hash from the canonical configuration on read or at integrity-check boundaries.
- Verify that each calculation's `policySnapshotId` resolves within its tenant and ledger.
- Keep policy definitions/version history separate from snapshots of the effective policy inputs.
- Retain snapshots for at least as long as the financial records that depend on them.

The database permissions and hash verification are both important: immutability should not depend solely on Java records.

### 5. Add focused tests

| Test                                            | Expected result                                                |
| ----------------------------------------------- | -------------------------------------------------------------- |
| Same configuration captured twice               | Same configuration hash                                        |
| Configuration changes                           | New hash and new snapshot                                      |
| Historical period recalculated                  | Original policy version remains available                      |
| Correction uses current policy without approval | Blocked                                                        |
| Policy missing for contract type                | Calculation fails explicitly                                   |
| Snapshot belongs to another tenant              | Access rejected                                                |
| Persisted configuration no longer matches hash  | Integrity gate fails                                           |
| Retry of same calculation                       | Existing snapshot/artifact reused according to idempotency key |

For deterministic hashing, test semantic equivalence: two configurations with identical values but different input-map ordering should produce the same canonical representation and hash.

## Next implementation order

1. Implement `SyirkahPolicySnapshotFactory` with canonical serialization and hash verification.
2. Implement the repository and migration for immutable snapshots.
3. Connect the resolver to the existing effective-dated contract and policy implementations.
4. Add `PolicySnapshotIntegrityGate` to the close validation pipeline.
5. Persist snapshot references on result, allocation, distribution, and close artifacts.
6. Add a correction integration test proving the original calculation remains reproducible after a policy version changes.

This is still a proposed code slice based on the framework inventory, not a compiled patch. The next useful step is to wire the snapshot factory and integrity gate into the existing close processor, so policy versioning becomes part of the actual close path rather than a standalone capability.

///////////

## Next: Make policy snapshots part of the actual close workflow

The key change is to ensure every Syirkah close calculation is tied to an immutable, verifiable policy snapshot. Retrying a close should reuse the same snapshot; a deliberate recalculation under changed policies should create a new snapshot and record the reason.

The code below is a proposed extension to the existing implementation, not a claim that the repository has been patched or compiled.

### 1. Add the snapshot factory

The factory resolves the applicable policy set, builds a canonical configuration, computes its SHA-256 hash, and persists the snapshot. The policy resolver must return version-pinned policies, not merely the latest policy implementations.

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import io.smallrye.mutiny.Uni;

public final class SyirkahPolicySnapshotFactory {

    private final SyirkahPolicySetResolver resolver;
    private final SyirkahPolicySnapshotRepository repository;
    private final PolicyConfigurationSerializer serializer;
    private final ClockProvider clock;

    public SyirkahPolicySnapshotFactory(
            SyirkahPolicySetResolver resolver,
            SyirkahPolicySnapshotRepository repository,
            PolicyConfigurationSerializer serializer,
            ClockProvider clock) {
        this.resolver = resolver;
        this.repository = repository;
        this.serializer = serializer;
        this.clock = clock;
    }

    public Uni<SyirkahPolicySnapshot> capture(
            PolicySnapshotRequest request) {

        return resolver.resolveForCalculation(
                        request.tenantId(),
                        request.ledgerId(),
                        request.contractId(),
                        request.effectiveDate())
                .flatMap(policySet -> {
                    String canonicalConfiguration =
                            serializer.serializeCanonical(
                                    policySet.canonicalConfiguration());

                    String configurationHash =
                            sha256(canonicalConfiguration);

                    SyirkahPolicySnapshot candidate =
                            new SyirkahPolicySnapshot(
                                    request.snapshotId(),
                                    request.tenantId(),
                                    request.ledgerId(),
                                    request.contractId(),
                                    policySet.contractVersion(),
                                    policySet.policySetVersion(),
                                    policySet.resultPolicy().version(),
                                    policySet.profitAllocationPolicy().version(),
                                    policySet.lossAllocationPolicy().version(),
                                    policySet.distributionPolicy().version(),
                                    policySet.settlementCorrectionPolicy().version(),
                                    policySet.currencyPolicy().version(),
                                    canonicalConfiguration,
                                    configurationHash,
                                    clock.now(),
                                    request.capturedBy());

                    return repository.saveIfAbsent(candidate)
                            .map(saved -> {
                                verifyIdentity(candidate, saved);
                                verifyHash(saved);
                                return saved;
                            });
                });
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static void verifyHash(SyirkahPolicySnapshot snapshot) {
        String actual = sha256(snapshot.canonicalConfiguration());

        if (!MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                snapshot.configurationHash()
                        .getBytes(StandardCharsets.US_ASCII))) {
            throw new PolicySnapshotIntegrityException(
                    "Policy snapshot hash does not match its configuration");
        }
    }

    private static void verifyIdentity(
            SyirkahPolicySnapshot candidate,
            SyirkahPolicySnapshot persisted) {
        if (!candidate.snapshotId().equals(persisted.snapshotId())
                || !candidate.tenantId().equals(persisted.tenantId())
                || !candidate.ledgerId().equals(persisted.ledgerId())) {
            throw new PolicySnapshotIntegrityException(
                    "Snapshot ID is already bound to a different scope");
        }
    }
}
```

The example uses small supporting abstractions so serialization and time remain testable:

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import io.smallrye.mutiny.Uni;

public interface PolicyConfigurationSerializer {
    String serializeCanonical(Map<String, Object> configuration);
}

public interface ClockProvider {
    Instant now();
}

public record PolicySnapshotRequest(
        String snapshotId,
        String tenantId,
        String ledgerId,
        String contractId,
        LocalDate effectiveDate,
        String capturedBy) {
}

public interface SyirkahPolicySnapshotRepository {

    Uni<SyirkahPolicySnapshot> saveIfAbsent(
            SyirkahPolicySnapshot snapshot);

    Uni<java.util.Optional<SyirkahPolicySnapshot>> findById(
            String tenantId,
            String ledgerId,
            String snapshotId);
}
```

`saveIfAbsent` must be backed by a database uniqueness constraint and a transaction. If the same ID is retried with different policy content, the repository must return the existing record and let the factory reject the mismatch rather than overwrite it.

### 2. Make canonical serialization deterministic

The serializer must produce identical bytes for equivalent policy configuration regardless of map insertion order. Do not use `Map.toString()` or rely on an unspecified serializer configuration.

For a Jackson-based implementation:

```
package tech.kayys.syirkah.accounting.infrastructure.policy;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import tech.kayys.syirkah.accounting.domain.syirkah.policy
        .PolicyConfigurationSerializer;

public final class JacksonPolicyConfigurationSerializer
        implements PolicyConfigurationSerializer {

    private final ObjectMapper mapper;

    public JacksonPolicyConfigurationSerializer(ObjectMapper applicationMapper) {
        this.mapper = applicationMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public String serializeCanonical(Map<String, Object> configuration) {
        try {
            return mapper.writeValueAsString(new TreeMap<>(configuration));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Unable to canonicalize Syirkah policy configuration",
                    exception);
        }
    }
}
```

Important: this assumes the policy configuration contains only supported, deterministic value types. Normalize decimals, dates, enums, and nested maps before serialization; avoid floating-point values for financial configuration. If policy configuration evolves, retain the canonicalization format version so historical hashes can still be verified.

### 3. Add the policy snapshot integrity gate

The gate checks that the close references a snapshot within the same tenant and ledger, and that the stored configuration still matches its hash.

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

public final class PolicySnapshotIntegrityGate
        implements CloseValidationGate {

    private final SyirkahPolicySnapshotRepository repository;

    public PolicySnapshotIntegrityGate(
            SyirkahPolicySnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public Uni<CloseGateResult> validate(CloseValidationContext context) {
        if (context.policySnapshotId() == null
                || context.policySnapshotId().isBlank()) {
            return Uni.createFrom().item(
                    CloseGateResult.blocked(
                            "POLICY_SNAPSHOT_MISSING",
                            "The close has no policy snapshot reference"));
        }

        return repository.findById(
                        context.tenantId(),
                        context.ledgerId(),
                        context.policySnapshotId())
                .map(found -> validateSnapshot(found, context));
    }

    private CloseGateResult validateSnapshot(
            Optional<SyirkahPolicySnapshot> found,
            CloseValidationContext context) {

        if (found.isEmpty()) {
            return CloseGateResult.blocked(
                    "POLICY_SNAPSHOT_NOT_FOUND",
                    "The referenced policy snapshot does not exist");
        }

        SyirkahPolicySnapshot snapshot = found.get();

        if (!context.tenantId().equals(snapshot.tenantId())
                || !context.ledgerId().equals(snapshot.ledgerId())) {
            return CloseGateResult.blocked(
                    "POLICY_SNAPSHOT_SCOPE_MISMATCH",
                    "The policy snapshot belongs to a different scope");
        }

        String calculatedHash =
                SyirkahPolicySnapshotFactory.sha256(
                        snapshot.canonicalConfiguration());

        boolean hashMatches = MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.US_ASCII),
                snapshot.configurationHash()
                        .getBytes(StandardCharsets.US_ASCII));

        if (!hashMatches) {
            return CloseGateResult.blocked(
                    "POLICY_SNAPSHOT_HASH_MISMATCH",
                    "Policy configuration integrity verification failed");
        }

        return CloseGateResult.passed(
                "POLICY_SNAPSHOT_INTEGRITY_VERIFIED");
    }
}
```

This assumes the existing close-validation result type supports `blocked(...)` and `passed(...)` factories; adapt those calls to the actual API. The gate verifies the snapshot's integrity and scope. It does not, by itself, prove that the policy was approved or that the policy's fiqh interpretation is correct. Those remain separate checks.

### 4. Extend `CloseValidationContext` compatibly

The gate needs the policy snapshot ID independently of the close snapshot ID. Add the new field while preserving the existing six-argument constructor.

```
package tech.kayys.syirkah.accounting.domain.close;

public record CloseValidationContext(
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String correctionId,
        String policySnapshotId) {

    public CloseValidationContext(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String closeCycleId,
            String snapshotId,
            String correctionId) {
        this(
                tenantId,
                ledgerId,
                fiscalPeriodId,
                closeCycleId,
                snapshotId,
                correctionId,
                null);
    }

    public boolean isCorrection() {
        return correctionId != null && !correctionId.isBlank();
    }
}
```

The compatibility constructor prevents existing callers from failing to compile, but those callers will have a missing policy snapshot until they are updated. For Syirkah-enabled closes, the pipeline must treat that as a blocking validation failure.

### 5. Wire the snapshot into `SyirkahCloseProcessorImpl`

The snapshot must be created or retrieved before the financial result is calculated, not as an afterthought during final validation.

Recommended flow:

```
Receive close request
    ↓
Resolve effective contract and pinned policy versions
    ↓
Capture or reuse immutable policy snapshot
    ↓
Calculate financial result using those exact policies
    ↓
Calculate partner allocations and loss responsibility
    ↓
Persist close calculation with policySnapshotId
    ↓
Create close snapshot referencing policySnapshotId
    ↓
Run close validation pipeline
    ↓
Lock period only if every mandatory gate passes
```

Extend the persisted close calculation with a reference to the policy snapshot:

```
public record CloseCalculation(
        String calculationId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String policySnapshotId,
        String resultId,
        String calculationHash) {
}
```

If `CloseCalculation` is already a class or record with additional fields, add `policySnapshotId` to the existing model rather than replacing it with this abbreviated example.

At the processor level, the change should resemble:

```
public Uni<CloseCalculation> calculateClose(CloseRequest request) {
    PolicySnapshotRequest snapshotRequest =
            policySnapshotRequestFactory.from(request);

    return policySnapshotFactory.capture(snapshotRequest)
            .flatMap(policySnapshot ->
                    calculationService.calculate(
                            request,
                            policySnapshot))
            .flatMap(calculation ->
                    closeCalculationRepository.saveIfAbsent(calculation));
}
```

Here, `calculationService.calculate(request, policySnapshot)` is an intended extension point. It must instantiate or resolve the calculation policies using the versions recorded in the snapshot; it must not silently resolve the latest policy again.

For retries, use a stable calculation/snapshot identity derived from the close cycle and calculation attempt. A changed policy should not silently replace the snapshot used by an existing calculation. A new calculation under changed policies needs a new identity and explicit correction or recalculation context.

### 6. Register the gate in the close validation pipeline

Add the policy snapshot gate to the existing ordered list of validation gates, after the close has established which snapshot it is validating and before the period is finalized.

```
List<CloseValidationGate> gates = List.of(
        closeTaskGate,
        journalReconciliationGate,
        syirkahResultReconciliationGate,
        partnerMovementAccountingGate,
        partnerPositionReconciliationGate,
        distributionSettlementCorrectionGate,
        distributionSettlementGate,
        syirkahSnapshotIntegrityGate,
        correctionReconciliationGate,
        policySnapshotIntegrityGate
);
```

The exact constructor and gate type names should follow the existing pipeline implementation. If the pipeline already has a dependency-injection registration mechanism, register the gate there instead of creating a second pipeline.

The finalization transaction must also recheck the period and snapshot state under the appropriate database locks. A successful earlier validation is not a substitute for checking state again at commit time.

### 7. Database constraints and permissions

Keep the snapshot immutable at the persistence layer as well as in Java.

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_policy_snapshot_scope
    ON syirkah_policy_snapshot (
        tenant_id,
        ledger_id,
        snapshot_id
    );
```

Add a foreign key from the close calculation or close snapshot table to the policy snapshot, using the project's actual table and column names. Where the schema permits it, include tenant and ledger in the relationship so that a reference cannot cross scopes.

For the application database role, grant only the required read and insert permissions on the policy snapshot table. If `saveIfAbsent` uses an upsert, ensure the upsert cannot update existing snapshot content. Database permissions should be tested against the actual role used by the service.

### 8. Add focused tests before enabling the gate

| Test                                                | Expected behavior                                                                                     |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Capture the same request twice                      | Existing snapshot reused; content unchanged                                                           |
| Capture a new policy version                        | New snapshot identity and corresponding version metadata                                              |
| Change configuration but reuse the same snapshot ID | Operation rejected                                                                                    |
| Alter stored configuration without changing hash    | Integrity gate blocks close                                                                           |
| Supply another tenant's snapshot ID                 | Not found within the requested scope or explicitly blocked                                            |
| Calculate close with no policy snapshot             | Syirkah close cannot finalize                                                                         |
| Retry after calculation persistence                 | Same policy snapshot remains attached                                                                 |
| Correct a previously locked close                   | Original policy snapshot remains unchanged; corrected calculation references its own approved context |

Next implementation target: integrate this with the existing `SyirkahCloseProcessorImpl` and its actual repository and transaction APIs. This slice establishes the interfaces and invariants; the final wiring should use the project's current types and persistence conventions rather than introduce parallel infrastructure.


/////////////

## Next: Make Syirkah close calculations reproducible and auditable

With policy snapshots wired into the close path, the next step is to make the calculation itself replayable. A policy hash proves which configuration was captured; it does not prove which financial inputs were used, which outputs were produced, or whether a retry changed the result.

The design should preserve three separate immutable records:

1. Policy snapshot — which contract and policy versions governed the calculation.
2. Calculation evidence — which financial inputs and movements were included.
3. Close result — the calculated result, partner allocations, and reconciliation status.

A correction creates new evidence and a new result. It must not rewrite the original locked close.

### 1. Define immutable calculation evidence

Create this in the existing Syirkah accounting domain module.

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SyirkahCalculationEvidence(
        String evidenceId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String contractId,
        int contractVersion,
        String policySnapshotId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<CalculationInputReference> inputReferences,
        String canonicalInputManifest,
        String inputManifestHash,
        String calculationVersion,
        String resultHash,
        String createdBy,
        Instant createdAt,
        String supersedesEvidenceId) {

    public SyirkahCalculationEvidence {
        requireText(evidenceId, "evidenceId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(fiscalPeriodId, "fiscalPeriodId");
        requireText(closeCycleId, "closeCycleId");
        requireText(contractId, "contractId");
        requireText(policySnapshotId, "policySnapshotId");
        requireText(canonicalInputManifest, "canonicalInputManifest");
        requireText(inputManifestHash, "inputManifestHash");
        requireText(calculationVersion, "calculationVersion");
        requireText(resultHash, "resultHash");
        requireText(createdBy, "createdBy");

        if (contractVersion < 1) {
            throw new IllegalArgumentException(
                    "contractVersion must be positive");
        }

        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(inputReferences, "inputReferences");
        Objects.requireNonNull(createdAt, "createdAt");

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "periodEnd must not precede periodStart");
        }

        inputReferences = List.copyOf(inputReferences);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

Each input reference identifies a source record and the exact revision used:

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import java.time.Instant;
import java.util.Objects;

public record CalculationInputReference(
        String inputType,
        String sourceId,
        String sourceRevision,
        String contentHash,
        Instant effectiveAt) {

    public CalculationInputReference {
        if (inputType == null || inputType.isBlank()
                || sourceId == null || sourceId.isBlank()
                || sourceRevision == null || sourceRevision.isBlank()
                || contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Input type, source ID, revision, and hash are required");
        }

        Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
```

Input types should cover the records actually consumed by your existing calculation, such as:

- Financial result inputs and approved adjustments.
- Effective Syirkah contract and partner agreement revisions.
- Posted partner-account movements.
- Relevant journal reconciliation evidence.
- The policy snapshot itself.

Do not include a record just because it exists in the system. Include it because the calculation actually depends on it.

### 2. Build a deterministic input manifest

The manifest must be sorted and serialized canonically before hashing. The same set of references should produce the same hash regardless of repository return order.

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class CalculationManifestFactory {

    private final CalculationManifestSerializer serializer;

    public CalculationManifestFactory(
            CalculationManifestSerializer serializer) {
        this.serializer = serializer;
    }

    public CalculationManifest create(
            List<CalculationInputReference> references) {

        List<CalculationInputReference> ordered = references.stream()
                .sorted(Comparator
                        .comparing(CalculationInputReference::inputType)
                        .thenComparing(CalculationInputReference::sourceId)
                        .thenComparing(CalculationInputReference::sourceRevision))
                .toList();

        rejectDuplicateReferences(ordered);

        String canonical = serializer.serialize(ordered);

        return new CalculationManifest(
                canonical,
                sha256(canonical),
                ordered);
    }

    private static void rejectDuplicateReferences(
            List<CalculationInputReference> references) {

        for (int i = 1; i < references.size(); i++) {
            CalculationInputReference previous = references.get(i - 1);
            CalculationInputReference current = references.get(i);

            if (previous.inputType().equals(current.inputType())
                    && previous.sourceId().equals(current.sourceId())
                    && previous.sourceRevision().equals(
                            current.sourceRevision())) {
                throw new IllegalArgumentException(
                        "Duplicate calculation input reference: "
                                + current.sourceId());
            }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }
}
```

Supporting types:

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import java.util.List;

public record CalculationManifest(
        String canonicalContent,
        String hash,
        List<CalculationInputReference> references) {

    public CalculationManifest {
        references = List.copyOf(references);
    }
}

public interface CalculationManifestSerializer {
    String serialize(List<CalculationInputReference> references);
}
```

The serializer should use a stable field order and stable encoding. Sorting references alone is insufficient if their serialization is nondeterministic.

### 3. Separate calculation from evidence persistence

The calculation engine should be deterministic for a given policy snapshot, input manifest, and calculation version.

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationEngine {

    Uni<SyirkahCalculationOutput> calculate(
            SyirkahCalculationContext context);
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.calculation;

import java.util.List;

public record SyirkahCalculationOutput(
        String resultId,
        String canonicalResult,
        String resultHash,
        List<PartnerAllocationOutput> allocations) {

    public SyirkahCalculationOutput {
        allocations = List.copyOf(allocations);
    }
}
```

`SyirkahCalculationContext` should contain the immutable policy snapshot, the pinned contract version, the input manifest, and the period being calculated. The engine must not reach out to repositories to fetch unpinned “latest” data during calculation.

The output should contain calculated values, not side effects. Posting journals, creating distribution obligations, and settling partner balances belong to subsequent application workflows.

### 4. Add an idempotent calculation application service

```
package tech.kayys.syirkah.accounting.application.syirkah;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationRepository {

    Uni<java.util.Optional<PersistedSyirkahCalculation>> findByKey(
            String tenantId,
            String ledgerId,
            String idempotencyKey);

    Uni<PersistedSyirkahCalculation> saveIfAbsent(
            PersistedSyirkahCalculation calculation);
}
```

The application service should perform these steps:

1. Resolve the policy snapshot and pinned input revisions.
2. Build the canonical input manifest.
3. Look up the calculation using a stable idempotency key.
4. If a matching calculation already exists, return it.
5. Otherwise, run the deterministic calculation engine.
6. Persist the output and evidence atomically.
7. If another worker wins the concurrent insert, load the existing record and verify that its manifest and result hashes match.

An existing calculation must not be silently overwritten when the inputs or result differ. A changed input set means a different calculation identity or an explicitly authorized correction.

### 5. Enforce uniqueness and immutable evidence in the database

Adapt the names to the project's actual schema.

```
CREATE TABLE syirkah_calculation_evidence (
    tenant_id               VARCHAR(100) NOT NULL,
    ledger_id               VARCHAR(100) NOT NULL,
    evidence_id             VARCHAR(100) NOT NULL,
    close_cycle_id          VARCHAR(100) NOT NULL,
    policy_snapshot_id      VARCHAR(100) NOT NULL,
    input_manifest_hash     CHAR(64) NOT NULL,
    calculation_version     VARCHAR(100) NOT NULL,
    result_hash              CHAR(64) NOT NULL,
    supersedes_evidence_id  VARCHAR(100),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by               VARCHAR(200) NOT NULL,

    PRIMARY KEY (tenant_id, ledger_id, evidence_id),

    FOREIGN KEY (tenant_id, ledger_id, policy_snapshot_id)
        REFERENCES syirkah_policy_snapshot
            (tenant_id, ledger_id, snapshot_id)
);
```

For the persisted calculation table, enforce a unique key on the tenant, ledger, and idempotency key. Store the canonical manifest or its normalized input-reference rows as well as the hash; a hash alone cannot reconstruct the input set.

If evidence is intended to be immutable, deny ordinary `UPDATE` and `DELETE` permissions on its rows. A correction should create a new evidence record linked through `supersedes_evidence_id`.

### 6. Add the replay and tamper tests

| Scenario                                                | Required result                                                        |
| ------------------------------------------------------- | ---------------------------------------------------------------------- |
| Same pinned inputs and policy, same calculation version | Identical result hash                                                  |
| Repository returns inputs in a different order          | Identical manifest hash                                                |
| A posted movement changes after the original close      | Original evidence remains unchanged                                    |
| A new approved adjustment is included                   | New manifest and recalculation                                         |
| Stored input hash does not match canonical manifest     | Replay or close validation is blocked                                  |
| Two workers submit the same idempotency key             | One persisted calculation; conflicting content is rejected             |
| Correction recalculates a locked period                 | New evidence references the correction and preserves original evidence |
| A required source revision is unavailable               | Calculation fails closed rather than substituting current data         |

One important boundary: deterministic replay is only meaningful if the source revisions can still be retrieved and their contents verified. Keep the original canonical inputs or durable revision references according to your retention requirements.

## What comes next

The next slice is to connect this evidence to the partner allocation and profit-sharing calculation. That is where Syirkah-specific rules become financially consequential: the system must distinguish the realized business result, the agreed profit-sharing ratio (nisbah), loss-allocation rules, and amounts that are actually distributable. Those stages should have separate outputs and reconciliation checks rather than collapsing into a single “partner profit” number.

////////////

## Next: Implement the Syirkah profit-allocation pipeline

The next extension is to make profit-sharing calculations explicit and auditable without replacing the existing accounting engine.

Keep three financial stages separate:

1. Determine the financial result for the period using the existing accounting and close logic.
2. Allocate the result under the applicable contract and approved Syirkah policy, using the agreed nisbah for profit and the applicable loss-allocation rules for loss.
3. Reconcile the allocations before creating distribution obligations or accounting entries.

A calculated allocation is not automatically an approved distribution, and an approved distribution is not automatically a settled payment.

### 1. Introduce a distinct allocation result

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;
import java.util.Objects;

public record PartnerProfitAllocation(
        String allocationId,
        String tenantId,
        String ledgerId,
        String contractId,
        int contractVersion,
        String partnerId,
        String financialResultId,
        String policySnapshotId,
        BigDecimal financialResultAmount,
        BigDecimal agreedProfitRatio,
        BigDecimal allocatedProfitAmount,
        String currencyCode) {

    public PartnerProfitAllocation {
        requireText(allocationId, "allocationId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(partnerId, "partnerId");
        requireText(financialResultId, "financialResultId");
        requireText(policySnapshotId, "policySnapshotId");
        requireText(currencyCode, "currencyCode");

        if (contractVersion < 1) {
            throw new IllegalArgumentException(
                    "contractVersion must be positive");
        }

        Objects.requireNonNull(financialResultAmount);
        Objects.requireNonNull(agreedProfitRatio);
        Objects.requireNonNull(allocatedProfitAmount);

        if (agreedProfitRatio.signum() < 0
                || agreedProfitRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Profit ratio must be between zero and one");
        }

        if (allocatedProfitAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Allocated profit cannot be negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

This record represents a profit allocation only. Do not use it to represent losses, capital returns, distributions, or cash settlements. Those are different business events and should have their own records.

### 2. Calculate allocations using the pinned agreement

Use `BigDecimal` throughout; never use floating-point arithmetic for financial values.

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class SyirkahProfitAllocationCalculator {

    public List<CalculatedPartnerProfit> calculate(
            BigDecimal distributableProfit,
            List<PartnerProfitRatio> ratios,
            int currencyScale) {

        if (distributableProfit.signum() < 0) {
            throw new IllegalArgumentException(
                    "Profit allocation requires a non-negative profit result");
        }

        if (currencyScale < 0 || currencyScale > 9) {
            throw new IllegalArgumentException(
                    "Unsupported currency scale");
        }

        validateRatios(ratios);

        BigDecimal totalRatio = ratios.stream()
                .map(PartnerProfitRatio::ratio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRatio.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(
                    "Agreed profit ratios must total exactly 1");
        }

        List<CalculatedPartnerProfit> allocations = ratios.stream()
                .map(partner -> new CalculatedPartnerProfit(
                        partner.partnerId(),
                        partner.ratio(),
                        distributableProfit
                                .multiply(partner.ratio())
                                .setScale(
                                        currencyScale,
                                        RoundingMode.DOWN)))
                .toList();

        return allocateRoundingRemainder(
                distributableProfit,
                allocations,
                currencyScale);
    }

    private void validateRatios(List<PartnerProfitRatio> ratios) {
        if (ratios == null || ratios.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one partner ratio is required");
        }

        var partnerIds = new java.util.HashSet<String>();

        for (PartnerProfitRatio partner : ratios) {
            if (partner.partnerId() == null
                    || partner.partnerId().isBlank()) {
                throw new IllegalArgumentException(
                        "Partner ID is required");
            }

            if (!partnerIds.add(partner.partnerId())) {
                throw new IllegalArgumentException(
                        "Duplicate partner ratio: " + partner.partnerId());
            }

            if (partner.ratio() == null
                    || partner.ratio().signum() < 0
                    || partner.ratio().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "Invalid partner profit ratio");
            }
        }
    }

    private List<CalculatedPartnerProfit> allocateRoundingRemainder(
            BigDecimal total,
            List<CalculatedPartnerProfit> allocations,
            int currencyScale) {

        BigDecimal allocated = allocations.stream()
                .map(CalculatedPartnerProfit::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainder = total
                .setScale(currencyScale, RoundingMode.UNNECESSARY)
                .subtract(allocated);

        if (remainder.signum() == 0) {
            return allocations;
        }

        // The rounding policy must be explicitly configured.
        // Do not silently assign the remainder to an arbitrary partner.
        throw new AllocationRoundingException(
                "Allocation remainder requires an explicit rounding policy: "
                        + remainder.toPlainString());
    }
}
```

Supporting types:

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;

public record PartnerProfitRatio(
        String partnerId,
        BigDecimal ratio) {
}

public record CalculatedPartnerProfit(
        String partnerId,
        BigDecimal ratio,
        BigDecimal amount) {
}
```

The explicit remainder exception is intentional. A production implementation should inject a versioned `AllocationRoundingPolicy` that determines how minor-unit remainders are handled and records that policy in the calculation evidence. The policy must not silently change between retries.

The `RoundingMode.UNNECESSARY` call also means the input profit must already conform to the currency's supported scale. If the existing accounting system has a different currency-normalization rule, reuse that rule rather than introducing a conflicting one.

### 3. Add allocation reconciliation

Before allocations become distribution obligations, verify that the output is complete and conserves the distributable amount.

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;
import java.util.List;

public final class SyirkahAllocationReconciler {

    public AllocationReconciliation reconcile(
            BigDecimal expectedProfit,
            List<CalculatedPartnerProfit> allocations) {

        if (expectedProfit == null || expectedProfit.signum() < 0) {
            throw new IllegalArgumentException(
                    "Expected profit must be non-negative");
        }

        if (allocations == null || allocations.isEmpty()) {
            return AllocationReconciliation.failed(
                    "NO_PARTNER_ALLOCATIONS");
        }

        BigDecimal actualTotal = BigDecimal.ZERO;

        for (CalculatedPartnerProfit allocation : allocations) {
            if (allocation.amount() == null
                    || allocation.amount().signum() < 0) {
                return AllocationReconciliation.failed(
                        "INVALID_ALLOCATION_AMOUNT");
            }

            actualTotal = actualTotal.add(allocation.amount());
        }

        BigDecimal difference = expectedProfit.subtract(actualTotal);

        if (difference.signum() != 0) {
            return AllocationReconciliation.failed(
                    "ALLOCATION_TOTAL_MISMATCH");
        }

        return AllocationReconciliation.passed(actualTotal);
    }
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;

public record AllocationReconciliation(
        boolean passed,
        String errorCode,
        BigDecimal allocatedTotal) {

    public static AllocationReconciliation passed(BigDecimal total) {
        return new AllocationReconciliation(true, null, total);
    }

    public static AllocationReconciliation failed(String errorCode) {
        return new AllocationReconciliation(false, errorCode, null);
    }
}
```

In the complete implementation, this reconciler should also verify that:

- Every eligible partner in the effective agreement has exactly one allocation.
- No allocation references a partner outside that agreement.
- All records share the same tenant, ledger, contract version, financial result, and policy snapshot.
- The ratios match the versioned agreement.
- The allocation output hash matches the persisted calculation evidence.

### 4. Keep loss allocation as a separate policy path

Do not pass a negative financial result through the profit allocation calculator. Resolve the applicable loss policy and produce a distinct loss-allocation result.

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import java.math.BigDecimal;
import java.util.List;

public interface SyirkahLossAllocationService {

    List<CalculatedPartnerLoss> allocate(
            LossAllocationContext context);
}

public record CalculatedPartnerLoss(
        String partnerId,
        BigDecimal amount,
        String responsibilityReason) {
}
```

The service should delegate to the existing `LossAllocationPolicyResolver` and applicable policy implementation, rather than duplicating those rules in the new allocation module. Missing policies or unsupported loss causes must block the calculation. Whether a particular loss is attributable to ordinary business risk, negligence, breach, or another cause must be supported by the relevant recorded facts and approved policy—not inferred from the loss amount.

### 5. Persist the allocation as a versioned artifact

The application service should orchestrate the steps:

```
Pinned calculation evidence
    ↓
Resolve effective contract and partner ratios
    ↓
Determine profit, loss, or break-even result
    ↓
Run the appropriate allocation policy
    ↓
Apply explicit rounding policy
    ↓
Reconcile totals and partner eligibility
    ↓
Persist immutable allocation artifact
    ↓
Create distribution obligations only for eligible allocations
```

Recommended repository contract:

```
package tech.kayys.syirkah.accounting.domain.syirkah.allocation;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

public interface SyirkahAllocationRepository {

    Uni<Optional<PersistedSyirkahAllocation>> findByIdempotencyKey(
            String tenantId,
            String ledgerId,
            String idempotencyKey);

    Uni<PersistedSyirkahAllocation> saveIfAbsent(
            PersistedSyirkahAllocation allocation);
}
```

Enforce a database uniqueness constraint on the tenant, ledger, and idempotency key. If a retry encounters an existing artifact, verify that its input manifest hash, policy snapshot, and allocation hash match. Do not overwrite an earlier allocation.

### 6. Add tests for financial invariants

| Scenario                              | Expected behavior                                 |
| ------------------------------------- | ------------------------------------------------- |
| Valid profit and ratios               | Allocations reconcile to the distributable profit |
| Ratios total less or more than 100%   | Calculation rejected                              |
| Duplicate partner ratio               | Calculation rejected                              |
| Minor-unit rounding remainder         | Explicit rounding policy required                 |
| Loss result sent to profit calculator | Calculation rejected                              |
| Missing loss policy                   | Calculation blocked                               |
| Partner not in effective contract     | Allocation rejected                               |
| Same calculation retried              | Existing artifact reused                          |
| Changed contract version              | New calculation context required                  |
| Allocation total differs from result  | Distribution obligations cannot be approved       |

Next: connect the immutable allocation artifacts to distribution obligations and the existing journal-posting transaction. That integration should ensure each approved obligation posts exactly once, is reconciled against the allocation, and is marked `POSTED` only after its journal transaction succeeds.

////////////////

## Next: Connect partner allocations to distribution obligations and journal posting

The next extension is the transaction boundary between Syirkah allocation and accounting. The core invariant is:

An approved distribution obligation becomes `POSTED` only after its journal entry has been successfully posted in the same database transaction.

A calculated profit allocation is not itself a payment instruction. Keep the lifecycle distinct:

`CALCULATED → APPROVED → POSTED → SETTLED`

If an obligation has already been settled, a correction must preserve that history and create compensating records rather than edit the original.

### 1. Define a posting request tied to the approved obligation

Use the existing `PostJournalEntryCommand` from:

`tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand`

The distribution request should reference the exact approved obligation and allocation artifact, not accept an arbitrary amount from the caller.

```
package tech.kayys.syirkah.accounting.application.syirkah.distribution;

import java.util.Objects;

public record DistributionPostingRequest(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String obligationId,
        String allocationId,
        String allocationHash,
        String postedBy) {

    public DistributionPostingRequest {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(closeCycleId, "closeCycleId");
        requireText(obligationId, "obligationId");
        requireText(allocationId, "allocationId");
        requireText(allocationHash, "allocationHash");
        requireText(postedBy, "postedBy");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

The application service must load the obligation and allocation within the specified tenant and ledger, verify their relationship, and derive the accounting command from the approved distribution accounting policy.

### 2. Define the transactional posting operation

Reuse the existing transaction-aware journal operation so distribution posting does not open a nested transaction.

```
package tech.kayys.syirkah.accounting.application.syirkah.distribution;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.journal.JournalEntryId;

public interface DistributionPostingTransaction {

    Uni<JournalEntryId> postApprovedObligation(
            DistributionPostingRequest request);
}
```

The implementation should perform the following steps inside one database transaction:

```
BEGIN
  Lock distribution obligation
  Verify tenant, ledger, close cycle, and APPROVED status
  Load immutable allocation artifact
  Verify allocation hash and obligation amount
  Check journal idempotency key
  Build journal command using the distribution accounting policy
  Post journal using postInsideTransaction(...)
  Mark obligation POSTED with journal ID
  Persist audit event and transactional outbox record
COMMIT
```

If journal posting fails, the obligation must remain `APPROVED`. If the obligation is already `POSTED`, return its existing journal reference only after verifying that the existing posting matches the same obligation and allocation.

### 3. Make the journal source stable and idempotent

Use a stable source identity for each obligation:

```
private static final String SOURCE_TYPE = "SYIRKAH_DISTRIBUTION";

private static String sourceId(String obligationId) {
    return obligationId;
}
```

The journal command should use these values:

```
PostJournalEntryCommand command =
        PostJournalEntryCommand.builder()
                .journalEntryId(journalEntryId)
                .description(description)
                .referenceNumber(obligationId)
                .lines(lines)
                .sourceType("SYIRKAH_DISTRIBUTION")
                .sourceId(obligationId)
                .postedBy(postedBy)
                .build();
```

This builder example follows the existing command shape described earlier; adapt it if the repository's builder uses different method names.

Enforce source uniqueness in the journal database:

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_journal_entry_source
    ON journal_entry (
        tenant_id,
        ledger_id,
        source_type,
        source_id
    )
    WHERE source_type IS NOT NULL
      AND source_id IS NOT NULL;
```

The uniqueness constraint is the final concurrency safeguard. A preliminary repository lookup alone cannot prevent two workers from posting the same obligation concurrently.

### 4. Update the obligation only after posting succeeds

Keep the transition in the domain model guarded:

```
public void markPosted(
        String journalEntryId,
        String allocationId,
        String allocationHash) {

    if (status != DistributionStatus.APPROVED) {
        throw new IllegalStateException(
                "Only approved distributions can be posted");
    }

    if (journalEntryId == null || journalEntryId.isBlank()) {
        throw new IllegalArgumentException(
                "Journal entry ID is required");
    }

    if (!this.allocationId.equals(allocationId)
            || !this.allocationHash.equals(allocationHash)) {
        throw new IllegalStateException(
                "Posted allocation does not match approved allocation");
    }

    this.journalEntryId = journalEntryId;
    this.status = DistributionStatus.POSTED;
}
```

This assumes the obligation stores `allocationId` and `allocationHash`. If it currently stores only the amount and partner, extend it to reference the immutable allocation artifact. Avoid reconstructing the original allocation from mutable partner balances.

The database update should also be conditional on the previous status, for example `WHERE status = 'APPROVED'`, and verify that exactly one row was updated.

### 5. Reconcile obligations against posted journals

Implement reconciliation as a separate read operation. It should compare approved obligations with their actual posted journal entries, rather than trusting the obligation status alone.

```
package tech.kayys.syirkah.accounting.application.syirkah.distribution;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahDistributionPostingResult(
        int expectedCount,
        int postedCount,
        BigDecimal expectedAmount,
        BigDecimal postedAmount,
        List<String> errors) {

    public SyirkahDistributionPostingResult {
        errors = List.copyOf(errors);
    }

    public boolean passed() {
        return errors.isEmpty()
                && expectedCount == postedCount
                && expectedAmount.compareTo(postedAmount) == 0;
    }
}
```

The reconciliation service should detect at least:

- An approved obligation with no journal.
- A `POSTED` obligation whose journal is missing or not posted.
- A journal whose source points to an unknown obligation.
- A journal amount that differs from the approved obligation.
- Duplicate source records or a journal linked to a different allocation.
- A tenant or ledger mismatch.
- A journal posted in a locked or otherwise ineligible fiscal period.

Compare normalized monetary amounts using the currency's defined scale. For multi-currency ledgers, reconcile separately by currency and apply the existing foreign-exchange accounting rules instead of summing unlike currencies.

### 6. Integrate it with close finalization

The existing `SyirkahDistributionPostingService` can retain its public API:

```
public interface SyirkahDistributionPostingService {

    Uni<List<JournalEntryId>> postApprovedDistributions(
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String postedBy);

    Uni<SyirkahDistributionPostingResult> reconcile(
            String tenantId,
            String ledgerId,
            String closeCycleId);
}
```

The implementation should process each obligation through the transactional posting operation and then run reconciliation. Do not mark the close's distribution task complete just because the posting loop returned successfully.

The close task can be completed only when:

1. All eligible obligations have been processed.
2. Every required journal is posted.
3. Reconciliation passes with no unexplained differences.
4. No required obligation remains in an invalid or pending state.

If any check fails, retain the close task as incomplete or blocked, record the reconciliation evidence, and prevent finalization.

### 7. Add the critical tests

| Scenario                                  | Expected behavior                               |
| ----------------------------------------- | ----------------------------------------------- |
| Successful posting                        | Journal and `POSTED` obligation commit together |
| Journal posting fails                     | Obligation remains `APPROVED`                   |
| Same request retried                      | Existing matching journal is reused             |
| Two workers post concurrently             | Only one journal is created                     |
| Allocation hash changes                   | Posting is rejected                             |
| Journal posts but obligation update fails | Entire transaction rolls back                   |
| Posted obligation has no journal          | Reconciliation fails                            |
| Journal amount differs from obligation    | Reconciliation fails                            |
| Distribution is already settled           | Original settlement history remains unchanged   |
| Reconciliation has differences            | Close distribution task cannot complete         |

Next implementation target: settlement and correction handling. That should connect posted distribution obligations to immutable payment or settlement records, distinguish outstanding from settled amounts, and determine the required follow-up when a previously calculated distribution is corrected. The exact recovery, refund, or offset treatment must remain configurable and approved under the applicable Sharia policy; it should not be inferred solely from the arithmetic difference.

/////////////

## Next: Implement Syirkah distribution settlement and correction handling

The next layer connects a posted distribution obligation to the actual settlement of funds. It must preserve the distinction between profit allocated, distribution posted to the ledger, and money settled with the partner.

A settlement is an immutable financial event. If a distribution is corrected later, the original payment record remains intact; the system calculates the outstanding difference and creates an explicit recovery, refund, offset, or review workflow according to the configured and approved policy.

## 1. Define an immutable settlement record

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SyirkahDistributionSettlement(
        String settlementId,
        String tenantId,
        String ledgerId,
        String obligationId,
        BigDecimal amount,
        String currencyCode,
        String settlementReference,
        Instant settledAt,
        String settledBy,
        String correctionId) {

    public SyirkahDistributionSettlement {
        requireText(settlementId, "settlementId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(obligationId, "obligationId");
        requireText(currencyCode, "currencyCode");
        requireText(settlementReference, "settlementReference");
        requireText(settledBy, "settledBy");

        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(settledAt, "settledAt");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Settlement amount must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

A partial payment creates another settlement record. Never update an existing settlement to change its amount or mark it as paid again. Enforce uniqueness for the external settlement reference within the appropriate tenant and ledger scope.

## 2. Record settlement as a transaction

The settlement service should verify the posted obligation, prevent over-settlement unless an explicitly approved workflow permits it, persist the settlement, and update the obligation's settlement projection in one transaction.

```
package tech.kayys.syirkah.accounting.application.syirkah.settlement;

import io.smallrye.mutiny.Uni;

public interface SyirkahSettlementTransaction {

    Uni<SyirkahDistributionSettlement> settle(
            SettleDistributionCommand command);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.settlement;

import java.math.BigDecimal;
import java.time.Instant;

public record SettleDistributionCommand(
        String tenantId,
        String ledgerId,
        String obligationId,
        String settlementId,
        BigDecimal amount,
        String currencyCode,
        String settlementReference,
        Instant settledAt,
        String settledBy) {
}
```

The implementation should use the existing accounting transaction manager and lock the obligation before calculating its remaining balance.

```
BEGIN
  Lock obligation by tenant, ledger, and obligation ID
  Verify obligation status is POSTED or partially settled
  Verify currency and amount scale
  Load existing immutable settlements
  Calculate remaining amount
  Reject invalid or unapproved over-settlement
  Insert settlement using unique external reference
  Recalculate settlement state
  Persist audit event and outbox record
COMMIT
```

If settlement insertion or projection update fails, the transaction rolls back. If a request is retried, use a stable idempotency key and return the existing settlement only after confirming that the request content matches it.

A settlement record documents the recorded settlement event. If a payment provider is involved, the surrounding integration must also verify the provider's confirmed payment status; receiving a request from a client is not proof that funds moved.

## 3. Reconcile the obligation against settlements

Use a calculated settlement position rather than maintaining an independently editable paid amount.

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;

public record DistributionSettlementPosition(
        BigDecimal obligationAmount,
        BigDecimal settledAmount,
        BigDecimal outstandingAmount,
        SettlementState state) {
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

public enum SettlementState {
    UNSETTLED,
    PARTIALLY_SETTLED,
    FULLY_SETTLED,
    UNDER_SETTLED,
    OVER_SETTLED
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;
import java.util.List;

public final class DistributionSettlementCalculator {

    public DistributionSettlementPosition calculate(
            BigDecimal obligationAmount,
            List<SyirkahDistributionSettlement> settlements) {

        if (obligationAmount == null || obligationAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Obligation amount must be non-negative");
        }

        BigDecimal settled = settlements.stream()
                .map(SyirkahDistributionSettlement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = obligationAmount.subtract(settled);

        SettlementState state;
        if (settled.signum() == 0) {
            state = SettlementState.UNSETTLED;
        } else if (settled.compareTo(obligationAmount) == 0) {
            state = SettlementState.FULLY_SETTLED;
        } else if (settled.compareTo(obligationAmount) < 0) {
            state = SettlementState.PARTIALLY_SETTLED;
        } else {
            state = SettlementState.OVER_SETTLED;
        }

        return new DistributionSettlementPosition(
                obligationAmount, settled, outstanding, state);
    }
}
```

`UNDER_SETTLED` needs special care: it describes an obligation that has not been fully paid by the expected date or under the applicable settlement terms. It cannot be inferred from the arithmetic alone. Add due-date and settlement-term evaluation to distinguish a normal outstanding balance from a past-due under-settlement.

The repository must also validate that every settlement belongs to the requested obligation, tenant, ledger, and currency before aggregation. The calculator above assumes those checks have already been performed.

## 4. Evaluate the impact of a corrected distribution

When an allocation changes after settlement, compare the corrected obligation with the amount already settled. The difference determines the workflow that must be opened; it does not automatically authorize a debit, refund, or recovery.

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

public enum SettlementCorrectionDisposition {
    NO_CHANGE,
    REDUCED_OUTSTANDING,
    ADDITIONAL_SETTLEMENT_REQUIRED,
    RECOVERY_REQUIRED,
    REFUND_REQUIRED,
    OFFSET_REQUIRED,
    MANUAL_REVIEW
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;
import java.util.Objects;

public record SettlementCorrectionDecision(
        BigDecimal correctedObligationAmount,
        BigDecimal alreadySettledAmount,
        BigDecimal difference,
        SettlementState settlementState,
        SettlementCorrectionDisposition disposition,
        String policyVersion,
        String reasonCode) {

    public SettlementCorrectionDecision {
        Objects.requireNonNull(correctedObligationAmount);
        Objects.requireNonNull(alreadySettledAmount);
        Objects.requireNonNull(difference);
        Objects.requireNonNull(settlementState);
        Objects.requireNonNull(disposition);

        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Policy version is required");
        }
    }
}
```

Keep the disposition logic behind a versioned policy interface:

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;

public interface SettlementCorrectionPolicy {

    SettlementCorrectionDecision evaluate(
            BigDecimal correctedObligationAmount,
            BigDecimal alreadySettledAmount,
            SettlementState settlementState);
}
```

A default workflow policy can classify the arithmetic as follows:

| Corrected obligation compared with settled amount         | Possible workflow                |
| --------------------------------------------------------- | -------------------------------- |
| Equal                                                     | `NO_CHANGE`                      |
| Greater                                                   | `ADDITIONAL_SETTLEMENT_REQUIRED` |
| Lower, with no settlement yet                             | `REDUCED_OUTSTANDING`            |
| Lower, with a partial or full settlement                  | `RECOVERY_REQUIRED`              |
| Conflicting, incomplete, or ambiguous settlement evidence | `MANUAL_REVIEW`                  |

The policy should explicitly handle negative corrected amounts, over-settlement, past-due balances, currency mismatches, and previously initiated recoveries. `REFUND_REQUIRED` and `OFFSET_REQUIRED` should only be selected when the applicable, approved policy and facts support those workflows.

These are accounting and workflow classifications—not independent religious rulings. The applicable Sharia policy must determine the permitted treatment for the actual contract and circumstances.

## 5. Never correct a settled obligation by editing history

Create a correction case that links the original obligation, corrected allocation, settlement position, and approved disposition.

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.time.Instant;

public record SyirkahSettlementCorrectionCase(
        String correctionId,
        String tenantId,
        String ledgerId,
        String originalObligationId,
        String correctedAllocationId,
        String policySnapshotId,
        SettlementCorrectionDecision decision,
        String approvedBy,
        Instant approvedAt) {
}
```

The application workflow should:

1. Preserve the original obligation and settlement records.
2. Persist the corrected allocation and its evidence.
3. Reconcile the corrected amount against all confirmed settlements.
4. Resolve and record the versioned correction policy.
5. Create a correction case and any required follow-up obligation.
6. Require approval under maker-checker controls.
7. Post accounting effects through the existing transactional journal service.
8. Track recovery, refund, or additional settlement separately until resolved.

Do not use a generic “reverse settlement” operation to imply that cash has been returned. A reversal of an accounting entry and a real movement of funds are separate events.

## 6. Add persistence and concurrency safeguards

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_settlement_reference
    ON syirkah_distribution_settlement (
        tenant_id,
        ledger_id,
        settlement_reference
    );

CREATE INDEX IF NOT EXISTS ix_syirkah_settlement_obligation
    ON syirkah_distribution_settlement (
        tenant_id,
        ledger_id,
        obligation_id,
        settled_at
    );
```

Also add:

- A foreign key to the corresponding distribution obligation, scoped by tenant and ledger.
- An immutable settlement ID and request idempotency key.
- A transactionally maintained audit trail.
- A correction-case uniqueness rule preventing duplicate cases for the same correction and original obligation.
- Authorization checks for settlement approval and correction approval.
- Currency-scale validation using the platform's existing currency policy.

## 7. Required tests

| Scenario                                 | Expected behavior                                                    |
| ---------------------------------------- | -------------------------------------------------------------------- |
| Partial settlement                       | Outstanding balance is calculated correctly                          |
| Multiple partial settlements             | Settled total is the sum of immutable records                        |
| Duplicate external reference             | Second insert is rejected or idempotently resolved                   |
| Concurrent settlement attempts           | Row locking prevents invalid aggregate updates                       |
| Settlement exceeds obligation            | Rejected or sent through an explicitly authorized exception workflow |
| Corrected amount exceeds paid amount     | Additional settlement workflow is created                            |
| Corrected amount falls below paid amount | Recovery or review workflow is evaluated                             |
| Original settlement is corrected         | Original record remains unchanged                                    |
| Provider reports payment failure         | No confirmed settlement is recorded                                  |
| Correction approval fails                | No correction journal or follow-up obligation is posted              |

### Next: Make period close enforce settlement completeness

The next implementation slice is a `DistributionSettlementReconciliationGate` that plugs into the existing close-validation pipeline. It will block finalization when required distributions, settlements, or correction cases are unresolved, while preserving the distinction between a legitimately outstanding obligation and an unexplained financial discrepancy.

///////////

## Next: Add `DistributionSettlementReconciliationGate` to period close

This gate connects settlement tracking to the existing Syirkah close-validation pipeline. Its purpose is to prevent a close from being finalized when required distribution postings, settlement records, or correction workflows have unresolved inconsistencies.

One important distinction: an unpaid but valid distribution is not automatically a reconciliation error. Whether an outstanding obligation blocks closing depends on the applicable close policy. An unexplained mismatch, duplicate settlement, missing journal, or unapproved correction must not be silently treated as complete.

### 1. Define the reconciliation evidence

Keep the gate's result explicit and auditable.

```
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;
import java.util.List;

public record DistributionSettlementReconciliation(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        int expectedObligationCount,
        int postedObligationCount,
        int settledObligationCount,
        BigDecimal expectedDistributionAmount,
        BigDecimal postedDistributionAmount,
        BigDecimal confirmedSettlementAmount,
        BigDecimal outstandingAmount,
        List<String> errors,
        List<String> warnings) {

    public DistributionSettlementReconciliation {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean passed() {
        return errors.isEmpty();
    }
}
```

The counts have distinct meanings: `postedObligationCount` tracks obligations with valid accounting postings, while `settledObligationCount` tracks obligations that meet the configured full-settlement definition. Partial settlements must still be represented in the aggregate amounts.

### 2. Create a close-scoped reconciliation service

The gate should not query journal tables and settlement tables independently and then combine inconsistent reads. Use a read service that produces one tenant- and ledger-scoped reconciliation view, ideally from a consistent database snapshot or a transaction with suitable isolation.

```
package tech.kayys.syirkah.accounting.application.syirkah.settlement;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.settlement
        .DistributionSettlementReconciliation;

public interface DistributionSettlementReconciliationService {

    Uni<DistributionSettlementReconciliation> reconcile(
            String tenantId,
            String ledgerId,
            String closeCycleId);
}
```

The implementation should verify:

- The obligations belong to the requested close cycle and scope.
- Each posted obligation references the correct posted journal.
- Journal source type and source ID match the obligation.
- Settlement records belong to the correct obligation and currency.
- Settlement totals agree with the immutable settlement records.
- Correction cases are resolved, or explicitly permitted to remain open under the applicable close policy.
- No required posting or settlement event is missing from the reconciliation view.

### 3. Implement the validation gate

```
package tech.kayys.syirkah.accounting.application.syirkah.settlement;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.close
        .CloseValidationContext;
import tech.kayys.syirkah.accounting.domain.close
        .CloseValidationGate;
import tech.kayys.syirkah.accounting.domain.close
        .CloseGateResult;
import tech.kayys.syirkah.accounting.domain.syirkah.settlement
        .DistributionSettlementReconciliation;

public final class DistributionSettlementReconciliationGate
        implements CloseValidationGate {

    private final DistributionSettlementReconciliationService service;
    private final OutstandingDistributionClosePolicy closePolicy;

    public DistributionSettlementReconciliationGate(
            DistributionSettlementReconciliationService service,
            OutstandingDistributionClosePolicy closePolicy) {
        this.service = service;
        this.closePolicy = closePolicy;
    }

    @Override
    public Uni<CloseGateResult> validate(
            CloseValidationContext context) {

        return service.reconcile(
                        context.tenantId(),
                        context.ledgerId(),
                        context.closeCycleId())
                .map(reconciliation ->
                        evaluate(context, reconciliation));
    }

    private CloseGateResult evaluate(
            CloseValidationContext context,
            DistributionSettlementReconciliation reconciliation) {

        if (!context.tenantId().equals(reconciliation.tenantId())
                || !context.ledgerId().equals(reconciliation.ledgerId())
                || !context.closeCycleId().equals(
                        reconciliation.closeCycleId())) {
            return CloseGateResult.blocked(
                    "DISTRIBUTION_RECONCILIATION_SCOPE_MISMATCH",
                    "Reconciliation evidence does not match the close scope");
        }

        if (!reconciliation.passed()) {
            return CloseGateResult.blocked(
                    "DISTRIBUTION_SETTLEMENT_RECONCILIATION_FAILED",
                    String.join(",", reconciliation.errors()));
        }

        if (!closePolicy.allowsOutstandingDistributions(
                context, reconciliation)) {
            return CloseGateResult.blocked(
                    "OUTSTANDING_DISTRIBUTIONS_NOT_ALLOWED",
                    "Outstanding distributions violate the close policy");
        }

        return CloseGateResult.passed(
                "DISTRIBUTION_SETTLEMENT_RECONCILIATION_PASSED");
    }
}
```

The result factories and package locations here follow the previously proposed API; adapt them to the project's actual classes. A missing reconciliation result or an infrastructure failure must fail closed—not be converted into a passing result.

The close policy should be versioned and included in the policy snapshot. It should decide which outstanding obligations are permitted at close, rather than embedding an arbitrary “all distributions must be settled” rule in the gate.

### 4. Make outstanding-distribution treatment explicit

```
package tech.kayys.syirkah.accounting.application.syirkah.settlement;

import tech.kayys.syirkah.accounting.domain.close.CloseValidationContext;
import tech.kayys.syirkah.accounting.domain.syirkah.settlement
        .DistributionSettlementReconciliation;

public interface OutstandingDistributionClosePolicy {

    boolean allowsOutstandingDistributions(
            CloseValidationContext context,
            DistributionSettlementReconciliation reconciliation);
}
```

This policy must use the applicable close-policy version. It should distinguish:

- Valid outstanding distributions that the close policy permits.
- Past-due settlements that require review.
- Amount mismatches or missing settlement evidence that block closing.
- Open correction cases that require further approval.
- Over-settlements requiring an explicit exception or corrective workflow.

Do not let a Boolean policy result erase the reason for an exception. Persist the policy version and the decision evidence alongside the close validation record.

### 5. Add monetary invariants to reconciliation

For a single currency and a consistent as-of point, validate these relationships:

\\[ \text{Expected distribution} = \text{Posted distribution} \\]

\\[ \text{Outstanding amount} = \text{Posted distribution} - \text{Confirmed settlements} \\]

These relationships must be evaluated against the actual obligation and journal records. If a legitimate cancellation, correction, or reversal changes the expected total, the reconciliation must account for the corresponding immutable artifacts rather than simply ignoring the difference.

For multi-currency operations, aggregate and reconcile by currency. Do not add amounts denominated in different currencies without applying the existing accounting FX policy.

### 6. Persist the gate's evidence

Store the reconciliation outcome with the close cycle so an auditor can later establish what was checked.

```
CREATE TABLE syirkah_distribution_reconciliation (
    tenant_id                    VARCHAR(100) NOT NULL,
    ledger_id                    VARCHAR(100) NOT NULL,
    reconciliation_id             VARCHAR(100) NOT NULL,
    close_cycle_id                VARCHAR(100) NOT NULL,
    expected_obligation_count     INTEGER NOT NULL,
    posted_obligation_count       INTEGER NOT NULL,
    settled_obligation_count      INTEGER NOT NULL,
    reconciliation_hash           CHAR(64) NOT NULL,
    close_policy_version          VARCHAR(100) NOT NULL,
    passed                        BOOLEAN NOT NULL,
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                    VARCHAR(200) NOT NULL,

    PRIMARY KEY (tenant_id, ledger_id, reconciliation_id)
);
```

Persist the detailed monetary totals by currency and the error/warning details in normalized child rows or a canonical evidence document. Hash the canonical evidence, not just the Boolean `passed` flag. Use the actual project naming conventions and migration format when integrating this schema.

### 7. Register the gate in the existing pipeline

Place it after distribution posting reconciliation and before the final snapshot-integrity and correction gates, adapting the ordering to the current pipeline:

```
Distribution posting reconciliation
    ↓
Distribution settlement reconciliation
    ↓
Partner position reconciliation
    ↓
Close snapshot integrity
    ↓
Correction reconciliation
    ↓
Policy snapshot integrity
    ↓
Transactional finalization
```

The finalization transaction must revalidate the relevant states under locks. A gate passing earlier does not prevent a concurrent worker from adding a settlement or opening a correction before the period is locked.

### 8. Add these integration tests

| Test                                                   | Expected outcome                                                 |
| ------------------------------------------------------ | ---------------------------------------------------------------- |
| Every expected journal is posted and amounts reconcile | Gate passes                                                      |
| Obligation is posted but its journal is missing        | Gate blocks                                                      |
| Settlement references a different obligation           | Gate blocks                                                      |
| Confirmed settlement totals exceed the posted amount   | Gate blocks or requires an explicitly approved exception         |
| Valid outstanding obligation is permitted by policy    | Gate may pass with recorded outstanding balance                  |
| Outstanding obligation violates close policy           | Gate blocks                                                      |
| Reconciliation is for another tenant or ledger         | Gate blocks                                                      |
| Reconciliation data cannot be retrieved                | Gate fails closed                                                |
| Settlement changes concurrently during finalization    | Transactional revalidation detects the change                    |
| Policy version changes after calculation               | Historical evidence remains tied to the original policy snapshot |

Next implementation target: finalize the complete Syirkah close transaction. This will combine the existing close tasks, calculation evidence, allocation reconciliation, journal posting, settlement reconciliation, policy integrity, and correction checks into one atomic finalization path—without rebuilding the existing accounting framework.

////////////

## Next: Implement correction-driven reclose

A correction to a previously locked Syirkah close must not edit the original close, rewrite posted journals, or erase settled distributions. It should create a new correction workflow that identifies affected artifacts, performs the required compensating actions, recalculates the result, and produces a replacement snapshot.

The core invariant is:

The original close remains immutable. The corrected close becomes active only after all required correction actions complete and the reclose validation passes.

## 1. Define the correction lifecycle

Use an explicit lifecycle rather than a single `correct()` method.

```
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

public enum SyirkahCorrectionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    EXECUTING,
    RECONCILING,
    READY_TO_RECLOSE,
    RECLOSED,
    BLOCKED,
    FAILED,
    CANCELLED
}
```

Expected transitions:

```
DRAFT
  ↓ submit
PENDING_APPROVAL
  ↓ approve
APPROVED
  ↓ execute
EXECUTING
  ↓ actions complete
RECONCILING
  ↓ all reconciliations pass
READY_TO_RECLOSE
  ↓ atomic reclose
RECLOSED
```

`BLOCKED` means a prerequisite is missing or a policy decision is required. `FAILED` means execution encountered an error that needs recovery. Neither state may transition directly to `RECLOSED`; the workflow must resume, reconcile, and pass validation first.

A correction should capture its reason, requester, approver, original close snapshot, original policy snapshot, and the intended corrected calculation context.

## 2. Create a correction aggregate linked to the original close

```
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

import java.time.Instant;
import java.util.Objects;

public record SyirkahCloseCorrection(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String originalCloseCycleId,
        String originalSnapshotId,
        String originalPolicySnapshotId,
        String reasonCode,
        String reason,
        String requestedBy,
        Instant requestedAt,
        SyirkahCorrectionStatus status,
        String approvedBy,
        Instant approvedAt,
        String correctedSnapshotId) {

    public SyirkahCloseCorrection {
        requireText(correctionId, "correctionId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(fiscalPeriodId, "fiscalPeriodId");
        requireText(originalCloseCycleId, "originalCloseCycleId");
        requireText(originalSnapshotId, "originalSnapshotId");
        requireText(originalPolicySnapshotId, "originalPolicySnapshotId");
        requireText(reasonCode, "reasonCode");
        requireText(reason, "reason");
        requireText(requestedBy, "requestedBy");

        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(status, "status");

        if (status == SyirkahCorrectionStatus.RECLOSED
                && (correctedSnapshotId == null
                    || correctedSnapshotId.isBlank())) {
            throw new IllegalArgumentException(
                    "A reclosed correction must reference its corrected snapshot");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

Treat this as a persisted state model, not an invitation to update arbitrary fields. In the actual domain implementation, expose explicit transition methods such as `approve(...)`, `beginExecution()`, `markReadyToReclose(...)`, and `markReclosed(...)`, with validation at each transition. The `RECLOSED` transition should only be invoked by the finalization transaction after reconciliation has passed.

Approval must enforce maker-checker separation: the requester cannot approve their own correction unless a documented authorization policy explicitly permits it.

## 3. Build an immutable correction action plan

A correction may affect multiple dependent artifacts. Record the planned actions before executing them, so retries can resume without repeating completed financial operations.

```
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

public enum SyirkahCorrectionActionType {
    REVERSE_JOURNAL,
    REVERSE_PARTNER_MOVEMENT,
    RECALCULATE_RESULT,
    RECALCULATE_ALLOCATION,
    RECALCULATE_LOSS_RESPONSIBILITY,
    CREATE_CORRECTED_PARTNER_MOVEMENT,
    CREATE_CORRECTED_DISTRIBUTION,
    RECALCULATE_SETTLEMENT,
    REBUILD_PARTNER_POSITION,
    CREATE_CORRECTED_SNAPSHOT,
    REBUILD_REPORTING
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

public enum CorrectionActionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED,
    FAILED,
    SKIPPED
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

public record SyirkahCorrectionAction(
        String actionId,
        String correctionId,
        String tenantId,
        String ledgerId,
        SyirkahCorrectionActionType actionType,
        String targetType,
        String targetId,
        String idempotencyKey,
        CorrectionActionStatus status,
        String resultArtifactId,
        String errorCode) {
}
```

Use a stable idempotency key derived from the correction ID, action type, and target artifact. Enforce uniqueness in the database so concurrent workers cannot execute the same planned action as if it were new.

An action should become `COMPLETED` only after its financial effects and action result have been persisted successfully. If an action creates a journal, record the journal ID. If it creates a corrected movement, record that movement's ID. This provides traceability from the original artifact through its compensating artifact to the corrected result.

## 4. Execute correction actions idempotently

Use a dedicated executor rather than embedding all correction logic in the close processor.

```
package tech.kayys.syirkah.accounting.application.syirkah.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.correction
        .SyirkahCorrectionAction;

public interface SyirkahCorrectionActionExecutor {

    Uni<SyirkahCorrectionAction> execute(
            String correctionId,
            String actionId);
}
```

The executor should:

1. Lock the correction action and verify its state.
2. Verify the correction is approved and belongs to the requested tenant and ledger.
3. Check whether the intended effect already exists by its idempotency key.
4. Execute the action through the relevant existing application service.
5. Persist the resulting artifact reference and mark the action complete in the same transaction where possible.
6. If the action is already completed, verify its result artifact and return it rather than repeating the operation.

For journal reversal, reuse the existing journal reversal transaction. For partner movements, use the existing movement-accounting transaction. For allocation and result recalculation, use the pinned policy and input-evidence pipeline.

Do not wrap a second transaction around a service that already owns the transaction boundary. Use the existing `postInsideTransaction(...)` or equivalent operation when a correction action needs to combine multiple database changes atomically.

If an action crosses a non-transactional external system, use an outbox or durable workflow state and an explicit reconciliation step. A database rollback cannot undo a payment that has already been sent to an external provider.

## 5. Execute actions in dependency order

Corrections should follow the financial dependency graph, not an arbitrary list order.

```
Identify affected artifacts
        ↓
Reverse affected accounting entries
        ↓
Reverse affected partner movements
        ↓
Recalculate financial result
        ↓
Recalculate partner allocation and loss responsibility
        ↓
Create corrected partner movements
        ↓
Create corrected distribution obligations
        ↓
Reconcile settlement consequences
        ↓
Rebuild partner positions
        ↓
Create corrected close snapshot
        ↓
Run all close validation gates
        ↓
Atomically reclose
```

Some branches may be independent. For example, reporting projections can be rebuilt after the corrected financial artifacts are established. Represent dependencies explicitly and detect cycles when building the plan.

Do not assume every correction requires every action. The impact engine should select actions based on affected artifacts and their dependencies. However, skipping an action must be recorded with a reason and must not bypass a mandatory reconciliation.

## 6. Reconcile before allowing reclose

Reuse the existing `SyirkahCorrectionReconciliation` rather than creating a competing reconciliation model. It should verify the relevant result, allocation, movement, partner-position, distribution, settlement, accounting, and snapshot sections.

The transition to `READY_TO_RECLOSE` must require:

- Every required correction action is complete.
- No action is still `IN_PROGRESS`, `BLOCKED`, or `FAILED`.
- Original artifacts and compensating artifacts reconcile.
- Corrected calculations use the approved policy context and pinned input evidence.
- Distribution and settlement consequences are resolved or explicitly permitted by policy.
- The corrected snapshot passes its integrity checks.

A correction aggregate should accept reconciliation evidence, not a caller-supplied Boolean:

```
public void markReadyToReclose(
        SyirkahCorrectionReconciliation reconciliation) {

    if (status != SyirkahCorrectionStatus.RECONCILING) {
        throw new IllegalStateException(
                "Correction must be reconciling before reclose readiness");
    }

    if (reconciliation == null || !reconciliation.readyToReclose()) {
        throw new IllegalStateException(
                "Correction reconciliation has not passed");
    }

    this.status = SyirkahCorrectionStatus.READY_TO_RECLOSE;
}
```

This is a method sketch: adapt it to the existing correction aggregate and its immutability conventions. The persisted reconciliation must be loaded and verified within the finalization transaction; passing an object from an earlier request is not sufficient evidence.

## 7. Atomically replace the active close snapshot

The reclose transaction must lock the correction, fiscal period, original snapshot, and corrected snapshot, then revalidate the required gates.

```
BEGIN TRANSACTION
  Lock correction and original close snapshot
  Lock fiscal period and corrected snapshot
  Verify correction is READY_TO_RECLOSE
  Verify original snapshot is still the expected active snapshot
  Verify corrected snapshot and policy references
  Re-run mandatory close and correction gates
  Mark original snapshot SUPERSEDED
  Mark corrected snapshot ACTIVE
  Mark correction RECLOSED
  Persist audit and outbox records
COMMIT
```

The original locked close must remain available for historical reporting and audit. Do not delete it or overwrite its original calculation evidence.

Keep the active-snapshot uniqueness constraint in place. If any state transition fails, the entire reclose transaction must roll back.

## 8. Persist action idempotency

Use a database constraint to prevent duplicate execution records:

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_correction_action_key
    ON syirkah_correction_action (
        tenant_id,
        ledger_id,
        correction_id,
        idempotency_key
    );
```

Also enforce:

- A unique correction ID within the tenant and ledger.
- A foreign key to the original close snapshot.
- A unique corrected snapshot reference where required.
- An immutable action-result record once completed.
- Audit metadata for every approval and state transition.

Use the existing schema's ID types and migration conventions; these statements illustrate the constraints, not a verified migration against the uploaded framework.

## 9. Test correction recovery and reclose

| Scenario                                      | Expected behavior                                   |
| --------------------------------------------- | --------------------------------------------------- |
| Same correction action retried                | Existing result artifact is reused                  |
| Worker crashes after journal reversal         | Retry reconciles the reversal before proceeding     |
| Correction is not approved                    | Execution is blocked                                |
| One mandatory action fails                    | Correction cannot become ready to reclose           |
| Corrected allocation differs from original    | New allocation and downstream artifacts are created |
| Original distribution was settled             | Settlement history remains immutable                |
| Original snapshot is no longer active         | Reclose detects the conflict                        |
| Two workers reclose the same correction       | Only one finalization commits                       |
| Corrected snapshot fails integrity validation | Original active snapshot remains unchanged          |
| Outbox write fails                            | Reclose transaction rolls back                      |

## What comes next

The correction workflow now has the core lifecycle: identify impacts, plan actions, execute idempotently, reconcile, and reclose transactionally.

The next slice is to connect this lifecycle to Syirkah contract transitions—partner entry, partner exit, capital changes, contract renewal, and full termination—so those operations use the same closing, snapshot, settlement, and audit guarantees instead of modifying an active agreement and its partner balances directly.

////////////

## Next: Integrate Syirkah contract transitions with the close and correction workflows

The next extension is to connect the existing contract-transition model to the accounting safeguards already designed. Partner entry, partner exit, capital changes, contract renewal, and full termination should not directly rewrite an active contract or overwrite partner balances.

The key rule is to freeze the predecessor's financial position, settle obligations separately, and establish any successor agreement from verified closing evidence. The transition process should reuse the existing close snapshots, partner-movement accounting, distribution settlement, and audit infrastructure.

## 1. Define the transition workflow

```
Create transition request
        ↓
Validate active contract and transition authority
        ↓
Close predecessor Syirkah
        ↓
Freeze and verify predecessor snapshot
        ↓
Calculate transition and settlement obligations
        ↓
Post and reconcile required accounting movements
        ↓
 ┌───────────────────────┴──────────────────────┐
 │                                               │
Full termination                           Successor agreement
 │                                               │
Complete liquidation                      Validate successor terms
 │                                               │
Return capital and settle                 Derive opening positions
 │                                               │
Mark predecessor terminated               Activate successor
 └───────────────────────┬──────────────────────┘
                         ↓
               Persist audit and outbox
```

The successor branch applies to transitions that continue the business relationship. Full termination is a terminal path and must not create a successor contract.

## 2. Add an application command for transition execution

Keep the command separate from the domain aggregate. It describes what the caller requests; the application service resolves persisted state and authorizes the operation.

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import java.time.LocalDate;

public record ExecuteSyirkahTransitionCommand(
        String tenantId,
        String ledgerId,
        String transitionId,
        String predecessorContractId,
        String predecessorSnapshotId,
        String successorContractId,
        LocalDate effectiveDate,
        String requestedBy
) {
    public ExecuteSyirkahTransitionCommand {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(transitionId, "transitionId");
        requireText(predecessorContractId, "predecessorContractId");
        requireText(predecessorSnapshotId, "predecessorSnapshotId");
        requireText(requestedBy, "requestedBy");

        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

`successorContractId` is intentionally nullable for `FULL_TERMINATION`. Validate that rule against the persisted transition type rather than trusting the caller.

## 3. Define the transition orchestration boundary

Reuse existing services for close finalization, settlement, opening-position calculation, and contract activation. Avoid duplicating their financial rules inside a new transition service.

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import io.smallrye.mutiny.Uni;

public interface SyirkahTransitionApplicationService {

    Uni<SyirkahTransitionExecutionResult> execute(
            ExecuteSyirkahTransitionCommand command);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

public record SyirkahTransitionExecutionResult(
        String transitionId,
        String predecessorContractId,
        String predecessorSnapshotId,
        String successorContractId,
        String status,
        boolean settlementRequired
) {
}
```

The implementation should coordinate these existing domain capabilities:

- `SyirkahTransition` and its instruction validator.
- The existing close finalization transaction.
- `SyirkahTransitionReconciliationCalculator`.
- `SyirkahOpeningPositionCalculator`.
- The existing partner-movement and accounting transactions.
- The liquidation workflow for full termination.

Use the actual packages and return types already present in the project; the interfaces above define the integration seam rather than a claim that those exact files exist.

## 4. Validate the transition against the frozen predecessor

Before applying any transition instruction, load and validate the predecessor contract and snapshot in the requested tenant and ledger.

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import io.smallrye.mutiny.Uni;

public interface SyirkahTransitionPreconditionService {

    Uni<TransitionPreconditionResult> validate(
            ExecuteSyirkahTransitionCommand command);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import java.util.List;

public record TransitionPreconditionResult(
        boolean valid,
        String predecessorStatus,
        String snapshotStatus,
        List<String> violations
) {
    public TransitionPreconditionResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
```

The precondition service should verify:

1. The predecessor contract belongs to the requested tenant and ledger.
2. The transition references the expected predecessor contract.
3. The predecessor snapshot is persisted, locked, and belongs to that contract and fiscal period.
4. The snapshot's calculation evidence and policy reference are intact.
5. The transition is approved and has not already been completed or cancelled.
6. The effective date is permitted by the contract and transition rules.
7. The requested transition type agrees with whether a successor contract is present.

A snapshot ID supplied by a caller is only a reference. It is not proof that the snapshot is valid or authoritative.

## 5. Derive successor opening positions from immutable evidence

Never derive a successor's opening balances from the current mutable `PartnerAccount` projection. Derive them from the frozen predecessor snapshot and the transition's approved instructions.

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface SyirkahOpeningPositionService {

    Uni<List<PartnerOpeningPosition>> calculate(
            String tenantId,
            String ledgerId,
            String predecessorSnapshotId,
            String transitionId);
}
```

`PartnerOpeningPosition` here represents the existing proposed domain type. Its calculation must distinguish:

- The predecessor's frozen closing position.
- Amounts due to an exiting partner.
- Amounts already settled.
- Amounts still outstanding.
- Capital or other positions carried into the successor agreement.

Do not silently convert an unsettled exit obligation into successor capital. The transition instructions must explicitly determine whether each partner continues, exits, contributes additional capital, or receives a capital return. Those instructions must pass the transition validator and reconciliation checks before successor activation.

The exact accounting and Sharia treatment of individual obligations must come from the applicable approved policy and contract terms; the orchestration layer should not invent that treatment.

## 6. Make successor activation atomic

A successor contract must not become active before its opening positions and predecessor relationship are fully validated. Introduce a transaction boundary dedicated to the final state transition.

```
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import io.smallrye.mutiny.Uni;

public interface SyirkahTransitionCompletionTransaction {

    Uni<SyirkahTransitionExecutionResult> completeSuccessorTransition(
            String tenantId,
            String ledgerId,
            String transitionId,
            String predecessorSnapshotId,
            String successorContractId);

    Uni<SyirkahTransitionExecutionResult> completeTermination(
            String tenantId,
            String ledgerId,
            String transitionId,
            String predecessorSnapshotId,
            String liquidationId);
}
```

These operations should execute the final checks and state changes in one database transaction.

For a successor transition:

1. Lock the transition and predecessor contract.
2. Lock and verify the frozen predecessor snapshot.
3. Verify that required settlement and accounting obligations meet the transition policy.
4. Verify that the successor's opening-position snapshot reconciles.
5. Mark the predecessor terminated or superseded according to the established lifecycle model.
6. Activate the successor contract.
7. Mark the transition `COMPLETED`.
8. Persist audit and outbox records.

For full termination, call the existing liquidation completion workflow. Do not activate a successor contract or mark termination complete while required liquidation and capital-return checks remain outstanding.

If any check fails, roll back the final state changes. Previously posted financial artifacts must be corrected through compensating transactions, not by deleting or editing their history.

## 7. Make retries safe

Add a durable transition execution record or reuse the existing transition instruction/action ledger. Enforce uniqueness for each transition operation so retrying a command cannot create duplicate capital movements, duplicate settlement obligations, or multiple successor activations.

A suitable uniqueness boundary is:

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_transition_operation
    ON syirkah_transition_operation (
        tenant_id,
        ledger_id,
        transition_id,
        operation_key
    );
```

Use the existing database naming conventions and actual schema types when implementing the migration. For concurrent execution, combine idempotency with row locking or optimistic version checks; idempotency keys alone do not prevent conflicting state transitions.

## 8. Add lifecycle tests

| Test                             | Required outcome                                                           |
| -------------------------------- | -------------------------------------------------------------------------- |
| New partner enters               | Predecessor close is frozen before successor opening positions are derived |
| Existing partner exits           | Exit obligation is distinct from successor opening capital                 |
| Capital changes                  | Approved instructions and accounting effects reconcile                     |
| Successor snapshot is invalid    | Successor activation is rejected                                           |
| Settlement remains unresolved    | Transition follows the configured settlement policy                        |
| Full termination                 | Liquidation completes without creating a successor                         |
| Transition command is retried    | Previously completed operations are reused                                 |
| Two workers execute concurrently | Only one valid completion commits                                          |
| Final activation fails           | Pre-transition state remains intact                                        |
| Audit/outbox persistence fails   | Final state transaction rolls back                                         |

## Next extension

The next slice is transition reconciliation and invariant enforcement: reconcile predecessor closing positions, partner entry and exit obligations, successor opening positions, and accounting movements as one transition evidence package. This closes the gap between individually valid accounting operations and a transition that is valid as a whole.

These are implementation designs based on the architecture established so far; they have not been compiled or tested against the existing repository.

///////////////

## Next: Versioned Sharia policy and calculation evidence

The next extension is to make every Syirkah calculation reproducible: the platform should be able to establish which approved policy version, contract terms, business inputs, and calculation engine version produced a historical result.

This builds on the existing `PolicySnapshotIntegrityGate`, close snapshots, correction workflow, and calculation evidence. It should extend the current Islamic accounting capability, not introduce a parallel policy engine or a new set of contract-type enums.

One important distinction: a versioned policy record proves which rules the software applied. It does not, by itself, prove that those rules are an authoritative or universally accepted Sharia ruling. Policy approval and its supporting authority must be explicit.

## 1. Define the policy identity

Use the existing policy model if the repository already has one. If it does not, this is a proposed domain record:

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.time.Instant;
import java.time.LocalDate;

public record SyirkahPolicyVersion(
        String policyId,
        String versionId,
        String policyCode,
        String version,
        String jurisdiction,
        String standardReference,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String documentReference,
        String contentHash,
        String approvedBy,
        String approvalReference,
        Instant approvedAt,
        SyirkahPolicyStatus status) {

    public SyirkahPolicyVersion {
        requireText(policyId, "policyId");
        requireText(versionId, "versionId");
        requireText(policyCode, "policyCode");
        requireText(version, "version");
        requireText(contentHash, "contentHash");
        requireText(approvedBy, "approvedBy");
        requireText(approvalReference, "approvalReference");

        if (effectiveFrom == null || approvedAt == null || status == null) {
            throw new IllegalArgumentException(
                    "Effective date, approval time, and status are required");
        }

        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil cannot precede effectiveFrom");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

public enum SyirkahPolicyStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    SUPERSEDED,
    WITHDRAWN
}
```

Here, `standardReference` and `jurisdiction` identify the relevant policy context; they must not be inferred from the contract type alone. The record is illustrative and should be aligned with existing policy entities, persistence conventions, and the actual approval authority model.

## 2. Pin policy versions to calculation evidence

A calculation should reference an immutable policy version, rather than asking for whichever policy happens to be current when the calculation is replayed.

```
package tech.kayys.syirkah.accounting.domain.syirkah.policy;

import java.time.Instant;
import java.util.List;

public record SyirkahCalculationEvidence(
        String evidenceId,
        String tenantId,
        String ledgerId,
        String contractId,
        String financialResultId,
        String policyVersionId,
        String contractVersion,
        String inputEvidenceHash,
        String calculationEngineVersion,
        String outputHash,
        Instant calculatedAt,
        List<String> sourceArtifactIds) {

    public SyirkahCalculationEvidence {
        requireText(evidenceId, "evidenceId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(financialResultId, "financialResultId");
        requireText(policyVersionId, "policyVersionId");
        requireText(contractVersion, "contractVersion");
        requireText(inputEvidenceHash, "inputEvidenceHash");
        requireText(calculationEngineVersion, "calculationEngineVersion");
        requireText(outputHash, "outputHash");

        if (calculatedAt == null) {
            throw new IllegalArgumentException("calculatedAt is required");
        }

        sourceArtifactIds = sourceArtifactIds == null
                ? List.of()
                : List.copyOf(sourceArtifactIds);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

The evidence should capture enough information to explain and reproduce the result:

- The exact approved policy version and its content hash.
- The effective contract version.
- The business inputs and source artifacts used.
- The calculation engine version.
- The output hash and calculation timestamp.
- The tenant and ledger scope.

The hash is an integrity aid, not a substitute for retaining the underlying input and output artifacts. Store the evidence immutably and use canonical serialization before calculating hashes.

## 3. Resolve policies explicitly

Avoid fallback behavior such as “use the latest policy” when a historical close or correction needs a specific version. The resolver should fail when there is no approved policy for the requested context.

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

import io.smallrye.mutiny.Uni;

public interface SyirkahPolicyResolver {

    Uni<ResolvedSyirkahPolicy> resolveForCalculation(
            String tenantId,
            String ledgerId,
            String contractId,
            String policyVersionId,
            String effectiveDate);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

public record ResolvedSyirkahPolicy(
        String policyId,
        String policyVersionId,
        String policyCode,
        String contentHash,
        String standardReference,
        String jurisdiction) {
}
```

The implementation should verify that:

1. The policy version exists and is approved.
2. The requested tenant and ledger are authorized to use it.
3. The contract and effective date are compatible with the policy's approved scope.
4. The stored content hash matches the approved policy content.
5. The requested policy version is explicit for historical replay and correction.

The date is represented as a string above only to keep the example boundary simple; use `LocalDate` in the production API. Also, avoid treating a generic policy version as sufficient if separate approved policies govern profit allocation, loss responsibility, or settlement corrections.

## 4. Separate policy approval from calculation execution

The calculation service must not be able to approve its own policy. Use an independent policy approval workflow with authenticated actor identity and authorization checks.

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

import io.smallrye.mutiny.Uni;

public interface SyirkahPolicyApprovalService {

    Uni<ApprovedSyirkahPolicy> approve(
            ApproveSyirkahPolicyCommand command);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.policy;

public record ApproveSyirkahPolicyCommand(
        String tenantId,
        String policyVersionId,
        String approverId,
        String approvalReference,
        String decisionReason) {
}
```

Approval should verify the approver's authorization, ensure the policy content has not changed since review, and persist the approval decision in an audit record. The approval reference should identify the actual supporting document or decision; it must not be generated merely to make a policy appear approved.

For policies that require review by a designated Sharia governance authority, encode that requirement in the authorization and approval workflow rather than assuming every accounting administrator has that authority.

## 5. Add a policy integrity gate to close and correction

Extend the existing `PolicySnapshotIntegrityGate` instead of creating a second close-validation pipeline.

The gate should verify:

- The close snapshot references an immutable policy version.
- The policy was approved and applicable to the calculation.
- The calculation evidence references the same policy version and content hash.
- The contract version and input evidence match the calculation record.
- Every required result and allocation has calculation evidence.
- A correction's policy choice is explicit: either replay the original policy version or use a separately approved replacement policy where the correction process permits that change.

A correction must distinguish two different events:

- Corrected business facts: the original policy remains pinned, but the inputs change.
- Approved policy change: the correction explicitly identifies a different policy version and records why it applies.

Do not silently substitute a newer policy during recalculation. Historical reproducibility and policy-change approval are separate requirements.

## 6. Add persistence constraints

Adapt these constraints to the actual schema and database dialect.

```
CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_policy_version
    ON syirkah_policy_version (
        policy_id,
        version
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_syirkah_calculation_evidence
    ON syirkah_calculation_evidence (
        tenant_id,
        ledger_id,
        evidence_id
    );
```

For approved policy content, use an immutable versioning strategy: create a new version instead of editing the content of an already approved version. Enforce this in the application and, where appropriate, with database permissions or triggers.

## 7. Add the critical tests

| Scenario                                              | Expected behavior                                                 |
| ----------------------------------------------------- | ----------------------------------------------------------------- |
| No approved policy exists                             | Calculation is blocked                                            |
| Policy content changes after approval                 | Integrity validation fails                                        |
| Historical close is replayed                          | The original policy version is reused                             |
| Correction changes business inputs                    | New calculation evidence is produced                              |
| Correction changes policy version                     | Explicit approval and policy-change evidence are required         |
| Policy approval is attempted by an unauthorized actor | Approval is rejected                                              |
| Policy version is outside its effective period        | Resolution fails unless an explicit, authorized exception applies |
| Calculation engine version changes                    | New evidence identifies the engine version                        |
| Close snapshot references mismatched evidence         | Finalization is rejected                                          |

These are implementation requirements; the examples have not been compiled or tested against the repository.

## Next slice: Wire policy versioning into the actual calculation pipeline

The next implementation step is to connect the pinned policy to `SyirkahResultCalculator`, `SyirkahProfitSharingEngine`, and `LossAllocationPolicyResolver`. Each calculation should receive an explicit policy context and return both the financial result and its calculation evidence. That will make it possible to trace a final distribution or loss allocation back to the exact contract, inputs, policy, and engine version that produced it.

///////////


## Next: Wire pinned policy versions into the Syirkah calculation pipeline

The goal is to make the existing calculation components consume an explicit, approved policy context and produce both a financial result and its evidence. This connects policy versioning to the calculations that drive profit sharing, loss allocation, and distributions—without replacing the existing accounting implementation.

The intended flow is:

```
Close request
    ↓
Resolve effective contract version
    ↓
Resolve approved policy version(s)
    ↓
Load and validate financial input evidence
    ↓
Calculate financial result
    ↓
Allocate profit or loss under the resolved policy
    ↓
Persist result + calculation evidence
    ↓
Reconcile and continue close validation
```

A historical close or correction must not silently use whichever policy happens to be current.

## 1. Introduce an immutable calculation context

The context carries resolved references into the calculation engine. It should contain identifiers and validated policy objects, not just policy names supplied by a caller.

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import java.time.LocalDate;

public record SyirkahCalculationContext(
        String tenantId,
        String ledgerId,
        String contractId,
        String contractVersion,
        LocalDate effectiveDate,
        String resultPolicyVersionId,
        String allocationPolicyVersionId,
        String lossPolicyVersionId,
        String inputEvidenceId,
        String calculationEngineVersion) {

    public SyirkahCalculationContext {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(contractVersion, "contractVersion");
        requireText(resultPolicyVersionId, "resultPolicyVersionId");
        requireText(allocationPolicyVersionId, "allocationPolicyVersionId");
        requireText(inputEvidenceId, "inputEvidenceId");
        requireText(calculationEngineVersion, "calculationEngineVersion");

        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

The loss policy reference can be optional only where the resolved result does not require loss allocation. Enforce that condition after determining the financial result; do not permit a missing loss policy to default silently to a generic rule.

This is a proposed integration type. If the existing modules already have a calculation context or policy snapshot, extend that type rather than creating a duplicate.

## 2. Return the calculation and its evidence together

Avoid persisting a result first and attempting to reconstruct its provenance later. The application-level calculation should return the result, allocations, and evidence references as one unit.

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import java.util.List;

public record SyirkahCalculationOutcome(
        String financialResultId,
        String allocationId,
        String resultPolicyVersionId,
        String allocationPolicyVersionId,
        String lossPolicyVersionId,
        String inputEvidenceId,
        String calculationEngineVersion,
        String outputHash,
        List<String> allocationArtifactIds) {

    public SyirkahCalculationOutcome {
        allocationArtifactIds = allocationArtifactIds == null
                ? List.of()
                : List.copyOf(allocationArtifactIds);
    }
}
```

This outcome is the application-level result, not the financial result aggregate itself. Preserve the existing domain types for `FinancialResult`, `ProfitSharingResult`, and the allocation records. Add the evidence references to their persisted calculation record or link them through an immutable evidence entity.

## 3. Make policy resolution explicit

Resolve policies before running any financial calculation. Keep the resolver responsible for verifying approval, effective dates, scope, and content integrity.

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationContextFactory {

    Uni<SyirkahCalculationContext> create(
            SyirkahCalculationRequest request);
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import java.time.LocalDate;

public record SyirkahCalculationRequest(
        String tenantId,
        String ledgerId,
        String contractId,
        LocalDate effectiveDate,
        String requestedResultPolicyVersionId,
        String requestedAllocationPolicyVersionId,
        String requestedLossPolicyVersionId,
        String inputEvidenceId,
        boolean historicalReplay) {
}
```

The context factory should use the effective contract version for the requested date. For a historical replay, require the policy references recorded in the original evidence. For a correction, require the correction workflow to determine whether the original policy is retained or an explicitly approved replacement policy applies.

Do not let the factory choose a newer policy just because the caller omitted a version ID.

## 4. Adapt the existing calculation components

Keep `SyirkahResultCalculator`, `SyirkahProfitSharingEngine`, and `LossAllocationPolicyResolver` as the domain calculation components. Add adapters or overloads so the application passes the resolved context explicitly.

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationPipeline {

    Uni<SyirkahCalculationOutcome> calculate(
            SyirkahCalculationRequest request);
}
```

The implementation follows this sequence:

```
package tech.kayys.syirkah.accounting.application.syirkah.calculation;

import io.smallrye.mutiny.Uni;

public class DefaultSyirkahCalculationPipeline
        implements SyirkahCalculationPipeline {

    private final SyirkahCalculationContextFactory contextFactory;
    private final SyirkahCalculationInputProvider inputProvider;
    private final SyirkahCalculationEngine calculationEngine;
    private final SyirkahCalculationEvidenceService evidenceService;

    public DefaultSyirkahCalculationPipeline(
            SyirkahCalculationContextFactory contextFactory,
            SyirkahCalculationInputProvider inputProvider,
            SyirkahCalculationEngine calculationEngine,
            SyirkahCalculationEvidenceService evidenceService) {
        this.contextFactory = contextFactory;
        this.inputProvider = inputProvider;
        this.calculationEngine = calculationEngine;
        this.evidenceService = evidenceService;
    }

    @Override
    public Uni<SyirkahCalculationOutcome> calculate(
            SyirkahCalculationRequest request) {

        return contextFactory.create(request)
                .flatMap(context ->
                        inputProvider.loadValidatedInputs(context)
                                .flatMap(inputs ->
                                        calculationEngine.calculate(context, inputs)
                                                .flatMap(result ->
                                                        evidenceService.persist(
                                                                context,
                                                                inputs,
                                                                result))));
    }
}
```

The supporting interfaces in this sketch are integration seams to implement against existing domain APIs:

- `SyirkahCalculationInputProvider` loads and validates the source evidence.
- `SyirkahCalculationEngine` adapts the existing result, allocation, and loss-policy components.
- `SyirkahCalculationEvidenceService` persists the calculation output and immutable provenance.

The code illustrates the orchestration order; the project-specific types and signatures need to match the actual repository.

### Important domain rules

- Determine whether the result is profit, loss, or break-even before selecting downstream allocation logic.
- For profit, apply the effective contractual profit-sharing ratios and the resolved allocation policy.
- For loss, require the applicable loss-allocation policy and relevant loss-cause evidence.
- For break-even, do not manufacture a profit allocation or loss allocation.
- Reject unresolved policy conflicts rather than silently selecting a default.

The application pipeline should orchestrate these decisions; it should not embed its own alternative profit-sharing or loss-allocation formulas.

## 5. Persist result and evidence consistently

Calculation evidence should be stored with the financial result and allocation records. If they share a database, persist the records in one transaction:

```
BEGIN
  Verify pinned policy versions remain valid
  Persist financial result
  Persist profit/loss allocation artifacts
  Persist calculation evidence
  Persist audit and outbox records
COMMIT
```

If calculations are long-running or use external data sources, first persist a durable calculation run and its input references. Complete the run only after all required output artifacts and evidence are persisted. A partially persisted calculation must not be eligible for close finalization.

Store the original input evidence, not just its hash. The hash detects changes; the retained evidence makes the calculation explainable and replayable.

## 6. Extend close validation

The existing close validation pipeline should verify that:

- Every required financial result has calculation evidence.
- Result and allocation policy IDs match the evidence.
- Policy content hashes match the approved versions.
- Contract versions and input references are consistent.
- Loss calculations have the required loss-policy and loss-cause evidence.
- The close snapshot references the exact evidence used for its calculations.

For correction-driven reclose, preserve the original evidence and create new evidence for the corrected calculation. Never overwrite the original calculation's output hash or policy references.

## 7. Add tests for reproducibility

| Scenario                                       | Expected result                                             |
| ---------------------------------------------- | ----------------------------------------------------------- |
| Approved policy versions are supplied          | Calculation uses those exact versions                       |
| Policy version is missing                      | Calculation fails rather than selecting a default           |
| Historical replay is requested                 | Original policy and input evidence are reused               |
| Financial input changes                        | New calculation evidence is produced                        |
| Loss result has no applicable loss policy      | Calculation is blocked                                      |
| Policy content hash mismatches                 | Calculation or close validation fails                       |
| Evidence persistence fails                     | No completed calculation is exposed as final                |
| Correction uses an approved replacement policy | Original and corrected evidence remain separately traceable |
| Break-even result                              | No profit or loss allocation is manufactured                |

These are proposed tests, not tests executed against the codebase.

## Next: Connect calculation evidence to accounting journal provenance

The next slice is to link each calculated result and partner allocation to the journal entries and partner movements generated from it. That completes the traceability chain:

`Contract version → Policy version → Input evidence → Financial result → Partner allocation → Partner movement → Journal entry → Close snapshot`

This connection should use the existing journal source identifiers and movement-accounting transaction, so the Syirkah capability remains an extension of the current accounting platform rather than a separate accounting system.


///////////////

## Next: Connect Syirkah calculation evidence to journal provenance

This completes the financial traceability chain from the approved contract and policy through to posted accounting entries. The key is to extend the existing journal source metadata and partner-movement accounting flow, rather than introduce a separate Syirkah ledger.

The desired chain is:

`Contract version → Policy version → Input evidence → Financial result → Allocation → Partner movement → Journal entry → Close snapshot`

Each link should be persisted and queryable. A journal entry's description alone is not sufficient provenance.

## 1. Define a shared provenance reference

Use a reference object to identify the calculation and allocation that produced an accounting artifact.

```
package tech.kayys.syirkah.accounting.domain.syirkah.provenance;

public record SyirkahAccountingProvenance(
        String calculationEvidenceId,
        String financialResultId,
        String allocationId,
        String contractId,
        String contractVersion,
        String policyVersionId,
        String inputEvidenceId,
        String closeCycleId,
        String correctionId) {

    public SyirkahAccountingProvenance {
        requireText(calculationEvidenceId, "calculationEvidenceId");
        requireText(financialResultId, "financialResultId");
        requireText(contractId, "contractId");
        requireText(contractVersion, "contractVersion");
        requireText(policyVersionId, "policyVersionId");
        requireText(inputEvidenceId, "inputEvidenceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

`allocationId`, `closeCycleId`, and `correctionId` may be absent when the artifact is created before those stages. Validate them when the relevant workflow stage requires them. For calculations with separate result, allocation, and loss policies, record all applicable policy references in the persisted evidence rather than relying on a single generic policy ID.

## 2. Extend the journal posting command compatibly

The existing `PostJournalEntryCommand` already carries `sourceType` and `sourceId`. Preserve those fields for idempotency and add a structured provenance reference.

Avoid adding a constructor-breaking record field without checking existing call sites. One compatible option is a separate posting request that wraps the existing command:

```
package tech.kayys.syirkah.accounting.application.syirkah.provenance;

import tech.kayys.syirkah.accounting.application.api.command
        .PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.domain.syirkah.provenance
        .SyirkahAccountingProvenance;

public record SyirkahJournalPostingRequest(
        PostJournalEntryCommand journalCommand,
        SyirkahAccountingProvenance provenance) {

    public SyirkahJournalPostingRequest {
        if (journalCommand == null) {
            throw new IllegalArgumentException(
                    "journalCommand is required");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(
                    "provenance is required");
        }
    }
}
```

The Syirkah application service should construct this request from persisted calculation evidence. It should not trust arbitrary provenance IDs supplied by an external API.

Keep `sourceType` and `sourceId` stable. For example, profit allocation journals can use a source type such as `SYIRKAH_PROFIT_ALLOCATION` and the allocation ID as the source ID. Corrections must use distinct, stable source identities for reversals and replacement entries.

## 3. Persist provenance beside the journal

Prefer a dedicated provenance table when the existing journal schema should remain stable or provenance will evolve independently.

```
CREATE TABLE IF NOT EXISTS syirkah_journal_provenance (
    tenant_id                   VARCHAR(100) NOT NULL,
    ledger_id                   VARCHAR(100) NOT NULL,
    journal_entry_id            VARCHAR(100) NOT NULL,
    calculation_evidence_id     VARCHAR(100) NOT NULL,
    financial_result_id         VARCHAR(100) NOT NULL,
    allocation_id               VARCHAR(100),
    contract_id                 VARCHAR(100) NOT NULL,
    contract_version            VARCHAR(100) NOT NULL,
    policy_version_id           VARCHAR(100) NOT NULL,
    input_evidence_id           VARCHAR(100) NOT NULL,
    close_cycle_id              VARCHAR(100),
    correction_id               VARCHAR(100),
    created_at                  TIMESTAMP NOT NULL,

    PRIMARY KEY (
        tenant_id,
        ledger_id,
        journal_entry_id
    )
);
```

Adapt identifier types and timestamp syntax to the actual database schema. Add foreign keys where compatible with the repository's existing table design.

Persist this record in the same database transaction that posts the journal entry. Otherwise, a journal might commit without provenance, or provenance might refer to a journal that failed to post.

## 4. Connect posting to provenance persistence

Build on the existing `JournalPostingOperations.postInsideTransaction(...)` transaction-aware operation.

```
package tech.kayys.syirkah.accounting.application.syirkah.provenance;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.accounting.JournalEntryId;

public interface SyirkahJournalProvenanceService {

    Uni<Void> attach(
            String tenantId,
            String ledgerId,
            JournalEntryId journalEntryId,
            SyirkahJournalPostingRequest request);
}
```

The package for `JournalEntryId` should match its actual location in the project; the import above is illustrative.

The application-level posting flow becomes:

```
BEGIN TRANSACTION
  Load persisted calculation evidence
  Verify allocation and contract references
  Check journal source idempotency
  Post journal using existing posting operation
  Persist journal provenance
  Persist audit and outbox records
COMMIT
```

If the journal source already exists, do not blindly return success. Verify that the existing journal and its provenance match the intended source and calculation. A mismatch should raise a conflict requiring investigation.

## 5. Link partner movements to journal provenance

A partner movement and its accounting journal represent related but different facts:

- The partner movement records the change in a partner's financial position.
- The journal entry records the corresponding accounting effect.
- The calculation evidence explains why that amount was determined.

Reuse the existing `PartnerMovementAccountingTransaction` so movement posting and journal posting remain atomic. Add or persist the calculation evidence reference on the movement, or in a companion provenance table if the movement is intended to remain a stable domain record.

The transaction must enforce these invariants:

1. A movement cannot become `POSTED` before its journal entry is successfully persisted.
2. A posted movement cannot have its amount or provenance silently overwritten.
3. A correction creates compensating and corrected movements with explicit lineage.
4. The journal provenance and movement provenance must refer to compatible calculation and allocation evidence.
5. Retrying the same posting operation must not create duplicate journals or movements.

## 6. Build a provenance reconciliation service

```
package tech.kayys.syirkah.accounting.application.syirkah.provenance;

import io.smallrye.mutiny.Uni;

public interface SyirkahAccountingProvenanceReconciliation {

    Uni<ProvenanceReconciliationResult> reconcile(
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String correctionId);
}
```

The reconciliation implementation should compare persisted records, not reconstruct the lineage from descriptions or logs.

| Check                     | Required condition                                                    |
| ------------------------- | --------------------------------------------------------------------- |
| Calculation to allocation | Allocation references the calculation evidence that produced it       |
| Allocation to movement    | Movement amount, currency, partner, and allocation reference agree    |
| Movement to journal       | Posted movement references the correct accounting journal             |
| Journal to provenance     | Provenance references the expected contract, policy, and calculation  |
| Correction lineage        | Reversals and replacement artifacts identify their original artifacts |
| Close snapshot            | Snapshot references the evidence and artifacts included in the close  |

Do not compare all amounts across the chain using a single equality rule: journal entries can contain multiple debit and credit lines, while one allocation can produce several movements or journals. Reconcile using explicit source relationships, currency-aware amounts, and the accounting policy's defined aggregation rules.

Include this service as a close-validation gate alongside the existing journal, partner-position, and snapshot integrity gates.

## 7. Test failure and recovery cases

- Journal posting fails: neither provenance nor a posted movement is committed.
- Provenance persistence fails: the journal posting transaction rolls back.
- A retry encounters an existing journal: the existing source and provenance are verified.
- Movement and journal amounts disagree: reconciliation fails.
- A correction reverses a journal: the original journal remains available with its reversal relationship.
- A historical policy is superseded: the old journal retains its original policy provenance.
- A close snapshot references missing evidence: close finalization is rejected.

These are implementation specifications, not tests already run against the repository.

## Next extension: Build a Syirkah audit and traceability query

With provenance persisted, the next step is a read-only query service that can take a partner allocation, journal entry, close snapshot, or correction ID and traverse the full lineage in either direction. It should support audit investigations and correction impact analysis without exposing mutable accounting internals or bypassing tenant and ledger isolation.

/////////////////

## Next: Syirkah audit and traceability query

Now that calculation evidence, partner movements, journal entries, close snapshots, and corrections have explicit provenance links, add a read-only traceability service that can traverse the entire chain without changing accounting state.

It should support two directions:

- Forward trace: from a contract, calculation, or partner allocation to its resulting movements, journals, and close snapshot.
- Reverse trace: from a journal entry or correction back to the source calculation, policy version, contract version, and affected close.

This service should reuse the provenance records and repositories already introduced. It must not recalculate historical results just to answer an audit query.

## 1. Define the trace query

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public enum SyirkahTraceRootType {
    CONTRACT,
    CALCULATION_EVIDENCE,
    FINANCIAL_RESULT,
    PARTNER_ALLOCATION,
    PARTNER_MOVEMENT,
    JOURNAL_ENTRY,
    CLOSE_SNAPSHOT,
    CORRECTION
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public record SyirkahTraceQuery(
        String tenantId,
        String ledgerId,
        SyirkahTraceRootType rootType,
        String rootId,
        int maxDepth,
        int maxNodes) {

    public SyirkahTraceQuery {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(rootId, "rootId");

        if (rootType == null) {
            throw new IllegalArgumentException("rootType is required");
        }
        if (maxDepth < 1 || maxDepth > 32) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and 32");
        }
        if (maxNodes < 1 || maxNodes > 5000) {
            throw new IllegalArgumentException(
                    "maxNodes must be between 1 and 5000");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

The bounds protect the service from unbounded graph traversal. They should be configurable within strict server-side limits, not controlled without restriction by API callers.

## 2. Represent the trace as a graph

Use a small, read-only graph model. Each node identifies a persisted artifact; each edge describes the documented relationship between artifacts.

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public enum SyirkahTraceNodeType {
    CONTRACT,
    POLICY_VERSION,
    INPUT_EVIDENCE,
    CALCULATION_EVIDENCE,
    FINANCIAL_RESULT,
    PARTNER_ALLOCATION,
    PARTNER_MOVEMENT,
    JOURNAL_ENTRY,
    DISTRIBUTION_OBLIGATION,
    SETTLEMENT,
    CLOSE_SNAPSHOT,
    CORRECTION
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public enum SyirkahTraceRelationship {
    USES_POLICY,
    USES_INPUT,
    PRODUCES_RESULT,
    PRODUCES_ALLOCATION,
    RECORDS_MOVEMENT,
    ACCOUNTED_BY,
    REFERENCES_SETTLEMENT,
    INCLUDED_IN_SNAPSHOT,
    CORRECTS,
    REVERSES,
    SUPERSEDES
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public record SyirkahTraceNode(
        SyirkahTraceNodeType type,
        String artifactId,
        String status,
        String version) {
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public record SyirkahTraceEdge(
        String fromArtifactId,
        String toArtifactId,
        SyirkahTraceRelationship relationship) {
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

import java.util.List;

public record SyirkahTraceResult(
        String tenantId,
        String ledgerId,
        SyirkahTraceNode root,
        List<SyirkahTraceNode> nodes,
        List<SyirkahTraceEdge> edges,
        boolean truncated) {

    public SyirkahTraceResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
```

Use composite identity `(tenantId, ledgerId, nodeType, artifactId)` internally. An artifact ID alone may not be globally unique across different artifact types or tenant scopes.

The graph should contain only relationships backed by persisted records. If provenance is missing, report the missing link explicitly; do not infer a relationship merely because two records have matching amounts or descriptions.

## 3. Define a repository-backed trace service

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahTraceService {

    Uni<SyirkahTraceResult> trace(SyirkahTraceQuery query);
}
```

Implement the service using dedicated repository queries for each supported relationship. For example:

- Calculation evidence → contract version and policy version.
- Calculation evidence → financial result and allocation.
- Allocation → partner movements.
- Movement → journal entry.
- Journal entry → reversal or correction.
- Close snapshot → included calculation evidence and financial artifacts.
- Settlement → distribution obligation.

Prefer batched relationship queries to one query per graph node. The service should enforce the tenant and ledger predicates at the repository boundary, not just filter the final response.

## 4. Traverse relationships safely

The traversal must handle cycles—for example, an original journal linked to a reversal whose correction also references the original artifact. Use a visited set and enforce the depth and node limits from the query.

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface SyirkahTraceRelationshipReader {

    Uni<List<SyirkahTraceEdge>> findOutgoing(
            String tenantId,
            String ledgerId,
            SyirkahTraceNode node);

    Uni<List<SyirkahTraceEdge>> findIncoming(
            String tenantId,
            String ledgerId,
            SyirkahTraceNode node);

    Uni<List<SyirkahTraceNode>> loadRelatedNodes(
            String tenantId,
            String ledgerId,
            List<SyirkahTraceEdge> edges);
}
```

This interface deliberately separates relationship discovery from traversal. Implement it with the existing provenance, movement, journal, correction, settlement, and snapshot repositories.

The traversal algorithm should:

1. Load and authorize the requested root artifact.
2. Add it to the visited set.
3. Read incoming and outgoing relationships as required by the trace mode.
4. Add unseen nodes and edges.
5. Stop at `maxDepth` or `maxNodes`, setting `truncated = true` when more data remains.
6. Return a stable, deduplicated result.

If the caller requests a forward-only or reverse-only trace, expose that as an explicit query option rather than always traversing both directions. For audit investigations, bidirectional tracing is often useful, but bounded traversal remains essential.

## 5. Make missing provenance visible

A trace should distinguish “no relationship exists” from “the expected relationship cannot be verified.”

For example, a posted partner movement may have no journal reference because the data is incomplete. That is not the same as a movement that legitimately has no accounting entry yet.

Add a structured finding model:

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public enum SyirkahTraceFindingType {
    MISSING_PROVENANCE,
    MISSING_ARTIFACT,
    TENANT_OR_LEDGER_MISMATCH,
    BROKEN_REVERSAL_LINK,
    BROKEN_SNAPSHOT_REFERENCE,
    INCONSISTENT_STATUS
}
```

```
package tech.kayys.syirkah.accounting.application.syirkah.audit;

public record SyirkahTraceFinding(
        SyirkahTraceFindingType type,
        String artifactId,
        String relationship,
        String description) {
}
```

Include these findings in the trace result, or return a companion audit-diagnostics result. Do not silently omit broken links, and do not change financial records from this read-only service.

## 6. Enforce tenant isolation and audit access

Treat traceability as a privileged read operation because it can expose contract terms, partner balances, accounting entries, and correction history.

- Resolve tenant and ledger access from the authenticated security context.
- Verify access to the root artifact before traversing any relationships.
- Apply tenant and ledger filters to every repository query.
- Avoid exposing internal database IDs or unrelated partner information to callers who lack access.
- Record the requester, query root, query time, and access decision in the audit log.
- Keep the trace service read-only; corrections must go through the existing correction workflow.

For large audit investigations, consider an asynchronous export built from a consistent evidence boundary. Do not return an incomplete trace as if it were a complete audit record when traversal was truncated.

## 7. Add focused tests

| Scenario                                      | Expected behavior                                                 |
| --------------------------------------------- | ----------------------------------------------------------------- |
| Trace from allocation                         | Returns linked movements, journals, and applicable close snapshot |
| Trace from journal reversal                   | Shows both original and reversal lineage                          |
| Trace from correction                         | Shows affected artifacts and replacement artifacts                |
| Provenance link is missing                    | Returns an explicit finding                                       |
| Root belongs to another tenant                | Access is denied without leaking artifact details                 |
| Relationship points to another ledger         | Link is rejected and reported                                     |
| Graph contains a cycle                        | Traversal terminates without duplicate nodes                      |
| Node limit is reached                         | Result is marked truncated                                        |
| Snapshot is superseded                        | Historical snapshot remains traceable                             |
| Caller requests trace as an unauthorized user | Access is denied and audited                                      |

## Next extension: Add audit-grade evidence export

The trace service gives us the relationships; the next step is to export them as a reproducible audit package containing the trace graph, policy and contract versions, calculation evidence, journal and movement references, reconciliation outcomes, and correction lineage.

The export should preserve source records and hashes, include the generation timestamp and requester, and clearly identify missing evidence. It should not claim that a hash proves the substantive correctness of a Sharia ruling or accounting treatment.

These are proposed implementation slices; they have not been compiled or run against the repository.

/////////////


The next implementation slice is the Syirkah audit-evidence export pipeline. It builds on the trace graph, but adds a reproducible, tenant-scoped evidence package with explicit completeness checks, immutable export metadata, and a durable lifecycle for large exports.

The key rule is that an export must distinguish evidence that exists, evidence that is missing, and evidence that could not be verified. A hash can help detect content changes; it does not prove that a calculation, accounting treatment, or Sharia interpretation is substantively correct.

## 1. Export command and lifecycle

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SyirkahAuditEvidenceExportCommand(
        String tenantId,
        String ledgerId,
        String rootType,
        String rootId,
        String requestedBy,
        String reason,
        String requestedFormat,
        Instant expiresAt,
        UUID requestId
) {
    public SyirkahAuditEvidenceExportCommand {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(rootType, "rootType");
        requireText(rootId, "rootId");
        requireText(requestedBy, "requestedBy");
        requireText(requestedFormat, "requestedFormat");

        if (!"JSON".equals(requestedFormat)
                && !"ZIP".equals(requestedFormat)) {
            throw new IllegalArgumentException(
                    "requestedFormat must be JSON or ZIP");
        }

        Objects.requireNonNull(requestId, "requestId");

        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "expiresAt must be in the future");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

Use the existing `SyirkahTraceRootType` enum instead of a free-form `String` if that enum is accessible from the application module. Likewise, use the platform's established identifier types where available.

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditExportStatus {
    REQUESTED,
    COLLECTING,
    VALIDATING,
    READY,
    FAILED,
    EXPIRED
}
```

The export is a durable application resource, not just a method returning bytes.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditEvidenceExportService {

    Uni<SyirkahAuditExportReceipt> request(
            SyirkahAuditEvidenceExportCommand command);

    Uni<SyirkahAuditExportStatusView> getStatus(
            String tenantId,
            String exportId,
            String requestedBy);

    Uni<SyirkahAuditExportDownload> getDownload(
            String tenantId,
            String exportId,
            String requestedBy);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahAuditExportReceipt(
        String exportId,
        SyirkahAuditExportStatus status,
        Instant requestedAt
) {}

public record SyirkahAuditExportStatusView(
        String exportId,
        SyirkahAuditExportStatus status,
        Instant requestedAt,
        Instant completedAt,
        String failureCode,
        boolean evidenceComplete,
        boolean traceTruncated
) {}

public record SyirkahAuditExportDownload(
        String exportId,
        String mediaType,
        String fileName,
        byte[] content,
        String manifestHash
) {
    public SyirkahAuditExportDownload {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
```

`getDownload` must return data only when the export is `READY`, has not expired, and the caller is authorized for the specific tenant and export. Avoid exposing internal failure details that could reveal cross-tenant records.

## 2. Evidence package and manifest

The package needs to retain the trace result and structured evidence references. Do not flatten the trace into a list of IDs and lose relationships such as `REVERSES`, `SUPERSEDES`, or `ACCOUNTED_BY`.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyirkahAuditEvidencePackage(
        SyirkahAuditExportManifest manifest,
        SyirkahTraceResult trace,
        List<SyirkahAuditArtifact> artifacts,
        List<SyirkahAuditEvidenceFinding> findings,
        List<SyirkahAuditReconciliationEvidence> reconciliations
) {
    public SyirkahAuditEvidencePackage {
        artifacts = List.copyOf(artifacts);
        findings = List.copyOf(findings);
        reconciliations = List.copyOf(reconciliations);
    }
}
```

The following references the previously defined `SyirkahTraceResult`. Adjust the import to its actual package in your repository.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Map;

public record SyirkahAuditExportManifest(
        int schemaVersion,
        String exportId,
        String tenantId,
        String ledgerId,
        String rootType,
        String rootId,
        Instant generatedAt,
        String requestedBy,
        String requestedFormat,
        String consistencyBoundaryId,
        boolean traceTruncated,
        boolean evidenceComplete,
        Map<String, Integer> recordCounts,
        String canonicalizationVersion,
        String hashAlgorithm,
        String packageHash
) {
    public SyirkahAuditExportManifest {
        recordCounts = Map.copyOf(recordCounts);
    }
}
```

The manifest should include the hash algorithm and canonicalization version because a hash is only reproducible when the bytes being hashed are defined consistently. Do not hash arbitrary JSON serialization whose field ordering, numeric representation, or timestamp formatting can change.

Each artifact should describe whether its content is embedded or retained as a reference:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Map;

public record SyirkahAuditArtifact(
        String artifactType,
        String artifactId,
        String sourceType,
        String sourceId,
        String sourceVersion,
        String contentHash,
        String contentHashAlgorithm,
        String contentLocation,
        boolean contentIncluded,
        boolean sourceVerified,
        Instant sourceUpdatedAt,
        Map<String, String> relationships
) {
    public SyirkahAuditArtifact {
        relationships = Map.copyOf(relationships);
    }
}
```

`contentLocation` must be a package-relative path or a stable authorized reference, never an arbitrary filesystem path. `sourceVersion` should identify an immutable version where the source system supports it. If only the current mutable record is available, report that limitation rather than pretending it is a historical snapshot.

## 3. Collect evidence through a consistent boundary

The collector should reuse the existing trace service and relationship reader. Add domain-specific evidence providers for contracts, policy snapshots, calculation inputs, journal records, settlements, snapshots, and correction lineage. The collector must not independently query each source at arbitrary times and label the result a consistent package.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditEvidenceCollector {

    Uni<SyirkahAuditEvidencePackage> collect(
            SyirkahAuditCollectionContext context);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahAuditCollectionContext(
        String exportId,
        String tenantId,
        String ledgerId,
        String rootType,
        String rootId,
        String requestedBy,
        String consistencyBoundaryId,
        Instant boundaryEstablishedAt,
        int maxTraceDepth,
        int maxTraceNodes
) {}
```

Define the consistency boundary as a platform abstraction. Depending on the storage technology, it can be a database snapshot, a stable version token, or an application-level evidence cutoff backed by immutable records. A timestamp by itself is not a transactional snapshot.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditConsistencyBoundary {

    Uni<SyirkahAuditReadBoundary> open(
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId);

    Uni<Void> close(SyirkahAuditReadBoundary boundary);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahAuditReadBoundary(
        String boundaryId,
        String tenantId,
        String ledgerId,
        String snapshotToken,
        boolean guaranteesConsistentReads
) {}
```

All evidence providers must read against the same boundary. If the storage implementation cannot guarantee consistent reads, the export must either fail validation or be marked explicitly as incomplete/inconsistent. Do not silently treat that case as a complete audit package.

### Evidence finding model

Reuse the existing `SyirkahTraceFindingType` where applicable, adding export-specific findings only when the trace findings do not cover the issue.

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditFindingCode {
    MISSING_CONTRACT_VERSION,
    MISSING_POLICY_VERSION,
    MISSING_INPUT_EVIDENCE,
    MISSING_CALCULATION_EVIDENCE,
    MISSING_JOURNAL_REFERENCE,
    MISSING_RECONCILIATION,
    MISSING_CORRECTION_LINEAGE,
    SOURCE_VERSION_UNAVAILABLE,
    SOURCE_HASH_MISMATCH,
    INCONSISTENT_READ_BOUNDARY,
    UNVERIFIED_SOURCE,
    TRACE_TRUNCATED
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahAuditEvidenceFinding(
        SyirkahAuditFindingCode code,
        String artifactType,
        String artifactId,
        String message,
        boolean blocksCompleteStatus
) {}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Map;

public record SyirkahAuditReconciliationEvidence(
        String reconciliationType,
        String reconciliationId,
        String status,
        Instant evaluatedAt,
        String rulesetVersion,
        String evidenceHash,
        Map<String, String> references
) {
    public SyirkahAuditReconciliationEvidence {
        references = Map.copyOf(references);
    }
}
```

`status` and `rulesetVersion` should be populated from the actual reconciliation artifact. The exporter must not rerun an old reconciliation using today's policy and present the result as the original close's evidence.

The evidence collection should include these groups where applicable:

- Contract identity, effective version, parties, and agreed ratios.
- Policy identity, version, effective date, and recorded approval metadata.
- Calculation inputs, calculation evidence, result and partner allocations.
- Partner movements, journal references, distributions and settlement history.
- Close snapshot and all relevant reconciliation outcomes.
- Corrections, reversals, superseded records, and corrected artifacts.
- Every missing or unverifiable relationship discovered during traversal.

## 4. Durable repository and storage interfaces

Separate export metadata from package bytes. This lets a worker resume after a crash without losing the request record, and lets storage be replaced without changing the domain model.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

public interface SyirkahAuditExportRepository {

    Uni<Void> create(SyirkahAuditExportRecord record);

    Uni<Optional<SyirkahAuditExportRecord>> findById(
            String tenantId,
            String exportId);

    Uni<Optional<SyirkahAuditExportRecord>> findByRequestId(
            String tenantId,
            String ledgerId,
            String requestId);

    Uni<Boolean> transitionStatus(
            String tenantId,
            String exportId,
            SyirkahAuditExportStatus expectedStatus,
            SyirkahAuditExportStatus newStatus);

    Uni<Void> markReady(
            String tenantId,
            String exportId,
            String storageKey,
            String manifestHash,
            boolean evidenceComplete);

    Uni<Void> markFailed(
            String tenantId,
            String exportId,
            String failureCode);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahAuditExportRecord(
        String exportId,
        String requestId,
        String tenantId,
        String ledgerId,
        String rootType,
        String rootId,
        String requestedBy,
        SyirkahAuditExportStatus status,
        Instant requestedAt,
        Instant expiresAt,
        String storageKey,
        String manifestHash,
        boolean evidenceComplete
) {}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditExportStorage {

    Uni<SyirkahStoredExport> storeImmutable(
            String tenantId,
            String exportId,
            String mediaType,
            byte[] content,
            String contentHash);

    Uni<byte[]> read(
            String tenantId,
            String storageKey);

    Uni<Void> deleteExpired(
            String tenantId,
            String exportId);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahStoredExport(
        String storageKey,
        String contentHash,
        long sizeBytes
) {}
```

Storage implementations must enforce tenant isolation, reject overwrites of finalized exports, and restrict access to the export service. Expiry should be processed by a retention worker with an audit record. If legal or contractual retention requirements apply, deletion must respect them.

## 5. Worker workflow and package validation

For large exports, use a durable worker rather than generating the entire archive during an HTTP request. The request operation should be idempotent, and each status transition should use compare-and-set semantics to prevent two workers from processing the same export concurrently.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditExportWorker {

    Uni<Void> process(String tenantId, String exportId);
}
```

The worker follows this sequence:

\#chatgpt-mermaid-\_r_2vfg\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_2vfg\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_2vfg\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_2vfg\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_2vfg\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_2vfg\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_2vfg\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_2vfg\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_2vfg\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_2vfg\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_2vfg\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_2vfg\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_2vfg\_ p{margin:0;}#chatgpt-mermaid-\_r_2vfg\_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_2vfg\_ .label text,#chatgpt-mermaid-\_r_2vfg\_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .node rect,#chatgpt-mermaid-\_r_2vfg\_ .node circle,#chatgpt-mermaid-\_r_2vfg\_ .node ellipse,#chatgpt-mermaid-\_r_2vfg\_ .node polygon,#chatgpt-mermaid-\_r_2vfg\_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .rough-node .label text,#chatgpt-mermaid-\_r_2vfg\_ .node .label text,#chatgpt-mermaid-\_r_2vfg\_ .image-shape .label,#chatgpt-mermaid-\_r_2vfg\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_2vfg\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .rough-node .label,#chatgpt-mermaid-\_r_2vfg\_ .node .label,#chatgpt-mermaid-\_r_2vfg\_ .image-shape .label,#chatgpt-mermaid-\_r_2vfg\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_2vfg\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_2vfg\_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_2vfg\_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_2vfg\_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-\_r_2vfg\_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_2vfg\_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_2vfg\_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_2vfg\_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-\_r_2vfg\_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_2vfg\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_2vfg\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_2vfg\_ .icon-shape,#chatgpt-mermaid-\_r_2vfg\_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_2vfg\_ .icon-shape p,#chatgpt-mermaid-\_r_2vfg\_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-\_r_2vfg\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_2vfg\_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_2vfg\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_2vfg\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_2vfg\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_2vfg\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_2vfg\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_2vfg\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_2vfg\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_2vfg\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_2vfg\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_2vfg\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_2vfg\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_2vfg\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_2vfg\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_2vfg\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .node rect,#chatgpt-mermaid-\_r_2vfg\_ .node circle,#chatgpt-mermaid-\_r_2vfg\_ .node ellipse,#chatgpt-mermaid-\_r_2vfg\_ .node polygon,#chatgpt-mermaid-\_r_2vfg\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_2vfg\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_2vfg\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_2vfg\_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_2vfg\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_2vfg\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Request persistedAuthorize export scopeOpen consistent evidenceboundaryCollect trace and sourceartifactsValidate completeness andrelationshipsCanonicalize and hashartifactsBuild versioned manifestSerialize JSON or ZIPStore immutable packagePersist READY metadataClose boundary and emitaudit eventFail or mark incompleteRetry or mark FAILEDBlocking finding or inconsistentreadsStorage failure

The exact transition policy should be explicit:

| Current status   | Next status  | Condition                                                    |
| ---------------- | ------------ | ------------------------------------------------------------ |
| `REQUESTED`      | `COLLECTING` | Worker claims the request                                    |
| `COLLECTING`     | `VALIDATING` | Collection completed                                         |
| `VALIDATING`     | `READY`      | Validation passed and immutable storage confirmed            |
| `VALIDATING`     | `FAILED`     | Required evidence is invalid or the boundary is inconsistent |
| Any active state | `FAILED`     | Non-retryable processing error                               |
| `READY`          | `EXPIRED`    | Retention policy permits expiry                              |
| `FAILED`         | `COLLECTING` | Explicit retry policy permits restart                        |

A policy may permit a `READY` package with `evidenceComplete = false` when incomplete evidence is useful for an investigation. If so, the manifest must report the findings and never represent the package as fully complete.

### Package hashing

Keep hashing behind an interface so the implementation can use the project's existing cryptographic facilities.

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahAuditCanonicalHasher {

    String canonicalizationVersion();

    String hashAlgorithm();

    byte[] canonicalize(Object value);

    String hash(byte[] canonicalBytes);
}
```

Recommended manifest process:

1. Canonicalize each artifact using a documented, versioned representation.
2. Calculate the hash of each artifact's canonical bytes.
3. Build the manifest containing artifact IDs, versions, counts, hashes, findings, and trace truncation status.
4. Canonicalize the manifest with `packageHash` excluded or set to a defined placeholder.
5. Hash those canonical manifest bytes and store the result as `packageHash`.
6. Verify artifact hashes and the manifest hash when the package is retrieved.

A hash alone does not establish who created the package or prevent an authorized actor from replacing both the content and its hash. If the platform already supports digital signatures or managed signing keys, add a signature over the canonical manifest. Otherwise, do not claim that the package is digitally signed.

## 6. Persistence constraints

Use database constraints alongside application-level checks. A minimal starting point:

```
CREATE TABLE syirkah_audit_export (
    export_id              VARCHAR(64)  NOT NULL,
    request_id             VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(128) NOT NULL,
    ledger_id              VARCHAR(128) NOT NULL,
    root_type              VARCHAR(64)  NOT NULL,
    root_id                VARCHAR(256) NOT NULL,
    requested_by           VARCHAR(256) NOT NULL,
    status                 VARCHAR(32)  NOT NULL,
    requested_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at             TIMESTAMP WITH TIME ZONE,
    storage_key            VARCHAR(1024),
    manifest_hash          VARCHAR(256),
    evidence_complete      BOOLEAN NOT NULL DEFAULT FALSE,
    version                BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, export_id),
    UNIQUE (tenant_id, ledger_id, request_id)
);
```

The `version` column supports optimistic concurrency. In addition, validate that a request ID cannot be reused with different request parameters; otherwise, an idempotent retry could accidentally return a package for a different root or export format.

Use the project's migration conventions and database dialect. The SQL above is a schema proposal, not a verified migration against the existing database.

## 7. Tests to add

These tests define the behavior that matters more than the archive format itself.

```
@Test
void rejectsDownloadForDifferentTenant() {
    // Given an export owned by tenant A
    // When tenant B requests its download
    // Then access is denied without revealing export metadata
}

@Test
void repeatedRequestReturnsSameExportForSameRequestId() {
    // Given the same tenant, ledger, request ID and request parameters
    // When the request is submitted more than once
    // Then only one durable export request exists
}

@Test
void marksTraceTruncationInManifest() {
    // Given a trace result whose truncated flag is true
    // When the evidence package is built
    // Then traceTruncated is true and a TRACE_TRUNCATED finding exists
}

@Test
void doesNotMarkInconsistentEvidenceComplete() {
    // Given providers reading different evidence boundaries
    // When the package is validated
    // Then evidenceComplete is false or the export fails
}

@Test
void detectsArtifactTampering() {
    // Given a finalized package
    // When an artifact's canonical bytes no longer match its stored hash
    // Then verification fails and the mismatch is recorded
}

@Test
void preservesCorrectionAndReversalLineage() {
    // Given an original artifact, reversal and corrected artifact
    // When the package is exported
    // Then all available lineage relationships are preserved
}

@Test
void doesNotExposeExportBeforeStorageAndMetadataCommit() {
    // Given package storage succeeded but READY metadata did not commit
    // When a client requests a download
    // Then the package is not exposed as READY
}
```

These are test specifications; they have not been compiled or executed against your repository.

## 8. Integration checklist

- Reuse the existing `SyirkahTraceService`, trace node types, relationship types, and findings.
- Reuse existing tenant authorization and audit-log facilities; do not introduce a parallel security system.
- Capture contract and policy versions as recorded for the original calculation, not merely their current versions.
- Export reconciliation evidence without rewriting historical outcomes.
- Preserve original journals and movements; corrections remain linked artifacts.
- Keep secrets and unnecessary personal data out of the package. Record redactions as explicit findings where they affect evidentiary completeness.
- Apply size limits, bounded trace traversal, worker concurrency limits, and retention rules.
- Persist export request, state transitions, and audit events transactionally where supported.

Next implementation step: build the concrete `SyirkahAuditEvidenceCollector` adapter over the existing repositories. That is where the abstract package becomes connected to real contract versions, calculation evidence, partner movements, journal entries, settlement records, snapshots, and correction lineage. The interfaces above intentionally leave those repository details open rather than inventing methods that may not exist in your current codebase.

/////////////

The next slice is the concrete evidence collector and package validator. It connects the export workflow to the existing Syirkah trace service and establishes a clear rule: the exporter preserves recorded evidence and relationships; it does not recalculate historical results or invent missing records.

## 1. Define the evidence-source contract

Rather than hard-coding every domain repository into one large service, introduce a small provider interface. Each provider adapts one existing repository or application service to the export model.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface SyirkahAuditEvidenceProvider {

    String providerType();

    Uni<SyirkahAuditProviderResult> collect(
            SyirkahAuditCollectionContext context);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahAuditProviderResult(
        String providerType,
        List<SyirkahAuditArtifact> artifacts,
        List<SyirkahAuditEvidenceFinding> findings,
        List<SyirkahAuditReconciliationEvidence> reconciliations
) {
    public SyirkahAuditProviderResult {
        artifacts = List.copyOf(artifacts);
        findings = List.copyOf(findings);
        reconciliations = List.copyOf(reconciliations);
    }
}
```

Use separate providers for the evidence families already identified in the trace model:

- Contract and policy versions.
- Calculation inputs and calculation evidence.
- Financial results and partner allocations.
- Partner movements and journal entries.
- Distribution obligations and settlements.
- Close snapshots and reconciliation evidence.
- Corrections, reversals, and superseded records.

Providers should return artifacts that already exist in their source systems. A missing record becomes a finding; it must not be replaced with a newly calculated approximation.

## 2. Make trace traversal a required collection input

The existing trace service already limits traversal by depth and node count. Reuse it rather than implementing a second relationship traversal algorithm in the exporter.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditTraceCollector {

    Uni<SyirkahTraceResult> collect(
            SyirkahAuditCollectionContext context);
}
```

The adapter should construct the existing `SyirkahTraceQuery` from the collection context, call `SyirkahTraceService.trace(...)`, and preserve the returned nodes, edges, findings, and truncation state. Adapt the package imports to the existing trace implementation.

The collector also needs to ensure that every artifact returned by a provider belongs to the requested tenant and ledger. A repository-level tenant filter is necessary, but the exporter should still validate the returned scope as a defense-in-depth check.

## 3. Validate scope, relationships, and evidence coverage

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahAuditPackageValidator {

    SyirkahAuditValidationResult validate(
            SyirkahAuditCollectionContext context,
            SyirkahTraceResult trace,
            List<SyirkahAuditArtifact> artifacts,
            List<SyirkahAuditEvidenceFinding> findings,
            List<SyirkahAuditReconciliationEvidence> reconciliations);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahAuditValidationResult(
        boolean valid,
        boolean evidenceComplete,
        List<SyirkahAuditEvidenceFinding> findings
) {
    public SyirkahAuditValidationResult {
        findings = List.copyOf(findings);
    }
}
```

Validation should check:

1. Scope: each artifact is in the requested tenant and ledger.
2. Identity: artifact IDs and types are present and unambiguous within their source.
3. Trace integrity: each edge references nodes present in the trace, and truncation is preserved.
4. Evidence coverage: required evidence for the root type is present or explicitly reported missing.
5. Lineage: available reversal, correction, and supersession relationships remain intact.
6. Reconciliation: historical outcomes and their ruleset versions are included where available.
7. Content integrity: included content hashes match the canonical content.
8. Consistency: all providers read from the same supported consistency boundary.

The validator should not assert that every Syirkah contract must have every possible artifact type. Evidence requirements depend on the root type and lifecycle stage. A contract-only investigation, for example, should not automatically fail because a distribution settlement does not yet exist.

## 4. Implement the collector orchestration

This implementation runs providers sequentially, making failures easier to attribute and avoiding uncontrolled concurrent load on accounting repositories. It assumes Mutiny is already used by the application module.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultSyirkahAuditEvidenceCollector
        implements SyirkahAuditEvidenceCollector {

    private final SyirkahAuditTraceCollector traceCollector;
    private final List<SyirkahAuditEvidenceProvider> providers;
    private final SyirkahAuditPackageValidator validator;
    private final SyirkahAuditPackageAssembler assembler;

    public DefaultSyirkahAuditEvidenceCollector(
            SyirkahAuditTraceCollector traceCollector,
            List<SyirkahAuditEvidenceProvider> providers,
            SyirkahAuditPackageValidator validator,
            SyirkahAuditPackageAssembler assembler) {

        this.traceCollector = Objects.requireNonNull(traceCollector);
        this.providers = List.copyOf(providers);
        this.validator = Objects.requireNonNull(validator);
        this.assembler = Objects.requireNonNull(assembler);

        long distinctTypes = this.providers.stream()
                .map(SyirkahAuditEvidenceProvider::providerType)
                .distinct()
                .count();

        if (distinctTypes != this.providers.size()) {
            throw new IllegalArgumentException(
                    "Evidence provider types must be unique");
        }
    }

    @Override
    public Uni<SyirkahAuditEvidencePackage> collect(
            SyirkahAuditCollectionContext context) {

        return traceCollector.collect(context)
                .flatMap(trace -> collectProviders(context)
                        .map(results -> assemble(context, trace, results)));
    }

    private Uni<List<SyirkahAuditProviderResult>> collectProviders(
            SyirkahAuditCollectionContext context) {

        return Multi.createFrom().iterable(providers)
                .onItem()
                .transformToUniAndConcatenate(
                        provider -> provider.collect(context))
                .collect()
                .asList();
    }

    private SyirkahAuditEvidencePackage assemble(
            SyirkahAuditCollectionContext context,
            SyirkahTraceResult trace,
            List<SyirkahAuditProviderResult> providerResults) {

        List<SyirkahAuditArtifact> artifacts = new ArrayList<>();
        List<SyirkahAuditEvidenceFinding> findings = new ArrayList<>();
        List<SyirkahAuditReconciliationEvidence> reconciliations =
                new ArrayList<>();

        for (SyirkahAuditProviderResult result : providerResults) {
            artifacts.addAll(result.artifacts());
            findings.addAll(result.findings());
            reconciliations.addAll(result.reconciliations());
        }

        SyirkahAuditValidationResult validation = validator.validate(
                context,
                trace,
                artifacts,
                findings,
                reconciliations);

        findings.addAll(validation.findings());

        return assembler.assemble(
                context,
                trace,
                artifacts,
                findings,
                reconciliations,
                validation);
    }
}
```

The assembler is responsible for creating the manifest, record counts, canonical hashes, and final immutable package model:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahAuditPackageAssembler {

    SyirkahAuditEvidencePackage assemble(
            SyirkahAuditCollectionContext context,
            SyirkahTraceResult trace,
            List<SyirkahAuditArtifact> artifacts,
            List<SyirkahAuditEvidenceFinding> findings,
            List<SyirkahAuditReconciliationEvidence> reconciliations,
            SyirkahAuditValidationResult validation);
}
```

One important refinement before production: the consistency boundary must be opened and closed by the worker or an outer orchestration component, and its token must be propagated into every provider's repository calls. The collector above deliberately does not open a boundary itself because the worker needs to control its lifecycle and cleanup.

Also, if a provider fails, do not silently omit its results and continue. The worker should classify retryable infrastructure failures separately from permanent evidence-validation failures, record a sanitized failure code, and only publish a package after validation and storage have succeeded.

## 5. Add provider coverage tests

At minimum, verify these cases:

- Every registered provider runs once per collection request.
- A provider failure fails collection instead of silently dropping evidence.
- Missing source records produce findings rather than fabricated artifacts.
- Duplicate provider types are rejected at startup.
- Cross-tenant or cross-ledger artifacts cause validation failure.
- Trace truncation is preserved in the final manifest.
- A failed validation cannot produce a package marked complete.
- A corrected journal retains links to its reversal and original entry.

These are implementation targets; I have not compiled or run them against the actual Syirkah repository.

Next: implement the concrete provider adapters and a root-type-specific evidence requirements policy. That policy is the piece that determines which evidence must exist for a contract, calculation, correction, or locked close—without confusing a genuinely missing record with a record that is not yet applicable.

///////////////

Next, implement the root-type-specific evidence requirements policy and the provider adapters. This separates two questions that an audit exporter must never confuse:

- Is this evidence required for this kind of export and lifecycle stage?
- If required, does the evidence actually exist and can it be verified?

For example, a locked-close export needs close and reconciliation evidence; a contract export may not yet have any distributions. Requirements should be configurable and versioned, rather than embedded as scattered `if` statements.

## 1. Define versioned evidence requirements

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditEvidenceType {
    CONTRACT_VERSION,
    POLICY_VERSION,
    INPUT_EVIDENCE,
    CALCULATION_EVIDENCE,
    FINANCIAL_RESULT,
    PARTNER_ALLOCATION,
    LOSS_RESPONSIBILITY,
    PARTNER_MOVEMENT,
    JOURNAL_ENTRY,
    DISTRIBUTION_OBLIGATION,
    SETTLEMENT,
    CLOSE_SNAPSHOT,
    RECONCILIATION,
    CORRECTION_LINEAGE
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditLifecycleStage {
    CONTRACT_DRAFT,
    CONTRACT_ACTIVE,
    CALCULATION_COMPLETED,
    DISTRIBUTION_IN_PROGRESS,
    CLOSE_IN_PROGRESS,
    CLOSE_LOCKED,
    CORRECTION_IN_PROGRESS,
    CORRECTION_RECLOSED,
    LIQUIDATION_IN_PROGRESS,
    TERMINATED
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Set;

public record SyirkahAuditEvidenceRequirement(
        SyirkahAuditEvidenceType evidenceType,
        boolean required,
        boolean mustBeVerified,
        String rationale
) {}
```

The rules need an explicit version so an export can explain which requirements were applied at the time it was generated.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahAuditEvidenceRequirements(
        String policyId,
        String policyVersion,
        String rootType,
        SyirkahAuditLifecycleStage lifecycleStage,
        List<SyirkahAuditEvidenceRequirement> requirements
) {
    public SyirkahAuditEvidenceRequirements {
        requirements = List.copyOf(requirements);
    }
}
```

## 2. Resolve requirements through a policy interface

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahAuditEvidenceRequirementsResolver {

    SyirkahAuditEvidenceRequirements resolve(
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId,
            SyirkahAuditLifecycleStage lifecycleStage);
}
```

The resolver should load a versioned policy from configuration or persistence. It should fail closed when no policy applies to a root type and lifecycle stage. A generic fallback that silently makes every artifact optional would make the completeness indicator unreliable.

For an initial policy, these are proposed application requirements, not a religious ruling:

| Evidence type        | Contract active        | Calculation completed         | Close locked                      | Correction reclosed                               |
| -------------------- | ---------------------- | ----------------------------- | --------------------------------- | ------------------------------------------------- |
| Contract version     | Required               | Required                      | Required                          | Required                                          |
| Policy version       | If applicable          | Required                      | Required                          | Required                                          |
| Input evidence       | Not generally required | Required                      | Required or linked to calculation | Required for corrected calculation                |
| Calculation evidence | Not generally required | Required                      | Required                          | Required                                          |
| Financial result     | Not generally required | Required                      | Required                          | Required                                          |
| Partner allocation   | Not generally required | Required when applicable      | Required when applicable          | Required when affected                            |
| Journal entry        | Not generally required | If accounting has been posted | Required for applicable postings  | Required for applicable reversals and corrections |
| Close snapshot       | Not generally required | Not generally required        | Required                          | Required                                          |
| Reconciliation       | Not generally required | Applicable calculation checks | Required                          | Required                                          |
| Correction lineage   | Not generally required | Not generally required        | If corrections affect the close   | Required                                          |

A rule marked “if applicable” must be represented explicitly in the policy or determined by a domain-specific applicability evaluator. Do not interpret it as permission to skip a failed lookup.

## 3. Add applicability evaluation

A required artifact may be inapplicable for a legitimate lifecycle reason. Keep applicability separate from existence so the audit package can explain the difference.

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceApplicability {
    APPLICABLE,
    NOT_APPLICABLE,
    UNDETERMINED
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceApplicabilityResult(
        SyirkahAuditEvidenceType evidenceType,
        SyirkahEvidenceApplicability applicability,
        String reasonCode,
        String explanation
) {}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahAuditEvidenceApplicabilityEvaluator {

    SyirkahEvidenceApplicabilityResult evaluate(
            SyirkahAuditCollectionContext context,
            SyirkahAuditEvidenceRequirements requirements,
            SyirkahAuditEvidenceType evidenceType);
}
```

Recommended interpretation:

- `APPLICABLE`: evidence must be collected and validated.
- `NOT_APPLICABLE`: the package records why it does not apply.
- `UNDETERMINED`: the exporter cannot establish applicability; it must not silently count the evidence as satisfied.

If an evidence type is required by policy but its applicability is undetermined, the package cannot be declared fully complete.

## 4. Validate requirements against collected artifacts

This validator checks the coverage policy after the providers have collected their results. It does not decide the substantive correctness of calculations or accounting treatment.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahAuditEvidenceCoverageValidator {

    SyirkahAuditValidationResult validateCoverage(
            SyirkahAuditEvidenceRequirements requirements,
            List<SyirkahEvidenceApplicabilityResult> applicability,
            List<SyirkahAuditArtifact> artifacts,
            List<SyirkahAuditEvidenceFinding> existingFindings);
}
```

Use a dedicated finding code for coverage gaps:

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditCoverageFindingCode {
    REQUIRED_EVIDENCE_MISSING,
    REQUIRED_EVIDENCE_UNVERIFIED,
    EVIDENCE_NOT_APPLICABLE,
    APPLICABILITY_UNDETERMINED,
    REQUIREMENTS_POLICY_UNAVAILABLE
}
```

The coverage validator should group artifacts by evidence type and verify each applicable requirement. It should not simply check that some journal entry exists: the evidence must be linked to the appropriate root, tenant, ledger, and source relationship.

A practical matching rule is:

1. Match evidence type.
2. Match tenant and ledger.
3. Match the expected root or a valid trace relationship to that root.
4. Confirm the artifact identity and version.
5. If required, verify the source content or immutable source reference.
6. Emit a finding for every unsatisfied requirement.

If the package includes an artifact but cannot verify its source version, it is present but unverified—not missing, and not fully verified.

## 5. Create repository-backed provider adapters

Each provider should adapt existing repositories rather than introduce duplicate domain storage. Start with contract and policy evidence because calculation, allocation, and close evidence need to reference their exact versions.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface SyirkahAuditSourceReader {

    String sourceType();

    SyirkahAuditEvidenceType evidenceType();

    Uni<List<SyirkahAuditArtifact>> findEvidence(
            SyirkahAuditCollectionContext context);
}
```

A source reader should return immutable version references wherever available. The provider wrapper can then standardize errors and findings.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Objects;

public final class DefaultSyirkahAuditEvidenceProvider
        implements SyirkahAuditEvidenceProvider {

    private final String providerType;
    private final List<SyirkahAuditSourceReader> readers;

    public DefaultSyirkahAuditEvidenceProvider(
            String providerType,
            List<SyirkahAuditSourceReader> readers) {

        if (providerType == null || providerType.isBlank()) {
            throw new IllegalArgumentException(
                    "providerType must not be blank");
        }

        this.providerType = providerType;
        this.readers = List.copyOf(readers);

        if (this.readers.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one source reader is required");
        }

        this.readers.forEach(Objects::requireNonNull);
    }

    @Override
    public String providerType() {
        return providerType;
    }

    @Override
    public Uni<SyirkahAuditProviderResult> collect(
            SyirkahAuditCollectionContext context) {

        return io.smallrye.mutiny.Multi.createFrom()
                .iterable(readers)
                .onItem()
                .transformToUniAndConcatenate(reader ->
                        reader.findEvidence(context))
                .collect()
                .asList()
                .map(groups -> new SyirkahAuditProviderResult(
                        providerType,
                        groups.stream()
                                .flatMap(List::stream)
                                .toList(),
                        List.of(),
                        List.of()));
    }
}
```

This wrapper deliberately lets infrastructure failures propagate. A missing record should be converted to a finding by the source reader; a database outage or permission error must not be misreported as an ordinary missing record.

For clearer operational diagnosis, add a provider-specific failure code to the export worker's error handling. Avoid catching every exception and returning an empty list.

## 6. Record the requirements policy in the manifest

Add these fields to `SyirkahAuditExportManifest`:

```
String requirementsPolicyId,
String requirementsPolicyVersion,
String requirementsPolicyHash,
String completenessStatus
```

Use a controlled completeness status instead of relying only on a boolean:

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditCompletenessStatus {
    COMPLETE,
    COMPLETE_WITH_DISCLOSED_LIMITATIONS,
    INCOMPLETE,
    UNVERIFIED
}
```

Interpretation:

- `COMPLETE`: all applicable mandatory evidence is present and verified.
- `COMPLETE_WITH_DISCLOSED_LIMITATIONS`: mandatory requirements are met, but explicitly permitted limitations remain.
- `INCOMPLETE`: one or more mandatory evidence requirements are unsatisfied.
- `UNVERIFIED`: the exporter cannot establish whether the completeness claim is valid.

A truncated trace should not be labelled `COMPLETE` if traversal may have omitted mandatory evidence. Similarly, a missing historical policy version must not be silently replaced by the current policy.

## 7. Tests for the requirements policy

```
@Test
void lockedCloseRequiresSnapshotAndReconciliationEvidence() {
    // Given the locked-close requirements policy
    // When snapshot or mandatory reconciliation evidence is absent
    // Then the package cannot be marked COMPLETE
}

@Test
void contractExportDoesNotRequireUnrelatedSettlement() {
    // Given an active contract with no distribution obligation
    // When contract evidence is validated
    // Then settlement absence does not create a false failure
}

@Test
void undeterminedApplicabilityDoesNotPassCoverage() {
    // Given a required evidence type with undetermined applicability
    // When coverage is validated
    // Then completeness is not COMPLETE
}

@Test
void missingHistoricalPolicyIsNotReplacedByCurrentPolicy() {
    // Given a calculation referring to an unavailable policy version
    // When evidence is collected
    // Then the original reference and a missing-version finding are retained
}

@Test
void evidenceFromAnotherLedgerIsRejected() {
    // Given an otherwise valid artifact from a different ledger
    // When the package is validated
    // Then validation fails
}
```

These are test specifications, not tests executed against the current repository.

## 8. Implementation order

1. Implement the requirements policy repository and versioned resolver.
2. Add lifecycle-stage resolution from existing contract, calculation, correction, and close records.
3. Implement contract and policy source readers.
4. Implement calculation, allocation, movement, journal, distribution, settlement, snapshot, and correction readers.
5. Integrate applicability and coverage validation into the collector.
6. Include the policy version, policy hash, and findings in the final manifest.
7. Add integration tests for tenant isolation, locked-close completeness, and correction lineage.

One architectural constraint matters here: the exporter should report historical evidence, not become another accounting engine. The calculation engine, accounting ledger, reconciliation services, and approved policy records remain the sources of truth.

The next slice after these adapters is export verification and independent audit replay: verify package hashes, validate graph integrity, compare the export against its recorded manifest, and distinguish replaying the original recorded calculation from recalculating it under a newer policy.

///////////


Next is independent export verification and calculation replay. These must be separate operations: verification checks whether an exported package is intact and internally consistent, while replay checks whether a recorded calculation can be reproduced from its original inputs, rules, and implementation version.

A successful hash check does not establish that the accounting treatment or Sharia interpretation was correct. Likewise, a replay that produces a different result is a discrepancy to investigate, not automatic proof that the original result was wrong.

## 1. Define verification outcomes

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditVerificationStatus {
    VERIFIED,
    VERIFIED_WITH_LIMITATIONS,
    INVALID,
    INDETERMINATE
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahAuditVerificationCheck {
    PACKAGE_HASH,
    ARTIFACT_HASHES,
    MANIFEST_SCHEMA,
    ARTIFACT_IDENTITY,
    TRACE_EDGE_INTEGRITY,
    TENANT_LEDGER_SCOPE,
    REQUIREMENTS_POLICY,
    EVIDENCE_COVERAGE,
    CORRECTION_LINEAGE,
    RECONCILIATION_REFERENCES
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahAuditVerificationFinding(
        SyirkahAuditVerificationCheck check,
        String artifactId,
        String code,
        String message,
        boolean blocksVerification
) {}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahAuditVerificationReport(
        String verificationId,
        String exportId,
        SyirkahAuditVerificationStatus status,
        Instant verifiedAt,
        String verifierVersion,
        List<SyirkahAuditVerificationFinding> findings
) {
    public SyirkahAuditVerificationReport {
        findings = List.copyOf(findings);
    }
}
```

The verifier must be read-only. It should never modify the original package, repair broken references in place, or silently replace a historical artifact with its current version.

## 2. Separate package loading from verification

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditPackageReader {

    Uni<SyirkahAuditEvidencePackage> read(
            String tenantId,
            String exportId);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditExportVerifier {

    Uni<SyirkahAuditVerificationReport> verify(
            String tenantId,
            String exportId,
            String verifiedBy);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahAuditPackageIntegrityChecker {

    List<SyirkahAuditVerificationFinding> check(
            SyirkahAuditEvidencePackage evidencePackage);
}
```

The integrity checker should verify at least:

- The canonical manifest hash matches the stored manifest.
- Each embedded artifact matches its declared canonical hash.
- Artifact identifiers are unique in the package's defined identity scope.
- Every trace edge points to nodes present in the trace.
- Tenant and ledger scope matches the export manifest.
- The recorded requirements policy version is present and its hash matches, if the policy content was embedded.
- Correction, reversal, and supersession links are internally consistent.
- Manifest record counts match the actual records.

If verification depends on a policy document or source artifact that was not included, the report should say the check could not be completed. It should not assume success.

## 3. Implement the verification service

Keep canonicalization and hash calculation behind the previously defined `SyirkahAuditCanonicalHasher`. The verifier must use the manifest's declared canonicalization version and hash algorithm, and reject unsupported versions rather than silently using a newer representation.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DefaultSyirkahAuditExportVerifier
        implements SyirkahAuditExportVerifier {

    private final SyirkahAuditPackageReader packageReader;
    private final SyirkahAuditPackageIntegrityChecker integrityChecker;
    private final SyirkahAuditVerificationRepository verificationRepository;
    private final SyirkahAuditExportAccessPolicy accessPolicy;
    private final Clock clock;
    private final String verifierVersion;

    public DefaultSyirkahAuditExportVerifier(
            SyirkahAuditPackageReader packageReader,
            SyirkahAuditPackageIntegrityChecker integrityChecker,
            SyirkahAuditVerificationRepository verificationRepository,
            SyirkahAuditExportAccessPolicy accessPolicy,
            Clock clock,
            String verifierVersion) {

        this.packageReader = Objects.requireNonNull(packageReader);
        this.integrityChecker = Objects.requireNonNull(integrityChecker);
        this.verificationRepository =
                Objects.requireNonNull(verificationRepository);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
        this.clock = Objects.requireNonNull(clock);
        this.verifierVersion = Objects.requireNonNull(verifierVersion);
    }

    @Override
    public Uni<SyirkahAuditVerificationReport> verify(
            String tenantId,
            String exportId,
            String verifiedBy) {

        return accessPolicy.requireCanVerify(
                        tenantId, exportId, verifiedBy)
                .flatMap(ignored -> packageReader.read(tenantId, exportId))
                .map(evidencePackage -> {
                    List<SyirkahAuditVerificationFinding> findings =
                            integrityChecker.check(evidencePackage);

                    SyirkahAuditVerificationStatus status =
                            determineStatus(findings);

                    SyirkahAuditVerificationReport report =
                            new SyirkahAuditVerificationReport(
                                    UUID.randomUUID().toString(),
                                    exportId,
                                    status,
                                    Instant.now(clock),
                                    verifierVersion,
                                    findings);

                    return report;
                })
                .flatMap(report ->
                        verificationRepository.save(tenantId, report)
                                .replaceWith(report));
    }

    private SyirkahAuditVerificationStatus determineStatus(
            List<SyirkahAuditVerificationFinding> findings) {

        if (findings.stream().anyMatch(
                SyirkahAuditVerificationFinding::blocksVerification)) {
            return SyirkahAuditVerificationStatus.INVALID;
        }

        if (findings.isEmpty()) {
            return SyirkahAuditVerificationStatus.VERIFIED;
        }

        return SyirkahAuditVerificationStatus.VERIFIED_WITH_LIMITATIONS;
    }
}
```

This is the service skeleton. Before production, the checker needs a richer outcome than a list of findings alone so it can distinguish an actual integrity failure from a check that could not run. Otherwise, the service risks labelling an indeterminate verification as merely “verified with limitations.”

Use this expanded outcome instead:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahAuditIntegrityResult(
        boolean integrityFailureDetected,
        boolean checksIncomplete,
        List<SyirkahAuditVerificationFinding> findings
) {
    public SyirkahAuditIntegrityResult {
        findings = List.copyOf(findings);
    }

    public SyirkahAuditVerificationStatus status() {
        if (integrityFailureDetected) {
            return SyirkahAuditVerificationStatus.INVALID;
        }

        if (checksIncomplete) {
            return SyirkahAuditVerificationStatus.INDETERMINATE;
        }

        if (!findings.isEmpty()) {
            return SyirkahAuditVerificationStatus.VERIFIED_WITH_LIMITATIONS;
        }

        return SyirkahAuditVerificationStatus.VERIFIED;
    }
}
```

Use `SyirkahAuditIntegrityResult` as the checker return type in the production implementation.

## 4. Persist verification as a separate audit record

Verification is an event about an export, not a change to the export itself.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditVerificationRepository {

    Uni<Void> save(
            String tenantId,
            SyirkahAuditVerificationReport report);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahAuditExportAccessPolicy {

    Uni<Void> requireCanVerify(
            String tenantId,
            String exportId,
            String verifiedBy);
}
```

A production implementation should also persist the verified package hash and verifier version with the report. That makes the report reproducible as an observation about a particular package version. If a new export is generated later, it gets a new export ID and verification history rather than overwriting the prior report.

## 5. Model calculation replay independently

Replay should have its own result status. A replay request must identify the original calculation evidence and explicitly select the replay mode.

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahCalculationReplayMode {
    HISTORICAL_REPRODUCTION,
    CURRENT_RULE_COMPARISON
}
```

- `HISTORICAL_REPRODUCTION` uses the recorded original inputs, policy version, rounding rules, and calculation implementation version, to the extent those artifacts are available.
- `CURRENT_RULE_COMPARISON` compares the historical inputs with a deliberately selected current policy and implementation. It is a comparison, not a replacement for the historical record.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Objects;
import java.util.UUID;

public record SyirkahCalculationReplayCommand(
        String tenantId,
        String ledgerId,
        String calculationEvidenceId,
        String requestedBy,
        SyirkahCalculationReplayMode mode,
        String targetPolicyVersion,
        UUID requestId
) {
    public SyirkahCalculationReplayCommand {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(calculationEvidenceId, "calculationEvidenceId");
        requireText(requestedBy, "requestedBy");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(requestId, "requestId");

        if (mode == SyirkahCalculationReplayMode.CURRENT_RULE_COMPARISON
                && (targetPolicyVersion == null
                || targetPolicyVersion.isBlank())) {
            throw new IllegalArgumentException(
                    "targetPolicyVersion is required for current-rule comparison");
        }

        if (mode == SyirkahCalculationReplayMode.HISTORICAL_REPRODUCTION
                && targetPolicyVersion != null) {
            throw new IllegalArgumentException(
                    "Historical reproduction uses the recorded policy version");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
```

The replay engine should not post journals, update partner positions, change distributions, or alter close snapshots. Its output is evidence for review. Any correction must go through the existing correction workflow and its approval controls.

## 6. Define the replay engine contract

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationReplayService {

    Uni<SyirkahCalculationReplayReport> replay(
            SyirkahCalculationReplayCommand command);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyirkahCalculationReplayReport(
        String replayId,
        String calculationEvidenceId,
        SyirkahCalculationReplayMode mode,
        SyirkahCalculationReplayStatus status,
        String originalPolicyVersion,
        String replayedPolicyVersion,
        String originalImplementationVersion,
        String replayedImplementationVersion,
        String originalResultHash,
        String replayedResultHash,
        List<SyirkahCalculationReplayDifference> differences,
        List<String> limitations,
        Instant replayedAt,
        String requestedBy
) {
    public SyirkahCalculationReplayReport {
        differences = List.copyOf(differences);
        limitations = List.copyOf(limitations);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahCalculationReplayStatus {
    MATCHED,
    DIFFERENCES_FOUND,
    INSUFFICIENT_EVIDENCE,
    UNSUPPORTED_IMPLEMENTATION,
    FAILED
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahCalculationReplayDifference(
        String fieldPath,
        String originalValue,
        String replayedValue,
        String differenceType,
        String explanation
) {}
```

The replay service needs a version-aware engine resolver rather than a direct call to whichever calculator happens to be current.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationEngineResolver {

    Uni<SyirkahVersionedCalculationEngine> resolve(
            String implementationVersion);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahVersionedCalculationEngine {

    String implementationVersion();

    Uni<SyirkahCalculationReplayOutput> calculate(
            SyirkahCalculationReplayInput input);
}
```

`SyirkahCalculationReplayInput` should be an immutable snapshot of the recorded calculation inputs, not a live query of mutable partner balances or current contract terms. `SyirkahCalculationReplayOutput` should contain the calculated values, currency and rounding metadata, and canonical result representation.

If the historical implementation cannot be resolved, report `UNSUPPORTED_IMPLEMENTATION`. Do not substitute the current engine and label its result a historical reproduction.

## 7. Comparison rules

A result hash is useful for exact comparison, but it should not be the only comparison mechanism.

| Check                              | What it establishes                                    |
| ---------------------------------- | ------------------------------------------------------ |
| Exact canonical result hash        | Whether canonical result representations match         |
| Field-level comparison             | Which amounts, ratios, or result fields differ         |
| Input hash comparison              | Whether replay used the same recorded inputs           |
| Policy and implementation versions | Which rules and implementation produced each result    |
| Rounding and currency comparison   | Whether differences may arise from numeric conventions |
| Evidence availability              | Whether the replay can support a meaningful comparison |

A matching result does not prove that the original inputs were accurate or that the policy was applied correctly. A difference does not by itself establish an error; it needs investigation against the recorded inputs, policy, implementation, and rounding rules.

## 8. Integration tests

Add tests for:

- A modified artifact causing verification to return `INVALID`.
- An unavailable historical hash algorithm returning `INDETERMINATE`.
- A valid package with disclosed limitations returning `VERIFIED_WITH_LIMITATIONS`.
- A historical replay refusing to substitute an unavailable implementation.
- A current-rule comparison preserving both the original and target policy versions.
- A replay never posting journals or changing close state.
- Two identical replay requests being idempotent using `(tenant_id, ledger_id, request_id)`.
- A replay difference creating evidence for review, not automatically launching a correction.

These are implementation specifications, not tests executed against the repository.

## 9. Recommended next slice

The next step is to connect replay to the existing `SyirkahProfitSharingEngine`, `SyirkahResultCalculator`, and policy-resolution mechanisms through versioned adapters, then persist replay reports alongside the audit evidence.

One important boundary: the current implementation versions and historical calculation artifacts available in the repository have not been verified here. The resolver must use actual persisted version identifiers; if those are not currently stored, introduce version metadata prospectively and report older calculations as insufficiently reproducible rather than fabricating historical version information.

////////////


Next, connect the replay framework to the existing Syirkah calculation engine and historical policy records. The objective is to make `HISTORICAL_REPRODUCTION` and `CURRENT_RULE_COMPARISON` use explicit, versioned inputs without touching posted journals, partner balances, or locked close snapshots.

The key design decision is to separate the recorded calculation from the engine that executes a replay. Historical evidence remains immutable even when the calculation implementation evolves.

## 1. Define an immutable calculation evidence record

This record captures the information needed to reproduce a calculation. Keep the recorded result separate from the inputs used to calculate it.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyirkahCalculationEvidenceRecord(
        String evidenceId,
        String tenantId,
        String ledgerId,
        String contractId,
        String contractVersion,
        String policyId,
        String policyVersion,
        String implementationVersion,
        String inputSchemaVersion,
        String canonicalizationVersion,
        String inputHash,
        String resultHash,
        String currencyCode,
        String roundingMode,
        int roundingScale,
        Instant calculatedAt,
        String calculatedBy,
        List<SyirkahCalculationInputArtifact> inputs,
        Map<String, String> resultValues
) {
    public SyirkahCalculationEvidenceRecord {
        inputs = List.copyOf(inputs);
        resultValues = Map.copyOf(resultValues);

        if (evidenceId == null || evidenceId.isBlank()) {
            throw new IllegalArgumentException("evidenceId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (ledgerId == null || ledgerId.isBlank()) {
            throw new IllegalArgumentException("ledgerId is required");
        }
        if (implementationVersion == null
                || implementationVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "implementationVersion is required");
        }
        if (roundingScale < 0) {
            throw new IllegalArgumentException(
                    "roundingScale must not be negative");
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahCalculationInputArtifact(
        String inputType,
        String artifactId,
        String artifactVersion,
        String canonicalHash,
        boolean contentAvailable
) {}
```

Important: these fields are prospective requirements for newly recorded calculations. For older calculations, absent implementation versions or input snapshots must remain explicitly unavailable; do not backfill guessed values.

## 2. Add a versioned evidence repository

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

public interface SyirkahCalculationEvidenceRepository {

    Uni<Optional<SyirkahCalculationEvidenceRecord>> findById(
            String tenantId,
            String ledgerId,
            String evidenceId);

    Uni<Void> append(SyirkahCalculationEvidenceRecord evidence);
}
```

Treat `append` as immutable insertion. Enforce a unique key on `(tenant_id, ledger_id, evidence_id)` and reject attempts to replace existing evidence. If the current database uses another repository convention, implement this interface as an adapter rather than introducing duplicate persistence.

## 3. Adapt the existing Syirkah calculator

Do not make the audit module depend on a new, parallel profit-sharing implementation. Wrap the existing `SyirkahProfitSharingEngine` and `SyirkahResultCalculator` behind a versioned port.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Map;

public record SyirkahCalculationReplayInput(
        String tenantId,
        String ledgerId,
        String contractId,
        String contractVersion,
        String policyId,
        String policyVersion,
        String inputSchemaVersion,
        String currencyCode,
        String roundingMode,
        int roundingScale,
        Map<String, String> inputValues
) {
    public SyirkahCalculationReplayInput {
        inputValues = Map.copyOf(inputValues);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Map;

public record SyirkahCalculationReplayOutput(
        Map<String, String> resultValues,
        String canonicalResultHash,
        String currencyCode,
        String roundingMode,
        int roundingScale
) {
    public SyirkahCalculationReplayOutput {
        resultValues = Map.copyOf(resultValues);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationReplayAdapter {

    String implementationVersion();

    boolean supportsInputSchema(String inputSchemaVersion);

    Uni<SyirkahCalculationReplayOutput> calculate(
            SyirkahCalculationReplayInput input);
}
```

Register one adapter per supported historical implementation version. The adapter must resolve the exact contract and policy versions referenced by the input; it must not silently load the latest versions.

The actual adapter should translate the stored input snapshot into the input types expected by the existing engine. Since the concrete method signatures and packages of those engines have not been verified here, this port is the integration boundary—not a claim that a specific adapter has already been compiled.

## 4. Resolve replay dependencies explicitly

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahHistoricalPolicyResolver {

    Uni<SyirkahResolvedPolicy> resolve(
            String tenantId,
            String ledgerId,
            String policyId,
            String policyVersion);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahResolvedPolicy(
        String policyId,
        String policyVersion,
        String canonicalPolicyHash,
        boolean immutableVersionAvailable
) {}
```

The replay should stop with `INSUFFICIENT_EVIDENCE` when a required input snapshot or policy version is missing, and with `UNSUPPORTED_IMPLEMENTATION` when the historical engine version is unavailable. A current-policy comparison can proceed only when the requested target policy is explicitly identified and resolvable.

## 5. Implement replay orchestration

The service below illustrates the dependency checks and exact-match comparison. The `SyirkahReplayInputLoader` must load the original immutable input snapshot and verify its hash before returning it.

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahReplayInputLoader {

    Uni<SyirkahCalculationReplayInput> loadAndVerify(
            SyirkahCalculationEvidenceRecord evidence);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import io.smallrye.mutiny.Uni;

public interface SyirkahReplayReportRepository {

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            String requestId,
            SyirkahCalculationReplayReport report);
}
```

The orchestrator should follow this order:

1. Load evidence scoped to tenant and ledger.
2. Verify recorded input and result hashes.
3. Resolve the required policy and implementation versions.
4. Load the immutable inputs.
5. Execute the selected adapter in an isolated, read-only replay context.
6. Compare canonical outputs and individual fields.
7. Persist the report idempotently using the request ID.

For historical reproduction, resolve `evidence.implementationVersion()` and `evidence.policyVersion()`. For current-rule comparison, resolve the explicitly requested target policy and a separately configured target implementation. Preserve both version identifiers in the report.

Do not execute replay through the journal-posting service or close-finalization transaction. A replay is an analysis operation and must have no financial side effects.

## 6. Define equality and discrepancy rules

Use canonical decimal strings or the project's established money representation—not binary floating-point comparisons.

- Exact canonical result match: `MATCHED`.
- Any material field difference: `DIFFERENCES_FOUND`.
- Missing inputs, policy versions, or original hashes: `INSUFFICIENT_EVIDENCE`.
- Historical implementation unavailable: `UNSUPPORTED_IMPLEMENTATION`.
- Infrastructure or execution failure: `FAILED`.

Record differences with field paths such as `partnerAllocations.partner-123.amount`, with original and replayed values, currency, scale, and rounding metadata. Keep the original recorded result untouched.

## 7. Tests for the adapter boundary

```
@Test
void historicalReplayUsesRecordedPolicyAndImplementation() {
    // Given evidence with a specific policy and engine version
    // When historical reproduction is requested
    // Then those exact versions are resolved
}

@Test
void missingInputSnapshotPreventsReplay() {
    // Given a historical evidence record whose input content is unavailable
    // When replay is requested
    // Then the report is INSUFFICIENT_EVIDENCE
}

@Test
void currentRuleComparisonDoesNotRewriteHistoricalEvidence() {
    // Given an original result and an explicitly selected target policy
    // When the comparison produces a different result
    // Then both results are retained and the difference is reported
}

@Test
void replayNeverPostsJournalEntries() {
    // Given a valid replay request
    // When replay completes
    // Then no journal-posting or close-finalization operation is invoked
}
```

These are test specifications, not executed tests.

## 8. Next: persist calculation evidence at the original transaction boundary

The next change should be in the existing calculation workflow itself: when a Syirkah calculation is accepted, persist the calculation result and its immutable evidence record together, including the contract version, policy version, implementation version, canonical inputs, and result hash.

That evidence should be committed atomically with the accepted calculation record when the existing persistence architecture supports it. If evidence persistence fails, the application must not report the calculation as fully recorded and auditable. Avoid recalculating historical data just to create missing evidence; for legacy records, expose the missing provenance as a limitation.

////////////

Next, implement the atomic calculation acceptance and evidence-persistence boundary. This connects the existing Syirkah calculation workflow to the audit evidence introduced in the previous slices.

The invariant is:

> A calculation must not be reported as accepted and auditable unless its result, immutable calculation evidence, and provenance references have been durably recorded together.

This should extend the existing calculation workflow—not introduce a second profit-sharing engine or change the existing accounting rules.

## 1. Define the accepted-calculation record

Keep the calculated business result separate from the evidence needed to reproduce it.

```
package tech.kayys.syirkah.accounting.application.calculation;

import java.time.Instant;

public record SyirkahCalculationAcceptance(
        String calculationId,
        String tenantId,
        String ledgerId,
        String contractId,
        String contractVersion,
        String policyId,
        String policyVersion,
        String implementationVersion,
        String calculationEvidenceId,
        String resultHash,
        Instant acceptedAt,
        String acceptedBy
) {}
```

The record should reference the existing `SyirkahCalculationId` and established identifier types where available. The string-based form above is an integration sketch, not a recommendation to replace typed IDs throughout the project.

## 2. Add a transaction boundary

The application service should coordinate the existing calculator and persistence ports. The transaction implementation belongs in the infrastructure module, using the project's existing transaction mechanism.

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;
import java.util.function.Supplier;

public interface SyirkahCalculationTransactionManager {

    <T> Uni<T> inTransaction(Supplier<Uni<T>> work);
}
```

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationAcceptanceRepository {

    Uni<Void> insertAcceptance(
            SyirkahCalculationAcceptance acceptance);

    Uni<Boolean> existsByIdempotencyKey(
            String tenantId,
            String ledgerId,
            String idempotencyKey);
}
```

The calculation evidence repository should support immutable insertion within the same transaction:

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationEvidenceWriter {

    Uni<Void> appendWithinTransaction(
            SyirkahCalculationEvidenceRecord evidence);
}
```

Use the existing `SyirkahCalculationEvidenceRecord` from the audit integration, adjusting its package as necessary. Avoid creating two independent definitions of the evidence record.

## 3. Coordinate calculation, evidence, and acceptance

The application service should call the existing calculation engine, capture the exact inputs and versions used, then persist the accepted result and evidence together.

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationExecutor {

    Uni<SyirkahCalculatedOutcome> calculate(
            SyirkahCalculationRequest request);
}
```

```
package tech.kayys.syirkah.accounting.application.calculation;

import java.util.Map;

public record SyirkahCalculatedOutcome(
        String calculationId,
        String tenantId,
        String ledgerId,
        String contractId,
        String contractVersion,
        String policyId,
        String policyVersion,
        String implementationVersion,
        String inputSchemaVersion,
        String currencyCode,
        String roundingMode,
        int roundingScale,
        Map<String, String> canonicalInputValues,
        Map<String, String> resultValues
) {
    public SyirkahCalculatedOutcome {
        canonicalInputValues = Map.copyOf(canonicalInputValues);
        resultValues = Map.copyOf(resultValues);
    }
}
```

The request type should reuse the existing `CloseRequest` or calculation command if it already contains the required fields. The above types illustrate the application boundary; they should not be duplicated if equivalent types already exist.

The acceptance service can then orchestrate the transaction:

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationAcceptanceService {

    Uni<SyirkahCalculationAcceptance> calculateAndAccept(
            SyirkahCalculationRequest request);
}
```

A concrete implementation should perform these operations in order:

1. Validate tenant, ledger, contract, and idempotency key.
2. Resolve the effective contract and policy versions.
3. Execute the existing calculation engine.
4. Canonicalize and hash the inputs and result.
5. Construct the immutable calculation evidence.
6. Insert the accepted result and evidence in one transaction.
7. Commit before returning acceptance to the caller.

Do not trust version fields supplied by the client. The application must capture versions from the actual contract, policy, and implementation resolved for the calculation.

## 4. Make retries safe

A client may retry after a timeout even though the original transaction committed. Add a durable idempotency record or unique constraint so the retry returns the original acceptance instead of creating a second calculation.

```
CREATE TABLE syirkah_calculation_idempotency (
    tenant_id          VARCHAR(128) NOT NULL,
    ledger_id           VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    request_hash        VARCHAR(256) NOT NULL,
    calculation_id      VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, ledger_id, idempotency_key)
);
```

The idempotency key alone is not sufficient. Compare `request_hash` as well:

- Same key and same request hash: return the original accepted calculation.
- Same key and different request hash: reject the request as an idempotency conflict.
- Concurrent requests with the same key: let the database uniqueness constraint arbitrate, then load the committed result.

The idempotency record, accepted calculation, evidence record, and any outbox event must be committed in the same transaction where they share a database. Avoid implementing this as a pre-check followed by unrelated writes; concurrent requests can both pass a pre-check.

## 5. Define the evidence writer's responsibilities

The evidence writer should create an immutable record from the actual outcome, not reconstruct inputs by querying mutable records after calculation.

```
package tech.kayys.syirkah.accounting.application.calculation;

import io.smallrye.mutiny.Uni;

public interface SyirkahCalculationEvidenceFactory {

    Uni<SyirkahCalculationEvidenceRecord> create(
            SyirkahCalculatedOutcome outcome,
            String acceptedBy);
}
```

The factory must:

- Canonicalize the exact inputs passed to the engine.
- Capture the resolved contract and policy versions.
- Capture the implementation and input-schema versions.
- Hash canonical input and output representations.
- Preserve currency and rounding metadata.
- Reject incomplete provenance instead of inventing version information.

If the engine currently receives only live domain objects, add a stable input snapshot at the application boundary before executing it. Ensure the snapshot corresponds to the values the engine actually used.

## 6. Publish an event only after the evidence is durable

If downstream audit indexing, reporting, or export workflows need to know about accepted calculations, use the existing transactional outbox mechanism if the platform has one.

Suggested event:

```
package tech.kayys.syirkah.accounting.application.calculation;

import java.time.Instant;

public record SyirkahCalculationAcceptedEvent(
        String eventId,
        String tenantId,
        String ledgerId,
        String calculationId,
        String calculationEvidenceId,
        String resultHash,
        Instant occurredAt
) {}
```

Write the outbox record in the same transaction as the acceptance and evidence records. Dispatch it after commit. Consumers should be idempotent because outbox delivery is normally at least once.

Do not publish a success event before the transaction commits, and do not include partner-sensitive calculation inputs in the event unless the consumer genuinely needs them.

## 7. Required database guarantees

Use the project's actual table names and migration conventions. At a minimum, enforce:

- Unique calculation identity within its tenant and ledger scope.
- Unique evidence identity within tenant and ledger.
- A foreign-key relationship between accepted calculation and evidence where feasible.
- Immutable evidence writes, enforced by repository permissions and application logic.
- Unique idempotency keys within tenant and ledger.
- Transactional outbox uniqueness for event identity.

If the acceptance record and evidence reside in separate databases, a local database transaction cannot make them atomic. In that case, use a durable workflow with explicit states such as `PENDING_EVIDENCE`, `ACCEPTED`, and `FAILED`, and never present `PENDING_EVIDENCE` as fully auditable.

## 8. Tests to implement

```
@Test
void acceptanceAndEvidenceCommitTogether() {
    // Simulate evidence persistence failure.
    // Verify the accepted calculation is not committed.
}

@Test
void retryReturnsOriginalAcceptedCalculation() {
    // Submit the same request and idempotency key twice.
    // Verify only one accepted calculation and evidence record exist.
}

@Test
void idempotencyKeyCannotBeReusedForDifferentRequest() {
    // Reuse a key with a different request hash.
    // Verify a conflict is returned.
}

@Test
void evidenceUsesResolvedVersionsNotClientSuppliedVersions() {
    // Supply misleading version metadata in a request.
    // Verify evidence contains the versions actually resolved by the application.
}

@Test
void outboxEventIsCommittedWithAcceptance() {
    // Verify acceptance, evidence and outbox records share the transaction.
}

@Test
void historicalEvidenceCannotBeOverwritten() {
    // Attempt to replace evidence for an accepted calculation.
    // Verify the operation is rejected.
}
```

These are test specifications; they have not been run against your repository.

## Next implementation target

Implement the concrete adapter for the existing `SyirkahCloseProcessorImpl` and `SyirkahCloseCalculationRepository` so close-time calculations use this acceptance boundary. The adapter should preserve the existing close-cycle task ordering, persist evidence for each accepted result, and keep distribution posting behind the existing approval and journal-posting workflow.

One distinction is essential: calculating a result during a close is not necessarily the same as accepting it as the final locked-period result. The close workflow should identify the calculation evidence used for finalization, and the existing close-validation pipeline should verify that the evidence belongs to the same tenant, ledger, period, contract version, and policy snapshot before locking the period.


/////////////

Next, wire the calculation-evidence acceptance boundary into `SyirkahCloseProcessorImpl` and the close-validation pipeline. The goal is to ensure a close uses identifiable, durable calculation evidence and that the finalization gates validate that evidence before the fiscal period is locked.

The existing close task sequence stays intact. This change extends close processing; it does not replace the accounting close engine.

## 1. Persist a close-to-calculation evidence link

A close can involve several calculations, so do not store only one `calculationEvidenceId` on the close cycle. Introduce a relationship record that identifies which evidence was used and for which contract and period.

```
package tech.kayys.syirkah.accounting.application.close;

import java.time.Instant;

public record SyirkahCloseCalculationEvidenceLink(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String fiscalPeriodId,
        String contractId,
        String contractVersion,
        String calculationId,
        String calculationEvidenceId,
        String policySnapshotId,
        String resultHash,
        Instant linkedAt,
        String linkedBy
) {}
```

The record should be immutable after close finalization. If a correction changes the calculation, create a new evidence link for the corrected calculation and retain the original link for lineage.

## 2. Add a repository for close evidence links

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

import java.util.List;

public interface SyirkahCloseCalculationEvidenceRepository {

    Uni<Void> appendWithinTransaction(
            SyirkahCloseCalculationEvidenceLink link);

    Uni<List<SyirkahCloseCalculationEvidenceLink>> findByCloseCycle(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<Boolean> existsForCalculation(
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String calculationEvidenceId);
}
```

Enforce uniqueness for a close cycle and evidence link so retries cannot duplicate the same relationship. Use the project's migration conventions; the following illustrates the intended constraint:

```
CREATE UNIQUE INDEX ux_syirkah_close_calculation_evidence
ON syirkah_close_calculation_evidence
    (tenant_id, ledger_id, close_cycle_id, calculation_evidence_id);
```

## 3. Extend the close processor without bypassing existing logic

The close processor should call the existing calculation flow, then link the accepted calculation evidence to the close. Avoid making the close processor calculate profit-sharing results independently.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseEvidenceLinker {

    Uni<SyirkahCloseCalculationEvidenceLink> linkAcceptedCalculation(
            SyirkahCloseEvidenceLinkRequest request);
}
```

```
package tech.kayys.syirkah.accounting.application.close;

public record SyirkahCloseEvidenceLinkRequest(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String fiscalPeriodId,
        String contractId,
        String contractVersion,
        String calculationId,
        String calculationEvidenceId,
        String policySnapshotId,
        String resultHash,
        String linkedBy
) {}
```

The implementation should load the accepted calculation evidence from the existing evidence repository and verify that the tenant, ledger, contract version, policy version, and result hash match the close request. It must not trust identifiers or hashes supplied by the caller without resolving the corresponding persisted record.

The link should be inserted within the same transaction as the close calculation acceptance when both operations share the same transaction boundary. If they cannot share a transaction, use a durable workflow and prevent finalization until the link has been confirmed.

## 4. Add a close calculation evidence gate

This gate checks that every calculation required by the close is linked to valid evidence and that the evidence agrees with the close's recorded policy snapshot.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseCalculationEvidenceGate {

    Uni<CloseGateResult> validate(
            CloseValidationContext context);
}
```

The implementation should use the existing `CloseGateResult` and `CloseValidationContext` types. Its validation sequence should be:

1. Load all evidence links for the requested tenant, ledger, and close cycle.
2. Determine which contracts and calculations are required for this close.
3. Confirm every required calculation has a persisted evidence record.
4. Verify each link's contract version, calculation result hash, and policy snapshot reference.
5. Reject evidence from another tenant, ledger, or fiscal period.
6. Report missing, duplicated, or inconsistent evidence as explicit gate findings.

A successful lookup of one evidence link is not sufficient if the close requires several contracts or calculations. The gate needs the expected calculation set from the close's actual domain data.

### Connect it to the existing validation pipeline

Register the new gate immediately after the existing Syirkah result reconciliation gate and before partner movement, position, and distribution checks:

```
CLOSE_TASK_GATE
JOURNAL_RECONCILIATION_GATE
SYIRKAH_RESULT_GATE
SYIRKAH_CALCULATION_EVIDENCE_GATE
PARTNER_MOVEMENT_ACCOUNTING_GATE
PARTNER_POSITION_GATE
DISTRIBUTION_SETTLEMENT_CORRECTION_GATE
DISTRIBUTION_SETTLEMENT_GATE
SNAPSHOT_INTEGRITY_GATE
CORRECTION_GATE
POLICY_SNAPSHOT_INTEGRITY_GATE
```

This preserves the existing validation order while ensuring later gates cannot cause a close to be finalized using unverified calculation provenance.

## 5. Freeze evidence links during finalization

The finalization transaction must validate evidence and lock the period atomically with the existing snapshot and correction checks.

Extend the existing finalization transaction internally to perform:

```
BEGIN
  Lock fiscal period
  Lock close cycle and relevant correction
  Verify fiscal period is OPEN
  Verify close calculation evidence links
  Run all CloseValidationPipeline gates
  Activate the final close snapshot
  Mark close cycle completed
  Lock fiscal period
  Persist audit and outbox records
COMMIT
```

If any required evidence is missing, the transaction must roll back. Do not mark the close as locked and attempt to repair provenance afterward.

For concurrent requests, use database locks or conditional state transitions. An application-level check such as “period was open a moment ago” is not sufficient protection against two close-finalization requests racing each other.

## 6. Tests to add

```
@Test
void closeCannotFinalizeWithoutRequiredCalculationEvidence() {
    // Required calculation exists but its evidence link is absent.
    // Finalization must be rejected.
}

@Test
void closeEvidenceMustMatchPolicySnapshot() {
    // Calculation evidence references a different policy snapshot.
    // The evidence gate must block finalization.
}

@Test
void closeEvidenceMustMatchContractVersion() {
    // The contract has a different effective version.
    // The evidence link must be rejected.
}

@Test
void closeEvidenceLinkIsIdempotent() {
    // The same accepted calculation is linked twice.
    // Only one relationship record is persisted.
}

@Test
void failedFinalizationLeavesFiscalPeriodOpen() {
    // Evidence validation fails inside finalization.
    // No snapshot activation or period lock is committed.
}

@Test
void correctionPreservesOriginalEvidenceLink() {
    // A corrected calculation is linked to a close.
    // The original evidence link remains available for traceability.
}
```

These are proposed tests, not tests run against the repository.

## 7. Important integration details

- Do not equate contract version with policy snapshot version. They are separate versioned references and must be validated independently.
- A close can legitimately involve multiple contracts and calculation records. Validate completeness against the expected set rather than accepting any nonempty list.
- Keep evidence records immutable after acceptance. Corrections should create new calculation evidence and new links.
- Use the existing `CloseGateResult`, `CloseValidationContext`, `SyirkahCloseProcessorImpl`, and finalization transaction where possible. The proposed interfaces are adapters, not instructions to duplicate existing services.
- If the close calculation and evidence records use different databases, explicitly model the intermediate state and block finalization until evidence persistence is confirmed.

Next implementation slice: implement the expected-calculation-set resolver for a close period. It should derive the contracts and calculations that must be accounted for from the actual close data, compare that set with the persisted evidence links, and produce a deterministic reconciliation report. That closes the gap between “some evidence exists” and “all calculations required for this close have traceable evidence.”

///////////////
## Next slice: expected calculation set for a Syirkah close

The close process needs to establish not only that calculation evidence exists, but that every calculation required by the applicable contracts and close policy is represented by valid evidence.

The key design distinction is that expected calculations must come from a versioned requirement policy and the actual contract scope for the period. Do not assume that every contract requires exactly one calculation: a contract may require multiple calculation kinds, and some requirements may depend on lifecycle stage or close policy.

The resolver should be deterministic and read-only. It should not calculate profit, post journals, create evidence, or mutate the close snapshot.

### 1. Define the expected calculation identity

Use a stable key that distinguishes calculation kinds for the same contract and period.

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.Objects;

public record SyirkahExpectedCalculationIdentity(
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String contractId,
    String contractVersion,
    String calculationKind
) {
    public SyirkahExpectedCalculationIdentity {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(contractId);
        Objects.requireNonNull(contractVersion);
        Objects.requireNonNull(calculationKind);

        if (tenantId.isBlank()
            || ledgerId.isBlank()
            || fiscalPeriodId.isBlank()
            || contractId.isBlank()
            || contractVersion.isBlank()
            || calculationKind.isBlank()) {
            throw new IllegalArgumentException(
                "Expected calculation identity fields must not be blank"
            );
        }
    }

    public String canonicalValue() {
        return String.join(
            "|",
            tenantId,
            ledgerId,
            fiscalPeriodId,
            contractId,
            contractVersion,
            calculationKind
        );
    }
}
```

This `canonicalValue()` is suitable for a readable stable key, but not a cryptographic encoding: if field values can contain `|`, use a canonical structured encoding or length-prefixed fields before hashing.

### 2. Represent each expectation and the resolved set

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.Objects;

public record SyirkahExpectedCloseCalculation(
    SyirkahExpectedCalculationIdentity identity,
    String closeCycleId,
    String policySnapshotId,
    String requirementRuleId,
    boolean required
) {
    public SyirkahExpectedCloseCalculation {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(policySnapshotId);
        Objects.requireNonNull(requirementRuleId);
    }
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;
import java.util.Objects;

public record SyirkahExpectedCalculationSet(
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String closeCycleId,
    String policySnapshotId,
    String scopeRevision,
    String resolverVersion,
    List<SyirkahExpectedCloseCalculation> expectations,
    String setHash
) {
    public SyirkahExpectedCalculationSet {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(policySnapshotId);
        Objects.requireNonNull(scopeRevision);
        Objects.requireNonNull(resolverVersion);
        expectations = List.copyOf(expectations);
        Objects.requireNonNull(setHash);
    }
}
```

`scopeRevision` identifies the stable version or revision of the source contract scope. `resolverVersion` records which resolution logic was used. `setHash` must be computed from canonicalized, sorted fields—including the policy and scope revisions—not from incidental repository iteration order.

### 3. Separate contract scope from calculation requirements

Keep the resolver independent of persistence details. The scope reader returns contracts relevant to the close period; the versioned policy determines which calculation kinds are required for each contract.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseExpectedCalculationResolver {

    Uni<SyirkahExpectedCalculationSet> resolve(
        CloseValidationContext context
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface SyirkahCloseContractScopeReader {

    Uni<SyirkahCloseContractScope> readScope(
        CloseValidationContext context
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public record SyirkahCloseContractScope(
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String scopeRevision,
    List<SyirkahCloseContractScopeItem> contracts
) {
    public SyirkahCloseContractScope {
        contracts = List.copyOf(contracts);
    }
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahCloseContractScopeItem(
    String contractId,
    String contractVersion,
    String syirkahType,
    String lifecycleStage
) {}
```

The scope reader should use the platform's actual contract and period data. If the existing `SyirkahContractRepository` only supports looking up one effective contract, implement this port in the infrastructure adapter rather than assuming a bulk repository method already exists.

The requirements policy must be versioned and bound to the policy snapshot used by the close:

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public interface SyirkahCloseCalculationRequirementPolicy {

    String policySnapshotId();

    String policyVersion();

    List<SyirkahCalculationRequirement> requirementsFor(
        SyirkahCloseContractScopeItem contract
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahCalculationRequirement(
    String calculationKind,
    String requirementRuleId,
    boolean required
) {}
```

Examples of `calculationKind` values should be defined by the platform's actual calculation workflow, not invented by the resolver. The requirement policy decides whether a given calculation kind applies to a contract and lifecycle stage.

### 4. Reconcile expectations against persisted evidence links

Reuse the existing `SyirkahCloseCalculationEvidenceRepository.findByCloseCycle(...)`. Do not infer completeness merely from the number of linked records: the evidence must match the expected identity and the accepted calculation evidence.

Define findings explicitly so the close gate can report actionable discrepancies.

```
package tech.kayys.syirkah.accounting.domain.close;

public enum SyirkahCloseEvidenceFindingCode {
    MISSING_EXPECTED_EVIDENCE,
    UNEXPECTED_EVIDENCE,
    DUPLICATE_EVIDENCE,
    SCOPE_MISMATCH,
    CONTRACT_VERSION_MISMATCH,
    POLICY_SNAPSHOT_MISMATCH,
    RESULT_HASH_MISMATCH,
    MISSING_SOURCE_EVIDENCE,
    EXPECTED_SET_UNSTABLE
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahCloseEvidenceFinding(
    SyirkahCloseEvidenceFindingCode code,
    String expectedCalculationKey,
    String calculationEvidenceId,
    String detail
) {}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public record SyirkahCloseEvidenceReconciliationReport(
    String tenantId,
    String ledgerId,
    String closeCycleId,
    String expectedSetHash,
    int expectedCount,
    int linkedCount,
    int matchedCount,
    List<SyirkahCloseEvidenceFinding> findings,
    boolean complete
) {
    public SyirkahCloseEvidenceReconciliationReport {
        findings = List.copyOf(findings);
    }
}
```

The reconciler should apply the following rules:

1. Missing: every required expected identity must have matching evidence.
2. Unexpected: a link must not satisfy an expectation outside the resolved contract scope or calculation requirements.
3. Duplicate: multiple accepted evidence links for the same expected identity must be flagged unless the domain explicitly supports multiple evidence records for that identity.
4. Version and policy: contract version and policy snapshot must match the expected versions.
5. Integrity: the linked result hash must match the accepted evidence record's result hash.
6. Source completeness: required input evidence must exist; a valid result hash alone does not prove that the calculation inputs are complete or correct.
7. Determinism: sort expectations and findings by stable keys before computing hashes or producing the report.

A `complete` report should mean there are no blocking findings and all required expectations are matched. Optional requirements should remain visible in the report, but should not automatically block close unless the applicable policy says they must.

### 5. Build the resolver deterministically

The following is an implementation outline. It assumes a policy snapshot can be resolved from the close context; adapt that lookup to the existing policy-snapshot service rather than introducing a second policy store.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class SyirkahCloseExpectedCalculationResolverImpl
    implements SyirkahCloseExpectedCalculationResolver {

    private static final String RESOLVER_VERSION = "1";

    private final SyirkahCloseContractScopeReader scopeReader;
    private final SyirkahCloseRequirementPolicyProvider policyProvider;
    private final SyirkahExpectedCalculationSetHasher hasher;

    public SyirkahCloseExpectedCalculationResolverImpl(
        SyirkahCloseContractScopeReader scopeReader,
        SyirkahCloseRequirementPolicyProvider policyProvider,
        SyirkahExpectedCalculationSetHasher hasher
    ) {
        this.scopeReader = scopeReader;
        this.policyProvider = policyProvider;
        this.hasher = hasher;
    }

    @Override
    public Uni<SyirkahExpectedCalculationSet> resolve(
        CloseValidationContext context
    ) {
        return scopeReader.readScope(context)
            .flatMap(scope ->
                policyProvider.resolveFor(context)
                    .map(policy -> buildSet(context, scope, policy))
            );
    }

    private SyirkahExpectedCalculationSet buildSet(
        CloseValidationContext context,
        SyirkahCloseContractScope scope,
        SyirkahCloseCalculationRequirementPolicy policy
    ) {
        validateScope(context, scope);
        validatePolicy(context, policy);

        List<SyirkahExpectedCloseCalculation> expectations =
            new ArrayList<>();

        for (SyirkahCloseContractScopeItem contract : scope.contracts()) {
            for (SyirkahCalculationRequirement requirement
                : policy.requirementsFor(contract)) {

                var identity = new SyirkahExpectedCalculationIdentity(
                    context.tenantId(),
                    context.ledgerId(),
                    context.fiscalPeriodId(),
                    contract.contractId(),
                    contract.contractVersion(),
                    requirement.calculationKind()
                );

                expectations.add(
                    new SyirkahExpectedCloseCalculation(
                        identity,
                        context.closeCycleId(),
                        policy.policySnapshotId(),
                        requirement.requirementRuleId(),
                        requirement.required()
                    )
                );
            }
        }

        expectations.sort(
            Comparator.comparing(
                item -> item.identity().canonicalValue()
            )
        );

        rejectDuplicateIdentities(expectations);

        String hash = hasher.hash(
            context,
            scope,
            policy,
            RESOLVER_VERSION,
            expectations
        );

        return new SyirkahExpectedCalculationSet(
            context.tenantId(),
            context.ledgerId(),
            context.fiscalPeriodId(),
            context.closeCycleId(),
            policy.policySnapshotId(),
            scope.scopeRevision(),
            RESOLVER_VERSION,
            expectations,
            hash
        );
    }

    private void validateScope(
        CloseValidationContext context,
        SyirkahCloseContractScope scope
    ) {
        if (!context.tenantId().equals(scope.tenantId())
            || !context.ledgerId().equals(scope.ledgerId())
            || !context.fiscalPeriodId().equals(scope.fiscalPeriodId())) {
            throw new IllegalStateException(
                "Resolved contract scope does not match close context"
            );
        }
    }

    private void validatePolicy(
        CloseValidationContext context,
        SyirkahCloseCalculationRequirementPolicy policy
    ) {
        if (context.policySnapshotId() == null
            || !context.policySnapshotId().equals(
                policy.policySnapshotId()
            )) {
            throw new IllegalStateException(
                "Resolved calculation policy does not match close snapshot"
            );
        }
    }

    private void rejectDuplicateIdentities(
        List<SyirkahExpectedCloseCalculation> expectations
    ) {
        for (int i = 1; i < expectations.size(); i++) {
            var previous = expectations.get(i - 1).identity();
            var current = expectations.get(i).identity();

            if (previous.equals(current)) {
                throw new IllegalStateException(
                    "Calculation requirement policy produced duplicate "
                        + "expected calculation identities"
                );
            }
        }
    }
}
```

Supporting ports:

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseRequirementPolicyProvider {

    Uni<SyirkahCloseCalculationRequirementPolicy> resolveFor(
        CloseValidationContext context
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public interface SyirkahExpectedCalculationSetHasher {

    String hash(
        CloseValidationContext context,
        SyirkahCloseContractScope scope,
        SyirkahCloseCalculationRequirementPolicy policy,
        String resolverVersion,
        List<SyirkahExpectedCloseCalculation> expectations
    );
}
```

The hasher should use a canonical encoding and a cryptographic hash such as SHA-256. It should include all fields that affect the expected set, including requirement rule IDs, required flags, scope revision, policy version/snapshot, resolver version, and sorted expectation identities.

### 6. Integrate reconciliation with the existing close gate

Keep the existing `CloseGateResult` construction isolated because its concrete shape is repository-specific. The new gate can depend on a reconciliation service and a mapper to that existing result type.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseEvidenceReconciler {

    Uni<SyirkahCloseEvidenceReconciliationReport> reconcile(
        CloseValidationContext context,
        SyirkahExpectedCalculationSet expectedSet
    );
}
```

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SyirkahCloseCalculationEvidenceGate {

    private final SyirkahCloseExpectedCalculationResolver resolver;
    private final SyirkahCloseEvidenceReconciler reconciler;
    private final SyirkahCloseEvidenceGateResultMapper resultMapper;

    public SyirkahCloseCalculationEvidenceGate(
        SyirkahCloseExpectedCalculationResolver resolver,
        SyirkahCloseEvidenceReconciler reconciler,
        SyirkahCloseEvidenceGateResultMapper resultMapper
    ) {
        this.resolver = resolver;
        this.reconciler = reconciler;
        this.resultMapper = resultMapper;
    }

    public Uni<CloseGateResult> validate(
        CloseValidationContext context
    ) {
        return resolver.resolve(context)
            .flatMap(expectedSet ->
                reconciler.reconcile(context, expectedSet)
                    .map(resultMapper::toGateResult)
            );
    }
}
```

`SyirkahCloseEvidenceGateResultMapper` is an adapter to implement using the actual `CloseGateResult` API. The gate should fail closed if the expected set cannot be resolved, if its source revision is unstable, or if evidence cannot be verified.

Register this gate after the existing Syirkah result gate and before partner movement, accounting, and finalization gates that depend on those calculations. The exact ordering should follow the dependencies in the current `SyirkahCloseValidationPipeline`.

Transaction boundary: run the gate inside the existing close-finalization transaction, before activating a new snapshot or locking the period. If contract scope or the policy snapshot can change concurrently, pin their revisions and verify them again before commit, or use the platform's equivalent concurrency-control mechanism.

### 7. Tests to add

| Test                                                 | Expected behavior                                               |
| ---------------------------------------------------- | --------------------------------------------------------------- |
| Required calculation has no evidence link            | Block close with `MISSING_EXPECTED_EVIDENCE`                    |
| Two links satisfy one expected identity              | Flag `DUPLICATE_EVIDENCE`                                       |
| Link refers to a contract outside the resolved scope | Flag `UNEXPECTED_EVIDENCE` or `SCOPE_MISMATCH`                  |
| Contract version differs                             | Block close with `CONTRACT_VERSION_MISMATCH`                    |
| Policy snapshot differs                              | Block close with `POLICY_SNAPSHOT_MISMATCH`                     |
| Link result hash differs from accepted evidence      | Block close with `RESULT_HASH_MISMATCH`                         |
| Source evidence is incomplete                        | Block close when required by the applicable policy              |
| Repository returns contracts in a different order    | Produce the same expected-set hash                              |
| Scope revision changes during finalization           | Abort or retry; do not finalize against an unstable set         |
| Reconciliation fails                                 | No snapshot activation, period lock, or correction finalization |

One additional rule is important: if an expected calculation was valid when first resolved but the underlying contract scope or policy changes before finalization, do not silently resolve a different set and continue. Restart validation against a newly pinned scope and policy revision, with the change recorded.

These are implementation proposals based on the architecture described so far; they have not been compiled or tested against the repository. The next slice is the concrete evidence reconciler, including the mapping from each persisted evidence link to its accepted calculation evidence record and the deterministic finding-generation rules.

//////////////

## Next slice: concrete evidence reconciler

The reconciler compares the expected calculation set with persisted close-evidence links and the underlying accepted calculation evidence. It must distinguish “a link exists” from “the correct evidence exists and matches the expected contract, policy, and result.”

The implementation below keeps storage behind ports, so it can be adapted to the existing repositories without assuming their exact database schema.

### 1. Define a normalized view of accepted evidence

The reconciler needs a read-only view of evidence that has already been accepted by the calculation workflow.

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahAcceptedCalculationEvidence(
    String tenantId,
    String ledgerId,
    String calculationEvidenceId,
    String calculationId,
    String fiscalPeriodId,
    String contractId,
    String contractVersion,
    String calculationKind,
    String policySnapshotId,
    String resultHash,
    boolean accepted,
    boolean sourceEvidenceComplete
) {}
```

This is a query projection, not a replacement for the platform's canonical evidence entity. The infrastructure adapter should map the actual persisted record into this projection.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;
import java.util.Collection;
import java.util.List;

public interface SyirkahAcceptedCalculationEvidenceReader {

    Uni<List<SyirkahAcceptedCalculationEvidence>> findByIds(
        String tenantId,
        String ledgerId,
        Collection<String> calculationEvidenceIds
    );
}
```

The reader must enforce tenant and ledger scope in the query itself, not merely filter records after loading them.

### 2. Define a stable reconciliation service

The reconciler reads the expected set, the links for the close cycle, and the accepted evidence records. It must report all actionable findings rather than stopping at the first mismatch.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

import tech.kayys.syirkah.accounting.domain.close.*;

@ApplicationScoped
public class SyirkahCloseEvidenceReconcilerImpl
    implements SyirkahCloseEvidenceReconciler {

    private final SyirkahCloseCalculationEvidenceRepository linkRepository;
    private final SyirkahAcceptedCalculationEvidenceReader evidenceReader;
    private final SyirkahCloseEvidenceReconciliationEngine engine;

    public SyirkahCloseEvidenceReconcilerImpl(
        SyirkahCloseCalculationEvidenceRepository linkRepository,
        SyirkahAcceptedCalculationEvidenceReader evidenceReader,
        SyirkahCloseEvidenceReconciliationEngine engine
    ) {
        this.linkRepository = linkRepository;
        this.evidenceReader = evidenceReader;
        this.engine = engine;
    }

    @Override
    public Uni<SyirkahCloseEvidenceReconciliationReport> reconcile(
        CloseValidationContext context,
        SyirkahExpectedCalculationSet expectedSet
    ) {
        validateContext(context, expectedSet);

        return linkRepository.findByCloseCycle(
                context.tenantId(),
                context.ledgerId(),
                context.closeCycleId()
            )
            .flatMap(links -> {
                List<String> evidenceIds = links.stream()
                    .map(SyirkahCloseCalculationEvidenceLink::calculationEvidenceId)
                    .distinct()
                    .sorted()
                    .toList();

                return evidenceReader.findByIds(
                    context.tenantId(),
                    context.ledgerId(),
                    evidenceIds
                ).map(evidence ->
                    engine.reconcile(context, expectedSet, links, evidence)
                );
            });
    }

    private void validateContext(
        CloseValidationContext context,
        SyirkahExpectedCalculationSet expectedSet
    ) {
        if (!context.tenantId().equals(expectedSet.tenantId())
            || !context.ledgerId().equals(expectedSet.ledgerId())
            || !context.fiscalPeriodId().equals(expectedSet.fiscalPeriodId())
            || !context.closeCycleId().equals(expectedSet.closeCycleId())
            || !context.policySnapshotId().equals(
                expectedSet.policySnapshotId()
            )) {
            throw new IllegalArgumentException(
                "Expected calculation set does not match close context"
            );
        }
    }
}
```

This assumes the existing link repository returns `Uni<List<SyirkahCloseCalculationEvidenceLink>>`. If its actual return type differs, adapt the boundary without changing the reconciliation rules.

### 3. Implement the deterministic reconciliation engine

One important adjustment: a link must be matched to an expectation using the contract and calculation kind from the accepted evidence, not just by counting records or matching `calculationEvidenceId`. The evidence record and link must also agree with each other.

```
package tech.kayys.syirkah.accounting.application.close;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import tech.kayys.syirkah.accounting.domain.close.*;

@ApplicationScoped
public class SyirkahCloseEvidenceReconciliationEngine {

    public SyirkahCloseEvidenceReconciliationReport reconcile(
        CloseValidationContext context,
        SyirkahExpectedCalculationSet expectedSet,
        List<SyirkahCloseCalculationEvidenceLink> links,
        List<SyirkahAcceptedCalculationEvidence> evidenceRecords
    ) {
        List<SyirkahCloseEvidenceFinding> findings = new ArrayList<>();

        Map<String, SyirkahAcceptedCalculationEvidence> evidenceById =
            indexEvidence(evidenceRecords, findings);

        Map<String, List<SyirkahCloseCalculationEvidenceLink>> linksByEvidenceId =
            links.stream().collect(Collectors.groupingBy(
                SyirkahCloseCalculationEvidenceLink::calculationEvidenceId
            ));

        Map<String, List<SyirkahCloseCalculationEvidenceLink>> linksByIdentity =
            new HashMap<>();

        Map<String, SyirkahExpectedCloseCalculation> expectedByIdentity =
            expectedSet.expectations().stream().collect(Collectors.toMap(
                item -> item.identity().canonicalValue(),
                Function.identity()
            ));

        for (SyirkahCloseCalculationEvidenceLink link : links) {
            SyirkahAcceptedCalculationEvidence evidence =
                evidenceById.get(link.calculationEvidenceId());

            if (evidence == null) {
                findings.add(finding(
                    SyirkahCloseEvidenceFindingCode.MISSING_SOURCE_EVIDENCE,
                    null,
                    link.calculationEvidenceId(),
                    "The evidence link references no accepted evidence record"
                ));
                continue;
            }

            validateLinkScope(context, link, findings);
            validateEvidenceScope(context, evidence, findings);
            validateLinkEvidenceConsistency(link, evidence, findings);

            String identityKey = identityKey(evidence);
            linksByIdentity.computeIfAbsent(
                identityKey,
                ignored -> new ArrayList<>()
            ).add(link);

            SyirkahExpectedCloseCalculation expected =
                expectedByIdentity.get(identityKey);

            if (expected == null) {
                findings.add(finding(
                    SyirkahCloseEvidenceFindingCode.UNEXPECTED_EVIDENCE,
                    identityKey,
                    link.calculationEvidenceId(),
                    "Evidence does not match an expected calculation identity"
                ));
                continue;
            }

            validateAgainstExpectation(
                expected,
                link,
                evidence,
                findings
            );
        }

        int matchedCount = 0;

        for (SyirkahExpectedCloseCalculation expected
            : expectedSet.expectations()) {

            String key = expected.identity().canonicalValue();
            List<SyirkahCloseCalculationEvidenceLink> matches =
                linksByIdentity.getOrDefault(key, List.of());

            List<SyirkahCloseCalculationEvidenceLink> validMatches =
                matches.stream()
                    .filter(link -> isValidMatch(
                        expected,
                        link,
                        evidenceById.get(link.calculationEvidenceId()),
                        context
                    ))
                    .toList();

            if (expected.required() && validMatches.isEmpty()) {
                findings.add(finding(
                    SyirkahCloseEvidenceFindingCode.MISSING_EXPECTED_EVIDENCE,
                    key,
                    null,
                    "Required calculation has no valid evidence link"
                ));
            }

            if (validMatches.size() > 1) {
                findings.add(finding(
                    SyirkahCloseEvidenceFindingCode.DUPLICATE_EVIDENCE,
                    key,
                    null,
                    "Multiple evidence links satisfy the same expected identity"
                ));
            }

            if (validMatches.size() == 1) {
                matchedCount++;
            }
        }

        findings.sort(
            Comparator.comparing(
                (SyirkahCloseEvidenceFinding f) -> f.code().name()
            ).thenComparing(
                f -> Objects.toString(f.expectedCalculationKey(), "")
            ).thenComparing(
                f -> Objects.toString(f.calculationEvidenceId(), "")
            ).thenComparing(SyirkahCloseEvidenceFinding::detail)
        );

        boolean complete = findings.isEmpty();

        return new SyirkahCloseEvidenceReconciliationReport(
            context.tenantId(),
            context.ledgerId(),
            context.closeCycleId(),
            expectedSet.setHash(),
            expectedSet.expectations().size(),
            links.size(),
            matchedCount,
            findings,
            complete
        );
    }

    private Map<String, SyirkahAcceptedCalculationEvidence> indexEvidence(
        List<SyirkahAcceptedCalculationEvidence> records,
        List<SyirkahCloseEvidenceFinding> findings
    ) {
        Map<String, SyirkahAcceptedCalculationEvidence> result =
            new HashMap<>();

        for (SyirkahAcceptedCalculationEvidence record : records) {
            var previous = result.putIfAbsent(
                record.calculationEvidenceId(),
                record
            );

            if (previous != null) {
                findings.add(finding(
                    SyirkahCloseEvidenceFindingCode.SCOPE_MISMATCH,
                    null,
                    record.calculationEvidenceId(),
                    "Evidence reader returned duplicate evidence identifiers"
                ));
            }
        }

        return result;
    }

    private String identityKey(
        SyirkahAcceptedCalculationEvidence evidence
    ) {
        return new SyirkahExpectedCalculationIdentity(
            evidence.tenantId(),
            evidence.ledgerId(),
            evidence.fiscalPeriodId(),
            evidence.contractId(),
            evidence.contractVersion(),
            evidence.calculationKind()
        ).canonicalValue();
    }

    private SyirkahCloseEvidenceFinding finding(
        SyirkahCloseEvidenceFindingCode code,
        String expectedKey,
        String evidenceId,
        String detail
    ) {
        return new SyirkahCloseEvidenceFinding(
            code,
            expectedKey,
            evidenceId,
            detail
        );
    }

    // The validation helpers below are defined in the next section.
}
```

This engine uses the identity's canonical value as a lookup key. In production, use a structured identity as the map key if canonical string collisions are possible.

### 4. Add scope, version, policy, and hash validation

Add these helper methods to the engine. They make the match criteria explicit and keep invalid evidence from satisfying an expected calculation.

```
private void validateLinkScope(
    CloseValidationContext context,
    SyirkahCloseCalculationEvidenceLink link,
    List<SyirkahCloseEvidenceFinding> findings
) {
    boolean valid =
        context.tenantId().equals(link.tenantId())
        && context.ledgerId().equals(link.ledgerId())
        && context.closeCycleId().equals(link.closeCycleId())
        && context.fiscalPeriodId().equals(link.fiscalPeriodId());

    if (!valid) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.SCOPE_MISMATCH,
            null,
            link.calculationEvidenceId(),
            "Evidence link does not belong to the current close scope"
        ));
    }
}

private void validateEvidenceScope(
    CloseValidationContext context,
    SyirkahAcceptedCalculationEvidence evidence,
    List<SyirkahCloseEvidenceFinding> findings
) {
    boolean valid =
        context.tenantId().equals(evidence.tenantId())
        && context.ledgerId().equals(evidence.ledgerId())
        && context.fiscalPeriodId().equals(evidence.fiscalPeriodId());

    if (!valid) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.SCOPE_MISMATCH,
            null,
            evidence.calculationEvidenceId(),
            "Accepted evidence does not belong to the current close scope"
        ));
    }
}

private void validateLinkEvidenceConsistency(
    SyirkahCloseCalculationEvidenceLink link,
    SyirkahAcceptedCalculationEvidence evidence,
    List<SyirkahCloseEvidenceFinding> findings
) {
    if (!Objects.equals(link.contractId(), evidence.contractId())
        || !Objects.equals(link.contractVersion(), evidence.contractVersion())) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.CONTRACT_VERSION_MISMATCH,
            null,
            evidence.calculationEvidenceId(),
            "Evidence link and accepted evidence identify different contract versions"
        ));
    }

    if (!Objects.equals(
        link.policySnapshotId(),
        evidence.policySnapshotId()
    )) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.POLICY_SNAPSHOT_MISMATCH,
            null,
            evidence.calculationEvidenceId(),
            "Evidence link and accepted evidence use different policy snapshots"
        ));
    }

    if (!Objects.equals(link.resultHash(), evidence.resultHash())) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.RESULT_HASH_MISMATCH,
            null,
            evidence.calculationEvidenceId(),
            "Evidence link result hash differs from accepted evidence"
        ));
    }
}

private void validateAgainstExpectation(
    SyirkahExpectedCloseCalculation expected,
    SyirkahCloseCalculationEvidenceLink link,
    SyirkahAcceptedCalculationEvidence evidence,
    List<SyirkahCloseEvidenceFinding> findings
) {
    String key = expected.identity().canonicalValue();

    if (!expected.identity().contractVersion().equals(
        evidence.contractVersion()
    )) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.CONTRACT_VERSION_MISMATCH,
            key,
            evidence.calculationEvidenceId(),
            "Evidence contract version differs from the expected version"
        ));
    }

    if (!expected.policySnapshotId().equals(
        evidence.policySnapshotId()
    )) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.POLICY_SNAPSHOT_MISMATCH,
            key,
            evidence.calculationEvidenceId(),
            "Evidence policy snapshot differs from the expected snapshot"
        ));
    }

    if (!evidence.accepted()) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.MISSING_SOURCE_EVIDENCE,
            key,
            evidence.calculationEvidenceId(),
            "Calculation evidence is not in an accepted state"
        ));
    }

    if (!evidence.sourceEvidenceComplete()) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.MISSING_SOURCE_EVIDENCE,
            key,
            evidence.calculationEvidenceId(),
            "Required calculation input evidence is incomplete"
        ));
    }

    if (evidence.resultHash() == null || evidence.resultHash().isBlank()) {
        findings.add(finding(
            SyirkahCloseEvidenceFindingCode.RESULT_HASH_MISMATCH,
            key,
            evidence.calculationEvidenceId(),
            "Accepted evidence has no result hash"
        ));
    }
}

private boolean isValidMatch(
    SyirkahExpectedCloseCalculation expected,
    SyirkahCloseCalculationEvidenceLink link,
    SyirkahAcceptedCalculationEvidence evidence,
    CloseValidationContext context
) {
    if (evidence == null || !evidence.accepted()
        || !evidence.sourceEvidenceComplete()) {
        return false;
    }

    return expected.identity().tenantId().equals(evidence.tenantId())
        && expected.identity().ledgerId().equals(evidence.ledgerId())
        && expected.identity().fiscalPeriodId().equals(evidence.fiscalPeriodId())
        && expected.identity().contractId().equals(evidence.contractId())
        && expected.identity().contractVersion().equals(evidence.contractVersion())
        && expected.identity().calculationKind().equals(evidence.calculationKind())
        && expected.policySnapshotId().equals(evidence.policySnapshotId())
        && expected.policySnapshotId().equals(link.policySnapshotId())
        && context.closeCycleId().equals(link.closeCycleId())
        && context.fiscalPeriodId().equals(link.fiscalPeriodId())
        && context.tenantId().equals(link.tenantId())
        && context.ledgerId().equals(link.ledgerId())
        && Objects.equals(link.contractId(), evidence.contractId())
        && Objects.equals(link.contractVersion(), evidence.contractVersion())
        && Objects.equals(link.resultHash(), evidence.resultHash())
        && evidence.resultHash() != null
        && !evidence.resultHash().isBlank();
}
```

Important implementation notes:

- The code above is a starting point, not a drop-in patch. Adapt method names and repository return types to the actual project.
- Avoid counting the same underlying evidence twice if it has multiple links. The expected identity should be satisfied by one valid evidence record, and duplicate links should be reported separately.
- A required calculation with a mismatched policy or contract version should not satisfy the expectation.
- The example uses `findByIds(...)` to retrieve evidence. If the reader silently omits inaccessible or nonexistent IDs, the reconciler should treat each unresolved link as a finding, as shown.
- The current report's `complete` flag should be based on blocking findings, not necessarily every informational finding. If the platform needs warnings, add a severity field to findings and derive completeness from severity.

### 5. Persistence and concurrency requirements

For this reconciler to be dependable at close time:

1. Enforce tenant, ledger, close-cycle, and evidence-ID constraints in the database query.
2. Add or retain a uniqueness constraint for a close-cycle/evidence link to prevent concurrent duplicate insertion.
3. Pin the expected-set hash and scope revision for the validation attempt.
4. Ensure the contract scope, accepted evidence, and policy snapshot cannot change unnoticed between validation and finalization.
5. Persist the reconciliation report or its immutable reference with the close audit trail, so a later reviewer can see why the close passed or failed.

### 6. Focused test cases

- Matching evidence produces a complete report.
- Missing required evidence produces a blocking finding.
- Evidence for an unexpected contract or calculation kind does not satisfy an expectation.
- Contract-version and policy-snapshot mismatches are both reported.
- A missing or unaccepted evidence record blocks the corresponding required expectation.
- Repository result order does not affect findings order or expected-set hash.
- Two links for the same expected calculation are detected as duplicates.
- A cross-tenant or cross-ledger evidence ID cannot satisfy the expectation.
- Failed reconciliation leaves close snapshots and period state unchanged.

The next slice should be the close-finalization integration and concurrency boundary: pinning the expected-set revision, running the evidence gate inside the finalization transaction, and ensuring a failed gate cannot supersede snapshots or lock the period.

/////////////

## Next slice: atomic close finalization with pinned evidence

The finalization boundary must guarantee that the close is validated against the same contract scope, policy snapshot, and evidence set that the system ultimately records. If validation fails—or any pinned revision changes—the transaction must not activate a new snapshot, supersede the previous snapshot, lock the period, or mark a correction as reclosed.

The implementation should extend the existing close-finalization workflow rather than introduce a separate transaction path.

### 1. Capture a close-validation pin

The pin records the versions and hashes against which validation was performed. It is a consistency token, not a replacement for the underlying evidence.

```
package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;
import java.util.Objects;

public record SyirkahCloseValidationPin(
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String closeCycleId,
    String snapshotId,
    String correctionId,
    String policySnapshotId,
    String contractScopeRevision,
    String expectedCalculationSetHash,
    Instant pinnedAt
) {
    public SyirkahCloseValidationPin {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(policySnapshotId);
        Objects.requireNonNull(contractScopeRevision);
        Objects.requireNonNull(expectedCalculationSetHash);
        Objects.requireNonNull(pinnedAt);
    }
}
```

The `correctionId` may be absent for an ordinary close if that is supported by the existing domain. Preserve the project's established representation for an optional correction instead of forcing a new convention.

### 2. Add a finalization consistency verifier

Immediately before committing finalization, verify that the pinned versions still match the authoritative state.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseValidationPinVerifier {

    Uni<SyirkahClosePinVerificationResult> verify(
        SyirkahCloseValidationPin pin
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public record SyirkahClosePinVerificationResult(
    boolean valid,
    List<String> violations
) {
    public SyirkahClosePinVerificationResult {
        violations = List.copyOf(violations);

        if (valid && !violations.isEmpty()) {
            throw new IllegalArgumentException(
                "A valid pin verification cannot contain violations"
            );
        }

        if (!valid && violations.isEmpty()) {
            throw new IllegalArgumentException(
                "An invalid pin verification must explain the mismatch"
            );
        }
    }
}
```

The verifier should check at least:

- The fiscal period and close cycle still refer to the intended close.
- The policy snapshot ID has not changed.
- The contract scope revision still matches.
- The expected calculation set hash is unchanged.
- Every required evidence link still references the same accepted evidence and result hash.
- The target snapshot and correction remain in the states expected by finalization.

Prefer locking or revision-based compare-and-set operations in the persistence layer over relying on a read-only check alone. Otherwise, a change could occur immediately after verification.

### 3. Make finalization an explicit transactional use case

The transaction coordinator below illustrates the sequence. Its repository and transaction APIs are ports to be adapted to the existing application; the code does not assume the current `CloseGateResult` constructor or database implementation.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import tech.kayys.syirkah.accounting.domain.close.*;

@ApplicationScoped
public class SyirkahCloseFinalizationService {

    private final SyirkahCloseFinalizationTransaction transaction;
    private final SyirkahCloseValidationPinFactory pinFactory;
    private final SyirkahCloseValidationPinVerifier pinVerifier;
    private final SyirkahCloseFinalizationOperations operations;

    public SyirkahCloseFinalizationService(
        SyirkahCloseFinalizationTransaction transaction,
        SyirkahCloseValidationPinFactory pinFactory,
        SyirkahCloseValidationPinVerifier pinVerifier,
        SyirkahCloseFinalizationOperations operations
    ) {
        this.transaction = transaction;
        this.pinFactory = pinFactory;
        this.pinVerifier = pinVerifier;
        this.operations = operations;
    }

    public Uni<SyirkahCloseFinalizationResult> finalizeClose(
        SyirkahCloseFinalizationCommand command
    ) {
        return transaction.execute(command, tx ->
            pinFactory.create(tx.context())
                .flatMap(pin ->
                    operations.validateAll(tx.context())
                        .flatMap(validation -> {
                            if (!validation.passed()) {
                                return Uni.createFrom().item(
                                    SyirkahCloseFinalizationResult.rejected(
                                        validation
                                    )
                                );
                            }

                            return pinVerifier.verify(pin)
                                .flatMap(verification -> {
                                    if (!verification.valid()) {
                                        return Uni.createFrom().item(
                                            SyirkahCloseFinalizationResult
                                                .conflict(
                                                    verification.violations()
                                                )
                                        );
                                    }

                                    return operations.finalizeState(
                                        tx,
                                        pin,
                                        validation
                                    );
                                });
                        })
                )
        );
    }
}
```

This proposal assumes the application can represent a rejected validation or concurrency conflict as a result. If the existing workflow uses exceptions for transaction rollback, use that established behavior instead.

The key point is that returning a rejection result must not accidentally commit partial mutations. The `validateAll` phase should be read-only, and `finalizeState` must only run after validation and pin verification succeed. If any earlier operation writes state, a rejected result must trigger rollback rather than commit.

### 4. Define the transaction contract and finalization operations

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import java.util.function.Function;

public interface SyirkahCloseFinalizationTransaction {

    Uni<SyirkahCloseFinalizationResult> execute(
        SyirkahCloseFinalizationCommand command,
        Function<SyirkahCloseFinalizationTransactionContext,
            Uni<SyirkahCloseFinalizationResult>> work
    );
}
```

```
package tech.kayys.syirkah.accounting.application.close;

import tech.kayys.syirkah.accounting.domain.close.CloseValidationContext;

public interface SyirkahCloseFinalizationTransactionContext {

    CloseValidationContext context();

    // Expose transaction-scoped repositories or a unit-of-work handle
    // through the project's existing transaction abstraction.
}
```

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

import tech.kayys.syirkah.accounting.domain.close.*;

public interface SyirkahCloseFinalizationOperations {

    Uni<SyirkahCloseFinalizationValidation> validateAll(
        CloseValidationContext context
    );

    Uni<SyirkahCloseFinalizationResult> finalizeState(
        SyirkahCloseFinalizationTransactionContext transaction,
        SyirkahCloseValidationPin pin,
        SyirkahCloseFinalizationValidation validation
    );
}
```

`finalizeState` must execute the existing state transitions in one database transaction:

1. Recheck and lock the fiscal period and target close records.
2. Verify the original and corrected snapshot lineage, if applicable.
3. Verify the pinned evidence and policy revisions.
4. Supersede the prior snapshot where the correction workflow requires it.
5. Activate the new snapshot.
6. Lock the fiscal period.
7. Mark the correction reclosed when applicable.
8. Append audit records and transactional outbox events.
9. Commit.

The precise ordering of snapshot and period updates should follow existing foreign-key and state-transition constraints. All these changes must remain atomic: no observer should see a partially finalized close.

### 5. Use optimistic concurrency or database locks

A practical implementation can use a revision column on mutable close-state records:

```
UPDATE fiscal_period
SET status = 'CLOSED',
    revision = revision + 1
WHERE tenant_id = :tenantId
  AND ledger_id = :ledgerId
  AND fiscal_period_id = :fiscalPeriodId
  AND revision = :expectedRevision
  AND status = 'CLOSING';
```

If the update affects zero rows, the state changed or no longer satisfies the preconditions. Abort the transaction and return a concurrency conflict; do not proceed with snapshot activation.

Use the project's existing schema and status names rather than applying this SQL literally. If the platform already uses row-level locking, aggregate version checks, or a dedicated close-state machine, extend that mechanism instead of adding a competing concurrency strategy.

A further distinction matters: the close-state revision alone does not protect the contract scope or policy snapshot. Those records need immutable versions or equivalent revision checks as well.

### 6. Publish events only after commit

Audit rows and outbox rows should be inserted within the finalization transaction. External publication should happen asynchronously after commit.

Useful event types include:

- `SyirkahCloseFinalized`
- `SyirkahCloseFinalizationRejected`
- `SyirkahCloseFinalizationConflict`

Do not publish `SyirkahCloseFinalized` before the database transaction commits. A rollback must not leave consumers believing the close succeeded. A rejected attempt can be recorded in a separate audit transaction if the main finalization transaction rolls back; that record should identify the attempt without implying that the close was finalized.

### 7. Tests for the finalization boundary

| Scenario                                            | Required result                                                       |
| --------------------------------------------------- | --------------------------------------------------------------------- |
| Evidence gate reports a missing calculation         | No snapshot or period-state changes                                   |
| Policy snapshot changes after pin creation          | Conflict; no finalization                                             |
| Contract scope revision changes                     | Conflict; validation must be retried against a newly pinned scope     |
| Concurrent finalization changes the period revision | One transaction wins; the stale transaction aborts                    |
| Snapshot activation fails                           | Prior snapshot and period state remain unchanged                      |
| Correction update fails                             | Snapshot changes roll back                                            |
| Transaction commits successfully                    | State, audit record, and outbox row commit together                   |
| Outbox publisher is temporarily unavailable         | Finalized state remains committed; event remains available for retry  |
| Same finalization command is retried                | Existing idempotency behavior prevents duplicate finalization effects |

Next slice: make finalization idempotent. Define a stable request identity and request hash, persist the outcome of each attempt, and ensure retries return the prior committed result rather than re-running snapshot transitions or duplicating audit and outbox records.

These are implementation proposals, not repository-verified patches; no compilation or tests have been run.


///////////////

## Next slice: idempotent close finalization

The goal is to make retries safe when a client times out, a connection drops after commit, or a worker repeats a command. A retry with the same request identity and same payload should return the existing outcome; the same identity with a different payload must be rejected.

This extends the current close workflow without changing Syirkah calculation or accounting semantics.

### 1. Define the idempotency record

Keep request identity separate from the close-cycle ID: one close cycle may receive multiple legitimate commands, but a single idempotency key must never represent two different requests.

```
package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;

public record SyirkahCloseFinalizationIdempotencyRecord(
    String tenantId,
    String ledgerId,
    String idempotencyKey,
    String requestHash,
    String closeCycleId,
    String status,
    String outcomeReference,
    Instant createdAt,
    Instant completedAt
) {}
```

Use explicit statuses rather than arbitrary strings in the implementation:

```
package tech.kayys.syirkah.accounting.domain.close;

public enum SyirkahCloseFinalizationRequestStatus {
    IN_PROGRESS,
    SUCCEEDED,
    REJECTED,
    CONFLICTED
}
```

`SUCCEEDED` means the close transaction committed. A validation rejection or concurrency conflict is not a successful finalization. Whether those outcomes are retained for retry is a deliberate API policy: deterministic validation failures can be recorded, while transient infrastructure failures should normally roll back the idempotency claim or leave a recoverable state.

### 2. Hash the semantic request, not the transport payload

The request hash should cover every field that changes the intended finalization, such as tenant, ledger, close cycle, fiscal period, target snapshot, correction ID, and policy snapshot. Exclude retry counters, timestamps generated per attempt, and tracing headers.

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahCloseFinalizationRequestIdentity(
    String tenantId,
    String ledgerId,
    String closeCycleId,
    String fiscalPeriodId,
    String snapshotId,
    String correctionId,
    String policySnapshotId
) {}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public interface SyirkahCloseFinalizationRequestHasher {

    String hash(SyirkahCloseFinalizationRequestIdentity identity);
}
```

Implement the hasher with a versioned canonical encoding and a cryptographic digest such as SHA-256. Include the encoding version in the hash input so future changes to canonicalization are explicit.

### 3. Add a persistence port with atomic claim semantics

A separate `find` followed by `insert` is not sufficient: two concurrent requests can both observe that the key is absent. The claim must be atomic under a unique constraint.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseFinalizationIdempotencyRepository {

    Uni<SyirkahCloseFinalizationClaimResult> claim(
        SyirkahCloseFinalizationIdempotencyRecord proposed
    );

    Uni<Void> markSucceeded(
        String tenantId,
        String ledgerId,
        String idempotencyKey,
        String requestHash,
        String outcomeReference
    );

    Uni<Void> markRejected(
        String tenantId,
        String ledgerId,
        String idempotencyKey,
        String requestHash,
        String outcomeReference
    );

    Uni<Void> markConflicted(
        String tenantId,
        String ledgerId,
        String idempotencyKey,
        String requestHash,
        String outcomeReference
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public record SyirkahCloseFinalizationClaimResult(
    SyirkahCloseFinalizationClaimStatus status,
    SyirkahCloseFinalizationIdempotencyRecord existingRecord
) {}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public enum SyirkahCloseFinalizationClaimStatus {
    CLAIMED,
    EXISTING,
    KEY_REUSED_WITH_DIFFERENT_REQUEST,
    IN_PROGRESS
}
```

Repository semantics:

- `CLAIMED`: this invocation acquired the key.
- `EXISTING`: a completed record already exists with the same request hash.
- `KEY_REUSED_WITH_DIFFERENT_REQUEST`: the key exists but its hash differs.
- `IN_PROGRESS`: another invocation currently owns the key and has not completed.

The persistence implementation should enforce a unique key on `(tenant_id, ledger_id, idempotency_key)`. The claim and the finalization transaction must share a carefully designed transaction boundary so a process crash cannot leave a permanently claimed key with no recovery path.

### 4. Make the idempotency record and close transition atomic

There are two valid implementation strategies:

- Single database transaction: insert the idempotency record, finalize the close, and mark the record successful in the same transaction. A crash rolls everything back. Concurrent duplicates wait on the unique key and then read the committed outcome.
- Durable claim with lease: commit an `IN_PROGRESS` claim first, then finalize in a second transaction. This requires lease expiry, ownership tokens, and recovery for abandoned claims.

For this close workflow, prefer the single-transaction approach if all relevant records share one transactional database. It avoids a durable orphaned claim and keeps the finalization result authoritative. In that approach, `IN_PROGRESS` may not be externally visible while the transaction is uncommitted; that is expected.

The core transaction should be conceptually:

```
BEGIN
  atomically claim idempotency key
  if same key + different request hash: reject
  if completed record exists: return its outcome without mutations
  lock close cycle, period, snapshot, and correction
  validate expected evidence and pinned revisions
  apply snapshot and period transitions
  append audit and outbox records
  mark idempotency record SUCCEEDED
COMMIT
```

If the key is already committed, return the recorded result. Do not rerun the state transitions. If the transaction fails unexpectedly, the claim and state changes roll back together.

### 5. Model the service result explicitly

The existing finalization result should be reusable when returning an earlier outcome. Avoid reconstructing a “successful” result from a status flag alone if the caller needs the actual finalized snapshot or audit reference.

```
package tech.kayys.syirkah.accounting.application.close;

public record SyirkahCloseFinalizationOutcome(
    String closeCycleId,
    String snapshotId,
    String outcomeReference,
    boolean finalized
) {}
```

The service should distinguish a newly performed finalization from a replay:

```
package tech.kayys.syirkah.accounting.application.close;

public record SyirkahIdempotentFinalizationResult(
    SyirkahCloseFinalizationOutcome outcome,
    boolean replayed
) {}
```

A replay returns `replayed = true` and the same committed outcome reference. A request-hash mismatch is an error, not a replay.

### 6. Integrate the key into the finalization use case

Add the idempotency key to the command received by the application layer. Validate that it is nonblank and within the API's configured length limit.

```
package tech.kayys.syirkah.accounting.application.close;

import java.util.Objects;

public record SyirkahCloseFinalizationCommand(
    String tenantId,
    String ledgerId,
    String closeCycleId,
    String fiscalPeriodId,
    String snapshotId,
    String correctionId,
    String policySnapshotId,
    String idempotencyKey,
    String requestedBy
) {
    public SyirkahCloseFinalizationCommand {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(policySnapshotId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(requestedBy);

        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                "Idempotency key must not be blank"
            );
        }
    }
}
```

A service-level flow can then be written around a transaction runner that provides the atomic claim and existing-outcome lookup. The exact `Uni` and transaction adapter signatures should match the project's actual transaction infrastructure.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import tech.kayys.syirkah.accounting.domain.close.*;

@ApplicationScoped
public class IdempotentSyirkahCloseFinalizer {

    private final SyirkahCloseFinalizationRequestHasher requestHasher;
    private final SyirkahCloseIdempotentTransaction transaction;

    public IdempotentSyirkahCloseFinalizer(
        SyirkahCloseFinalizationRequestHasher requestHasher,
        SyirkahCloseIdempotentTransaction transaction
    ) {
        this.requestHasher = requestHasher;
        this.transaction = transaction;
    }

    public Uni<SyirkahIdempotentFinalizationResult> finalizeClose(
        SyirkahCloseFinalizationCommand command
    ) {
        var identity = new SyirkahCloseFinalizationRequestIdentity(
            command.tenantId(),
            command.ledgerId(),
            command.closeCycleId(),
            command.fiscalPeriodId(),
            command.snapshotId(),
            command.correctionId(),
            command.policySnapshotId()
        );

        String requestHash = requestHasher.hash(identity);

        return transaction.executeAtomically(
            command,
            requestHash
        );
    }
}
```

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseIdempotentTransaction {

    Uni<SyirkahIdempotentFinalizationResult> executeAtomically(
        SyirkahCloseFinalizationCommand command,
        String requestHash
    );
}
```

The transaction adapter is responsible for the claim-and-replay decision, calling the existing validation pipeline, applying state changes, and committing the final outcome. Keeping those responsibilities in one transaction implementation prevents a controller retry from accidentally bypassing the validation gate.

### 7. Database constraints and recovery rules

A minimal relational table could look like this; adapt naming and types to the existing schema.

```
CREATE TABLE syirkah_close_finalization_idempotency (
    tenant_id          VARCHAR(128) NOT NULL,
    ledger_id          VARCHAR(128) NOT NULL,
    idempotency_key    VARCHAR(256) NOT NULL,
    request_hash       VARCHAR(128) NOT NULL,
    close_cycle_id     VARCHAR(128) NOT NULL,
    status             VARCHAR(32) NOT NULL,
    outcome_reference  VARCHAR(256),
    created_at         TIMESTAMP NOT NULL,
    completed_at       TIMESTAMP,
    PRIMARY KEY (tenant_id, ledger_id, idempotency_key)
);
```

Recommended persistence rules:

- Restrict `status` to the known enum values through a database constraint where supported.
- Set `completed_at` only for terminal outcomes.
- Do not overwrite `request_hash` when a key is reused.
- Store a durable outcome reference that resolves to the finalized snapshot or finalization record.
- Retain successful idempotency records for at least as long as clients may legitimately retry; do not purge them while their key could be reused.
- Keep the outbox event ID deterministic or uniquely constrained by the finalization record so retries cannot emit duplicate logical events.

If the existing platform has a standard idempotency facility, reuse it instead of adding a parallel table.

### 8. Tests to add

| Scenario                                         | Expected behavior                                                                               |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------- |
| Same key, same request after success             | Return original outcome; no repeated state transitions                                          |
| Same key, different snapshot ID                  | Reject key reuse                                                                                |
| Same key, different policy snapshot              | Reject key reuse                                                                                |
| Two concurrent identical requests                | One finalization; both observe the same committed outcome                                       |
| Process fails before commit                      | No finalization or successful idempotency record is committed                                   |
| Client times out after commit                    | Retry returns the committed outcome                                                             |
| Evidence gate rejects the close                  | No successful finalization record                                                               |
| Snapshot transition fails                        | Close state, idempotency success status, audit rows, and outbox rows roll back together         |
| Outbox publication is retried                    | No duplicate logical finalization event                                                         |
| Different keys target an already finalized close | Existing close-state rules reject or return the established outcome without repeating mutations |

One subtle point: the request hash should include every field that changes the intended close operation. If the close command later gains fields such as a target correction version or an explicit finalization mode, update the canonical identity and its versioned encoding accordingly.

Next slice: implement the transactional adapter and database-level concurrency tests against the project's actual persistence technology. The critical acceptance test is two concurrent requests using the same key and payload: there must be one committed finalization, one set of audit/outbox effects, and a consistent result for both callers.

These are proposed implementation slices; they have not been compiled or tested against the repository.

//////////////

## Next slice: transactional adapter and concurrent retry safety

Now connect the idempotency flow to the actual close transaction. The main invariant is:

For one `(tenantId, ledgerId, idempotencyKey)`, at most one finalization can commit, and every same-request retry resolves to that committed outcome.

The adapter must reuse the platform's existing transaction manager and repositories. The example below shows the required behavior independently of whether the implementation uses JTA, Hibernate Reactive, or another persistence layer.

### 1. Define the transaction result explicitly

A transactional handler should distinguish a newly finalized close from an already-completed request, a key conflict, and a validation rejection.

```
package tech.kayys.syirkah.accounting.application.close;

public enum SyirkahCloseIdempotencyDecision {
    EXECUTE,
    REPLAY,
    REJECT_KEY_REUSE,
    IN_PROGRESS
}
```

```
package tech.kayys.syirkah.accounting.application.close;

public record SyirkahCloseIdempotencyResolution(
    SyirkahCloseIdempotencyDecision decision,
    String existingRequestHash,
    String outcomeReference
) {}
```

```
package tech.kayys.syirkah.accounting.application.close;

public interface SyirkahCloseIdempotencyClaimStore {

    /*
     * Must run in the caller's transaction.
     * The database unique constraint is the concurrency authority.
     */
    SyirkahCloseIdempotencyResolution claimOrResolve(
        SyirkahCloseFinalizationCommand command,
        String requestHash
    );

    void completeSuccessfully(
        SyirkahCloseFinalizationCommand command,
        String requestHash,
        String outcomeReference
    );
}
```

These methods are intentionally synchronous ports because many JTA transaction adapters are synchronous. If the existing platform uses a reactive transaction manager, expose reactive methods instead—but retain the same atomicity contract.

### 2. Implement the transaction handler

This pseudocode shows the essential transaction flow. `CloseFinalizationUnitOfWork` represents the existing transaction-scoped repositories and operations; adapt it to the real persistence stack.

```
package tech.kayys.syirkah.accounting.application.close;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SyirkahCloseIdempotentTransactionHandler {

    private final SyirkahCloseIdempotencyClaimStore claimStore;
    private final SyirkahCloseFinalizationRequestHasher requestHasher;
    private final CloseFinalizationUnitOfWork unitOfWork;

    public SyirkahCloseIdempotentTransactionHandler(
        SyirkahCloseIdempotencyClaimStore claimStore,
        SyirkahCloseFinalizationRequestHasher requestHasher,
        CloseFinalizationUnitOfWork unitOfWork
    ) {
        this.claimStore = claimStore;
        this.requestHasher = requestHasher;
        this.unitOfWork = unitOfWork;
    }

    @Transactional
    public SyirkahCloseFinalizationOutcome finalizeAtomically(
        SyirkahCloseFinalizationCommand command
    ) {
        String requestHash = requestHasher.hash(
            new SyirkahCloseFinalizationRequestIdentity(
                command.tenantId(),
                command.ledgerId(),
                command.closeCycleId(),
                command.fiscalPeriodId(),
                command.snapshotId(),
                command.correctionId(),
                command.policySnapshotId()
            )
        );

        SyirkahCloseIdempotencyResolution resolution =
            claimStore.claimOrResolve(command, requestHash);

        return switch (resolution.decision()) {
            case REPLAY -> {
                if (!requestHash.equals(resolution.existingRequestHash())) {
                    throw new SyirkahCloseIdempotencyConflictException();
                }
                yield unitOfWork.loadOutcome(
                    command.tenantId(),
                    command.ledgerId(),
                    resolution.outcomeReference()
                );
            }

            case REJECT_KEY_REUSE ->
                throw new SyirkahCloseIdempotencyConflictException();

            case IN_PROGRESS ->
                throw new SyirkahCloseRequestInProgressException();

            case EXECUTE -> executeNewFinalization(command, requestHash);
        };
    }

    private SyirkahCloseFinalizationOutcome executeNewFinalization(
        SyirkahCloseFinalizationCommand command,
        String requestHash
    ) {
        unitOfWork.lockCloseState(command);

        var validation = unitOfWork.validateClose(command);
        if (!validation.passed()) {
            throw new SyirkahCloseValidationException(validation);
        }

        var pin = unitOfWork.createValidationPin(command);
        unitOfWork.verifyValidationPin(pin);

        SyirkahCloseFinalizationOutcome outcome =
            unitOfWork.applyFinalization(command, pin, validation);

        unitOfWork.appendFinalizationAudit(command, outcome);
        unitOfWork.appendFinalizationOutbox(command, outcome);

        claimStore.completeSuccessfully(
            command,
            requestHash,
            outcome.outcomeReference()
        );

        return outcome;
    }
}
```

The exception classes and `CloseFinalizationUnitOfWork` are application-level placeholders. Use the project's existing exception hierarchy, validation pipeline, transaction-scoped repositories, and result types.

Important transaction detail: the database adapter must not catch a persistence exception and continue inside a transaction that has already been marked rollback-only. A unique-key race may require retrying the entire transaction in a fresh transaction, then reading the winning request's committed record.

### 3. Handle the unique-key race correctly

A common implementation error is to query for a key, see no record, and then insert. Two concurrent transactions can both observe absence.

Instead, rely on the database's unique constraint:

```
PRIMARY KEY (tenant_id, ledger_id, idempotency_key)
```

The claim operation must behave as follows:

1. Attempt to insert the idempotency row inside the transaction.
2. If insertion succeeds, this transaction owns the request.
3. If the unique constraint is violated, do not continue finalization in the failed transaction.
4. Start or use a fresh transaction to read the existing row.
5. Compare request hashes:
   - Same hash and terminal success: return the original outcome.
   - Different hash: reject key reuse.
   - Same hash and an uncommitted competing request: wait or retry according to the database and transaction policy.
   - A durable `IN_PROGRESS` row: use the lease/recovery protocol if the platform chose a two-transaction claim design.

For the preferred single-transaction design, the inserted claim and the finalization commit together. A competing insert normally waits for the first transaction to commit or roll back. If the first commits, the second reads the committed outcome; if the first rolls back, the second can claim and execute.

Do not implement this behavior by catching a constraint violation and continuing with the same transaction unless the persistence technology explicitly guarantees that the transaction remains usable.

### 4. Make the outcome lookup safe

The idempotency record should point to an immutable finalization outcome or a durable finalization identifier—not just a status string.

```
package tech.kayys.syirkah.accounting.application.close;

public interface CloseFinalizationUnitOfWork {

    void lockCloseState(SyirkahCloseFinalizationCommand command);

    SyirkahCloseFinalizationValidation validateClose(
        SyirkahCloseFinalizationCommand command
    );

    SyirkahCloseValidationPin createValidationPin(
        SyirkahCloseFinalizationCommand command
    );

    void verifyValidationPin(SyirkahCloseValidationPin pin);

    SyirkahCloseFinalizationOutcome applyFinalization(
        SyirkahCloseFinalizationCommand command,
        SyirkahCloseValidationPin pin,
        SyirkahCloseFinalizationValidation validation
    );

    void appendFinalizationAudit(
        SyirkahCloseFinalizationCommand command,
        SyirkahCloseFinalizationOutcome outcome
    );

    void appendFinalizationOutbox(
        SyirkahCloseFinalizationCommand command,
        SyirkahCloseFinalizationOutcome outcome
    );

    SyirkahCloseFinalizationOutcome loadOutcome(
        String tenantId,
        String ledgerId,
        String outcomeReference
    );
}
```

The `loadOutcome` implementation must enforce tenant and ledger scope. It must not reconstruct success merely because the idempotency row says `SUCCEEDED`; the referenced finalization record and snapshot must still be resolvable.

### 5. Add database-level concurrency tests

Use the real database engine used by the project for these tests where possible. In-memory substitutes may not reproduce unique-key waiting, isolation, or transaction-abort behavior accurately.

| Test                                               | Assertions                                                                                |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Two concurrent requests, same key and hash         | Exactly one finalization, one logical audit outcome, one logical outbox event             |
| First request commits, response is lost            | Retry returns the original snapshot and outcome reference                                 |
| First request rolls back                           | No successful claim or partial close-state mutation remains; retry can proceed            |
| Same key, different request hash                   | Conflict; no second finalization                                                          |
| Different keys, same close cycle                   | Existing close-state lock/state machine prevents duplicate transition                     |
| Evidence gate fails                                | No activated snapshot, locked period, or successful idempotency record                    |
| Failure during outbox insert                       | Entire finalization transaction rolls back                                                |
| Tenant A reuses Tenant B's key                     | No cross-tenant outcome disclosure                                                        |
| Duplicate request arrives during first transaction | It waits/retries and then observes the committed outcome, or safely claims after rollback |

A useful integration test launches two independent transactions behind a barrier so both attempt the same key at nearly the same time. After both finish, assert that there is one finalized snapshot transition and that both callers resolve to the same durable outcome.

### 6. One domain boundary to preserve

Idempotency protects a request, not every possible command targeting a close cycle. Two different idempotency keys can still target the same cycle, so the existing close state machine and database locking must independently prevent duplicate finalization. Conversely, a retry with the same key must not bypass evidence validation if the original transaction never committed.

This gives two complementary protections:

- Idempotency key: prevents the same logical request from being applied twice.
- Close-state concurrency control: prevents conflicting requests from applying incompatible state transitions.

The next slice is to make the finalization audit evidence self-contained: record the expected calculation-set hash, validation findings, policy and contract revisions, and idempotency outcome so an auditor can reconstruct why a close was accepted or rejected.

These are proposed changes, not repository-verified code; compilation and tests have not been run.

////////////

## Next slice: immutable close-finalization audit evidence

The finalization audit should preserve the exact evidence that supported a close decision—not just a message saying “close completed.” This makes it possible to explain later which calculation set was expected, which evidence was checked, which policy and contract revisions were used, and whether the request was a new finalization or an idempotent replay.

Keep this as an extension of the existing audit/outbox and evidence-export infrastructure. Do not create a competing audit subsystem.

### 1. Define a finalization audit record

The audit record captures the decision and references its supporting artifacts. Large evidence graphs and calculation payloads should remain in their existing immutable evidence stores, referenced by stable identifiers and hashes.

```
package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;
import java.util.Objects;

public record SyirkahCloseFinalizationAuditEvidence(
    String auditId,
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String closeCycleId,
    String snapshotId,
    String correctionId,
    String policySnapshotId,
    String contractScopeRevision,
    String expectedCalculationSetHash,
    String reconciliationReportId,
    String reconciliationReportHash,
    String validationOutcome,
    String finalizationOutcome,
    String idempotencyKeyHash,
    String requestHash,
    String actorId,
    Instant recordedAt,
    String schemaVersion
) {
    public SyirkahCloseFinalizationAuditEvidence {
        Objects.requireNonNull(auditId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(policySnapshotId);
        Objects.requireNonNull(contractScopeRevision);
        Objects.requireNonNull(expectedCalculationSetHash);
        Objects.requireNonNull(validationOutcome);
        Objects.requireNonNull(finalizationOutcome);
        Objects.requireNonNull(requestHash);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(schemaVersion);
    }
}
```

Keep the raw idempotency key out of audit exports where it is not needed. A stable hash or internal request reference is usually enough to correlate retries without exposing the client-supplied key.

`reconciliationReportId` and `reconciliationReportHash` may be absent only if the existing workflow explicitly supports a finalization path without that report. For a Syirkah close requiring calculation evidence, the successful audit record should reference the report used by the validation gate.

### 2. Preserve the validation findings used for the decision

Do not regenerate the findings later and assume they are identical to the original validation. Persist the original reconciliation report—or an immutable reference to it—and its canonical hash.

```
package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;
import java.util.List;

public record SyirkahCloseValidationEvidence(
    String reportId,
    String tenantId,
    String ledgerId,
    String closeCycleId,
    String expectedCalculationSetHash,
    String contractScopeRevision,
    String policySnapshotId,
    String resolverVersion,
    String reconcilerVersion,
    int expectedCount,
    int linkedCount,
    int matchedCount,
    List<SyirkahCloseEvidenceFinding> findings,
    boolean passed,
    Instant validatedAt,
    String reportHash
) {
    public SyirkahCloseValidationEvidence {
        findings = List.copyOf(findings);
    }
}
```

Add explicit versions for the resolver, reconciler, and report schema. This helps distinguish a historical validation from one performed under newer logic.

The report hash should cover the complete canonical report, including sorted findings and the outcome. It proves that the stored report has not changed relative to the hash; it does not independently prove that the original evidence or calculation was substantively correct.

### 3. Add an append-only audit repository

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseFinalizationAuditRepository {

    Uni<Void> appendWithinTransaction(
        SyirkahCloseFinalizationAuditEvidence auditEvidence
    );

    Uni<Void> appendValidationEvidenceWithinTransaction(
        SyirkahCloseValidationEvidence validationEvidence
    );

    Uni<SyirkahCloseFinalizationAuditEvidence> findByAuditId(
        String tenantId,
        String ledgerId,
        String auditId
    );
}
```

Persistence requirements:

- Do not expose update or delete methods for finalized audit evidence.
- Enforce uniqueness on `auditId` and the relevant tenant/ledger scope.
- Store the audit evidence and successful finalization state in the same transaction.
- Use database permissions or append-only storage controls to prevent ordinary application roles from editing historical audit rows.
- Record corrections and later supersession as new events; do not rewrite the original finalization evidence.

For a rejected validation that rolls back the finalization transaction, record the failed attempt through the platform's separate audit path after rollback. The failure record must not claim that a snapshot was activated or that the period was closed.

### 4. Attach audit evidence to the finalization transaction

Extend the existing finalization unit of work so it builds audit evidence from the exact validation result and pin used by that transaction.

```
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

import tech.kayys.syirkah.accounting.domain.close.*;

public interface SyirkahCloseFinalizationAuditEvidenceFactory {

    Uni<SyirkahCloseFinalizationAuditEvidence> create(
        SyirkahCloseFinalizationCommand command,
        SyirkahCloseValidationPin pin,
        SyirkahCloseFinalizationValidation validation,
        SyirkahCloseFinalizationOutcome outcome,
        String requestHash
    );
}
```

The successful finalization sequence should now be:

1. Claim the idempotency key in the transaction.
2. Lock the close state and pin the required revisions.
3. Run the close validation pipeline, including expected-calculation reconciliation.
4. Verify that pinned revisions remain valid.
5. Apply the snapshot, period, and correction state transitions.
6. Persist the immutable validation report or its durable reference.
7. Append finalization audit evidence.
8. Append the outbox event.
9. Mark the idempotency record successful.
10. Commit.

All steps that modify the close state, audit evidence, idempotency outcome, and outbox must commit atomically when they share the same database. External event publication remains asynchronous after commit.

### 5. Use a versioned outbox event

The event should carry identifiers and hashes needed for correlation, rather than duplicating full calculation payloads.

```
package tech.kayys.syirkah.accounting.application.close;

import java.time.Instant;

public record SyirkahCloseFinalizedEvent(
    String eventId,
    String schemaVersion,
    String tenantId,
    String ledgerId,
    String fiscalPeriodId,
    String closeCycleId,
    String snapshotId,
    String correctionId,
    String policySnapshotId,
    String expectedCalculationSetHash,
    String reconciliationReportId,
    String reconciliationReportHash,
    String finalizationAuditId,
    String requestHash,
    Instant occurredAt
) {}
```

Use a deterministic event identity or a database uniqueness constraint so a retry cannot create a second logical `SyirkahCloseFinalizedEvent` for the same committed finalization. Consumers should also be idempotent.

Avoid publishing sensitive partner-level data in the event unless consumers genuinely require it. The evidence package can resolve the relevant data under its own authorization controls.

### 6. Add an audit-evidence integrity verifier

This verifier checks the stored report and finalization record before they are included in an audit export.

```
package tech.kayys.syirkah.accounting.domain.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseFinalizationAuditVerifier {

    Uni<SyirkahCloseAuditVerificationResult> verify(
        String tenantId,
        String ledgerId,
        String auditId
    );
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

import java.util.List;

public record SyirkahCloseAuditVerificationResult(
    SyirkahCloseAuditVerificationStatus status,
    List<String> findings
) {
    public SyirkahCloseAuditVerificationResult {
        findings = List.copyOf(findings);
    }
}
```

```
package tech.kayys.syirkah.accounting.domain.close;

public enum SyirkahCloseAuditVerificationStatus {
    VERIFIED,
    VERIFIED_WITH_LIMITATIONS,
    INVALID,
    INDETERMINATE
}
```

The verifier should check:

- The audit record and referenced validation report exist within the requested tenant and ledger.
- The canonical report hash matches the stored report.
- The expected calculation-set hash and policy snapshot agree across the pin, report, and audit record.
- The referenced snapshot and correction lineage match the recorded finalization outcome.
- The audit record, finalization record, and outbox event refer to the same close operation.
- The historical evidence remains available, or its absence is explicitly reported.

If a referenced artifact is missing, do not substitute the latest version. Return `INDETERMINATE` or `VERIFIED_WITH_LIMITATIONS`, depending on what can still be established. A matching hash establishes integrity against the recorded digest, not the truth of the underlying accounting or Sharia conclusion.

### 7. Tests for audit evidence

| Scenario                              | Expected behavior                                                                      |
| ------------------------------------- | -------------------------------------------------------------------------------------- |
| Successful close                      | Audit evidence, validation report reference, and outbox event commit with the close    |
| Audit insert fails                    | Entire close transaction rolls back                                                    |
| Validation rejects close              | Failed attempt is auditable without recording a successful finalization                |
| Historical policy artifact is missing | Verification reports the limitation; it does not substitute current policy             |
| Report contents are altered           | Hash verification returns `INVALID`                                                    |
| Report is unavailable                 | Verification returns an indeterminate or limited result                                |
| Close is corrected later              | Original audit remains unchanged; new audit references the correction and supersession |
| Same idempotency request is replayed  | Existing finalization audit is referenced; no duplicate finalization audit is created  |

Next slice: extend the existing audit evidence export so it packages the close-validation report, finalization audit, policy and contract revision references, and outbox event into one verifiable evidence bundle with a manifest and integrity checks.

These are proposed implementation changes, not repository-verified code. No compilation or tests have been run.

//////////////

The next slice extends the existing audit-evidence export to include the close-validation report, finalization audit, pinned policy and contract-scope revisions, and finalization outbox event in one verifiable bundle. It builds on the existing export pipeline rather than introducing a separate audit system.

The bundle must preserve historical references as they were at finalization time. Missing artifacts are reported as missing; the exporter must not silently substitute current policy, contract, or validation data.

## 1. Define the finalization evidence bundle

The export should package the original audit artifacts and their references, not recompute the close decision during export.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SyirkahCloseFinalizationEvidenceBundle(
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String correctionId,
        String finalizationAuditId,
        String validationReportId,
        String outboxEventId,
        String policySnapshotId,
        String contractScopeRevision,
        String expectedCalculationSetHash,
        String validationReportHash,
        String finalizationRequestHash,
        List<SyirkahFinalizationArtifactReference> artifacts,
        List<SyirkahFinalizationEvidenceFinding> findings,
        boolean complete,
        Instant assembledAt
) {
    public SyirkahCloseFinalizationEvidenceBundle {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(fiscalPeriodId);
        Objects.requireNonNull(closeCycleId);
        Objects.requireNonNull(finalizationAuditId);
        Objects.requireNonNull(artifacts);
        Objects.requireNonNull(findings);
        Objects.requireNonNull(assembledAt);

        artifacts = List.copyOf(artifacts);
        findings = List.copyOf(findings);
    }
}
```

Artifact references identify each record included in the package. A reference should include the artifact’s identity, type, content hash, schema version, and availability state.

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahFinalizationArtifactReference(
        String artifactType,
        String artifactId,
        String schemaVersion,
        String contentHash,
        ArtifactAvailability availability,
        String sourceRevision
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum ArtifactAvailability {
    INCLUDED,
    REFERENCED_ONLY,
    MISSING,
    ACCESS_DENIED,
    UNSUPPORTED_VERSION
}
```

`INCLUDED` means the artifact’s content is part of the export. `REFERENCED_ONLY` means the bundle identifies it but does not contain the content itself. That distinction prevents a manifest from implying that an auditor has received evidence they cannot actually inspect.

## 2. Define explicit findings

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahFinalizationEvidenceFindingCode {
    FINALIZATION_AUDIT_MISSING,
    VALIDATION_REPORT_MISSING,
    OUTBOX_EVENT_MISSING,
    POLICY_SNAPSHOT_MISSING,
    CONTRACT_SCOPE_REVISION_MISSING,
    EXPECTED_SET_HASH_MISMATCH,
    VALIDATION_REPORT_HASH_MISMATCH,
    REQUEST_HASH_MISMATCH,
    TENANT_OR_LEDGER_MISMATCH,
    SNAPSHOT_LINEAGE_MISMATCH,
    CORRECTION_LINEAGE_MISMATCH,
    ARTIFACT_CONTENT_UNAVAILABLE,
    UNSUPPORTED_ARTIFACT_SCHEMA,
    ARTIFACT_HASH_MISMATCH
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahFinalizationEvidenceFinding(
        SyirkahFinalizationEvidenceFindingCode code,
        String artifactType,
        String artifactId,
        String detail,
        boolean blocking
) {
}
```

Findings should be deterministic: sort by finding code, artifact type, and artifact ID before hashing or serializing the bundle. Avoid including sensitive partner or account data in free-text `detail` fields.

## 3. Add an artifact collector port

The collector retrieves immutable artifacts through existing repositories or adapters. It must not assume every artifact lives in the same module or database.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahCloseFinalizationArtifactReader {

    Optional<FinalizationAuditArtifact> findFinalizationAudit(
            String tenantId,
            String ledgerId,
            String auditId
    );

    Optional<ValidationReportArtifact> findValidationReport(
            String tenantId,
            String ledgerId,
            String reportId
    );

    Optional<OutboxEventArtifact> findOutboxEvent(
            String tenantId,
            String ledgerId,
            String eventId
    );

    Optional<PolicySnapshotArtifact> findPolicySnapshot(
            String tenantId,
            String ledgerId,
            String policySnapshotId
    );

    Optional<ContractScopeRevisionArtifact> findContractScopeRevision(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String revision
    );
}
```

The artifact wrappers below represent immutable, canonicalized content obtained from the relevant persistence adapter. Their concrete fields should be mapped from the project’s existing records rather than duplicating domain models.

```
package tech.kayys.syirkah.accounting.application.audit;

public record FinalizationAuditArtifact(
        String artifactId,
        String schemaVersion,
        String contentHash,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String correctionId,
        String validationReportId,
        String validationReportHash,
        String outboxEventId,
        String policySnapshotId,
        String contractScopeRevision,
        String expectedCalculationSetHash,
        String requestHash,
        String finalizationOutcome
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record ValidationReportArtifact(
        String artifactId,
        String schemaVersion,
        String contentHash,
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String policySnapshotId,
        String contractScopeRevision,
        String expectedCalculationSetHash,
        boolean passed
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record OutboxEventArtifact(
        String artifactId,
        String schemaVersion,
        String contentHash,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String correctionId,
        String policySnapshotId,
        String expectedCalculationSetHash,
        String validationReportId,
        String finalizationAuditId,
        String requestHash
) {
}
```

`PolicySnapshotArtifact` and `ContractScopeRevisionArtifact` should expose the stored canonical content or an immutable, verifiable content reference and hash. Do not reconstruct an old policy from today’s policy configuration.

## 4. Validate cross-artifact consistency

Existence and individual hashes are not enough. The exporter should verify that the artifacts all describe the same finalization.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SyirkahFinalizationEvidenceConsistencyChecker {

    public List<SyirkahFinalizationEvidenceFinding> check(
            FinalizationAuditArtifact audit,
            ValidationReportArtifact report,
            OutboxEventArtifact event
    ) {
        List<SyirkahFinalizationEvidenceFinding> findings =
                new ArrayList<>();

        requireEqual(
                findings,
                "tenantId",
                audit.tenantId(),
                report.tenantId()
        );
        requireEqual(
                findings,
                "ledgerId",
                audit.ledgerId(),
                report.ledgerId()
        );
        requireEqual(
                findings,
                "closeCycleId",
                audit.closeCycleId(),
                report.closeCycleId()
        );
        requireEqual(
                findings,
                "policySnapshotId",
                audit.policySnapshotId(),
                report.policySnapshotId()
        );
        requireEqual(
                findings,
                "contractScopeRevision",
                audit.contractScopeRevision(),
                report.contractScopeRevision()
        );
        requireEqual(
                findings,
                "expectedCalculationSetHash",
                audit.expectedCalculationSetHash(),
                report.expectedCalculationSetHash()
        );
        requireEqual(
                findings,
                "validationReportHash",
                audit.validationReportHash(),
                report.contentHash()
        );

        requireEqual(
                findings,
                "finalizationAuditId",
                audit.artifactId(),
                event.finalizationAuditId()
        );
        requireEqual(
                findings,
                "validationReportId",
                audit.validationReportId(),
                event.validationReportId()
        );
        requireEqual(
                findings,
                "requestHash",
                audit.requestHash(),
                event.requestHash()
        );
        requireEqual(
                findings,
                "snapshotId",
                audit.snapshotId(),
                event.snapshotId()
        );
        requireEqual(
                findings,
                "policySnapshotId",
                audit.policySnapshotId(),
                event.policySnapshotId()
        );
        requireEqual(
                findings,
                "expectedCalculationSetHash",
                audit.expectedCalculationSetHash(),
                event.expectedCalculationSetHash()
        );

        if (!report.passed()
                && "SUCCEEDED".equals(audit.finalizationOutcome())) {
            findings.add(new SyirkahFinalizationEvidenceFinding(
                    SyirkahFinalizationEvidenceFindingCode
                            .EXPECTED_SET_HASH_MISMATCH,
                    "VALIDATION_REPORT",
                    report.artifactId(),
                    "The audit records success but the linked validation report did not pass.",
                    true
            ));
        }

        return findings.stream()
                .sorted(java.util.Comparator
                        .comparing((SyirkahFinalizationEvidenceFinding f) ->
                                f.code().name())
                        .thenComparing(f -> Objects.toString(
                                f.artifactType(), ""))
                        .thenComparing(f -> Objects.toString(
                                f.artifactId(), "")))
                .toList();
    }

    private static void requireEqual(
            List<SyirkahFinalizationEvidenceFinding> findings,
            String field,
            String actual,
            String expected
    ) {
        if (!Objects.equals(actual, expected)) {
            findings.add(new SyirkahFinalizationEvidenceFinding(
                    SyirkahFinalizationEvidenceFindingCode
                            .EXPECTED_SET_HASH_MISMATCH,
                    "CROSS_ARTIFACT",
                    field,
                    "The linked artifacts disagree on " + field + ".",
                    true
            ));
        }
    }
}
```

Implementation note: the sample uses one generic mismatch code in `requireEqual` to keep the example compact. In production, map each mismatch to its specific finding code—for example, `REQUEST_HASH_MISMATCH`, `SNAPSHOT_LINEAGE_MISMATCH`, or `TENANT_OR_LEDGER_MISMATCH`. The check should also validate the policy and contract-scope artifacts, and recompute hashes from canonical content where the export contains that content.

## 5. Extend the export manifest

The manifest is the verifiable inventory of the bundle. It should record the export schema and hash algorithm, artifact counts, completeness, and the finalization identifiers.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahFinalizationEvidenceManifest(
        String manifestVersion,
        String hashAlgorithm,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String finalizationAuditId,
        String validationReportId,
        String outboxEventId,
        String policySnapshotId,
        String contractScopeRevision,
        String expectedCalculationSetHash,
        int artifactCount,
        int includedArtifactCount,
        int missingArtifactCount,
        boolean complete,
        List<SyirkahFinalizationArtifactReference> artifacts,
        List<SyirkahFinalizationEvidenceFinding> findings,
        String bundleContentHash,
        Instant generatedAt
) {
    public SyirkahFinalizationEvidenceManifest {
        artifacts = List.copyOf(artifacts);
        findings = List.copyOf(findings);
    }
}
```

Use a canonical serialization format and a versioned hashing specification. For example, the manifest’s content hash can be calculated over a canonical representation that excludes the `bundleContentHash` field itself. Define ordering, null handling, string encoding, timestamp representation, and numeric formatting explicitly; otherwise, logically identical manifests may hash differently.

The manifest hash protects integrity of the exported content. It does not prove that the underlying accounting result or Sharia interpretation was substantively correct.

## 6. Add an export orchestration service

The application service should:

1. Load the finalization audit within the tenant and ledger scope.
2. Resolve the report, outbox event, policy snapshot, and contract-scope revision referenced by that audit.
3. Collect artifact references and content hashes.
4. Check availability, schema compatibility, and cross-artifact consistency.
5. Build the manifest and calculate the bundle hash.
6. Persist the immutable export package using the existing export lifecycle.
7. Return the package status and findings to the caller.

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahCloseFinalizationEvidenceExporter {

    SyirkahFinalizationEvidenceExportResult export(
            SyirkahCloseFinalizationEvidenceExportCommand command
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahCloseFinalizationEvidenceExportCommand(
        String tenantId,
        String ledgerId,
        String finalizationAuditId,
        String requestedBy,
        String idempotencyKey
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahFinalizationEvidenceExportResult(
        String exportId,
        SyirkahFinalizationExportStatus status,
        String manifestHash,
        boolean complete,
        List<SyirkahFinalizationEvidenceFinding> findings
) {
    public SyirkahFinalizationEvidenceExportResult {
        findings = List.copyOf(findings);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahFinalizationExportStatus {
    READY,
    READY_WITH_LIMITATIONS,
    FAILED,
    EXPIRED
}
```

Map these statuses to the existing export lifecycle rather than introducing a parallel lifecycle in persistence. In particular, a missing historical policy artifact should produce a visible limitation or failure according to the evidence policy; it must not be filled with the current policy.

## 7. Required acceptance tests

| Scenario                                         | Expected result                                            |
| ------------------------------------------------ | ---------------------------------------------------------- |
| All artifacts exist and cross-references match   | `READY`, complete bundle                                   |
| Validation report is missing                     | Explicit finding; never substitute another report          |
| Report content no longer matches its stored hash | Integrity failure                                          |
| Audit and outbox refer to different snapshots    | Blocking lineage finding                                   |
| Historical policy snapshot is unavailable        | Explicit limitation or failure                             |
| Artifact belongs to another tenant or ledger     | Reject the artifact; no cross-tenant leakage               |
| Artifact schema is unsupported                   | Explicit unsupported-version finding                       |
| Same idempotency key and same request            | Return the existing export outcome                         |
| Same idempotency key and different request       | Reject key reuse                                           |
| Export is retried after partial failure          | No duplicate logical export or altered historical evidence |

The final bundle should be immutable after it becomes ready. If an artifact is later found to be incorrect or incomplete, create a new export version or correction record linked to the prior export; do not silently rewrite the package that was previously delivered.

Next slice: add a standalone verifier that accepts an exported package and manifest, checks the canonical bundle hash and each artifact hash, verifies cross-artifact links, and returns `VERIFIED`, `VERIFIED_WITH_LIMITATIONS`, `INVALID`, or `INDETERMINATE` without requiring the original accounting application to rerun the close.


/////////////



## Next slice: standalone evidence-bundle verifier

This verifier operates on an already-exported bundle. It checks the manifest and artifact contents, confirms that the finalization audit, validation report, and outbox event agree, and reports limitations without rerunning the accounting close or posting journals.

The key distinction is between integrity (whether the package matches its recorded hashes) and evidentiary completeness (whether the package contains enough historical material to verify the recorded decision).

### 1. Define verification outcomes

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceVerificationStatus {
    VERIFIED,
    VERIFIED_WITH_LIMITATIONS,
    INVALID,
    INDETERMINATE
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceVerificationFindingCode {
    MANIFEST_MISSING,
    MANIFEST_HASH_INVALID,
    BUNDLE_HASH_INVALID,
    ARTIFACT_MISSING,
    ARTIFACT_HASH_INVALID,
    ARTIFACT_CONTENT_UNREADABLE,
    UNSUPPORTED_SCHEMA,
    CROSS_REFERENCE_MISMATCH,
    TENANT_SCOPE_MISMATCH,
    LEDGER_SCOPE_MISMATCH,
    REQUIRED_POLICY_EVIDENCE_MISSING,
    REQUIRED_VALIDATION_EVIDENCE_MISSING,
    REQUIRED_FINALIZATION_AUDIT_MISSING,
    REQUIRED_OUTBOX_EVENT_MISSING,
    HISTORICAL_EVIDENCE_UNAVAILABLE,
    VERIFICATION_RULES_UNAVAILABLE
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceVerificationFinding(
        SyirkahEvidenceVerificationFindingCode code,
        String artifactType,
        String artifactId,
        String detail,
        boolean invalidatesIntegrity,
        boolean limitsVerification
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahEvidenceVerificationResult(
        String exportId,
        String manifestHash,
        SyirkahEvidenceVerificationStatus status,
        boolean integrityVerified,
        boolean crossReferencesVerified,
        boolean historicalEvidenceComplete,
        List<SyirkahEvidenceVerificationFinding> findings,
        Instant verifiedAt,
        String verifierVersion
) {
    public SyirkahEvidenceVerificationResult {
        findings = List.copyOf(findings);
    }
}
```

The result keeps integrity, cross-reference consistency, and historical completeness separate. A bundle may have intact hashes while still lacking the original policy snapshot needed to inspect the historical decision.

### 2. Define the verification input

The verifier should accept a bundle reader abstraction, not reach into accounting repositories or call the close engine.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceBundleReader {

    Optional<SyirkahEvidenceBundleContent> read(String exportId);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahEvidenceBundleContent(
        String exportId,
        SyirkahFinalizationEvidenceManifest manifest,
        String storedManifestHash,
        String storedBundleContentHash,
        List<SyirkahEvidenceArtifactContent> artifacts
) {
    public SyirkahEvidenceBundleContent {
        artifacts = List.copyOf(artifacts);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceArtifactContent(
        String artifactType,
        String artifactId,
        String schemaVersion,
        String declaredContentHash,
        String canonicalContent
) {
}
```

`canonicalContent` represents the exact canonical payload covered by the artifact hash. If the storage adapter returns only a reference and not the content, the verifier must classify that artifact as reference-only and limit the result accordingly.

### 3. Use a versioned canonical hashing port

Do not use `Object.toString()`, default JSON serialization, or unordered map serialization as a canonical hash input.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceCanonicalHasher {

    String algorithm();

    Optional<String> hashCanonicalManifest(
            SyirkahFinalizationEvidenceManifest manifest
    );

    Optional<String> hashArtifact(
            String artifactType,
            String schemaVersion,
            String canonicalContent
    );

    Optional<String> hashBundleContent(
            SyirkahFinalizationEvidenceManifest manifest,
            String manifestHash,
            java.util.List<SyirkahEvidenceArtifactContent> artifacts
    );
}
```

The implementation must document the canonicalization rules and support the manifest’s declared version. For example, a SHA-256 implementation can be used, but only after canonical serialization has been specified. A hash generated using a different canonicalization version must not be treated as comparable merely because the hash algorithm is the same.

### 4. Define a versioned cross-reference checker

Reuse the consistency checks from the exporter where practical, but expose a verifier-specific port so exported artifacts can be checked independently.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahEvidenceCrossReferenceChecker {

    List<SyirkahEvidenceVerificationFinding> check(
            SyirkahFinalizationEvidenceManifest manifest,
            List<SyirkahEvidenceArtifactContent> artifacts
    );
}
```

At minimum, check these links:

- Finalization audit → validation report ID and hash.
- Finalization audit → policy snapshot ID and contract-scope revision.
- Finalization audit → expected calculation set hash.
- Outbox event → finalization audit ID, validation report ID, request hash, and snapshot ID.
- Every artifact → manifest tenant and ledger scope.
- Manifest artifact reference → artifact type, ID, schema version, and content hash.

A missing reference and a mismatched reference are different conditions. Missing evidence may make verification indeterminate or limited; a contradictory reference can invalidate the package’s integrity claims.

### 5. Implement the verifier service

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SyirkahEvidenceBundleVerifier {

    private final SyirkahEvidenceBundleReader bundleReader;
    private final SyirkahEvidenceCanonicalHasher hasher;
    private final SyirkahEvidenceCrossReferenceChecker referenceChecker;
    private final Clock clock;
    private final String verifierVersion;

    public SyirkahEvidenceBundleVerifier(
            SyirkahEvidenceBundleReader bundleReader,
            SyirkahEvidenceCanonicalHasher hasher,
            SyirkahEvidenceCrossReferenceChecker referenceChecker,
            Clock clock,
            String verifierVersion
    ) {
        this.bundleReader = bundleReader;
        this.hasher = hasher;
        this.referenceChecker = referenceChecker;
        this.clock = clock;
        this.verifierVersion = verifierVersion;
    }

    public SyirkahEvidenceVerificationResult verify(String exportId) {
        Optional<SyirkahEvidenceBundleContent> optionalBundle =
                bundleReader.read(exportId);

        if (optionalBundle.isEmpty()) {
            return result(
                    exportId,
                    null,
                    SyirkahEvidenceVerificationStatus.INDETERMINATE,
                    false,
                    false,
                    false,
                    List.of(finding(
                            SyirkahEvidenceVerificationFindingCode
                                    .MANIFEST_MISSING,
                            "MANIFEST",
                            exportId,
                            "The evidence bundle or its manifest is unavailable.",
                            false,
                            true
                    ))
            );
        }

        SyirkahEvidenceBundleContent bundle = optionalBundle.get();
        SyirkahFinalizationEvidenceManifest manifest = bundle.manifest();

        if (manifest == null) {
            return result(
                    exportId,
                    null,
                    SyirkahEvidenceVerificationStatus.INDETERMINATE,
                    false,
                    false,
                    false,
                    List.of(finding(
                            SyirkahEvidenceVerificationFindingCode
                                    .MANIFEST_MISSING,
                            "MANIFEST",
                            exportId,
                            "The exported bundle has no manifest.",
                            false,
                            true
                    ))
            );
        }

        List<SyirkahEvidenceVerificationFinding> findings =
                new ArrayList<>();

        Optional<String> calculatedManifestHash =
                hasher.hashCanonicalManifest(manifest);

        if (calculatedManifestHash.isEmpty()) {
            findings.add(finding(
                    SyirkahEvidenceVerificationFindingCode
                            .UNSUPPORTED_SCHEMA,
                    "MANIFEST",
                    exportId,
                    "The manifest version cannot be canonicalized.",
                    false,
                    true
            ));
        } else if (!calculatedManifestHash.get()
                .equals(bundle.storedManifestHash())) {
            findings.add(finding(
                    SyirkahEvidenceVerificationFindingCode
                            .MANIFEST_HASH_INVALID,
                    "MANIFEST",
                    exportId,
                    "The stored manifest hash does not match its content.",
                    true,
                    false
            ));
        }

        verifyArtifacts(bundle, findings);

        Optional<String> calculatedBundleHash =
                hasher.hashBundleContent(
                        manifest,
                        bundle.storedManifestHash(),
                        bundle.artifacts()
                );

        if (calculatedBundleHash.isEmpty()) {
            findings.add(finding(
                    SyirkahEvidenceVerificationFindingCode
                            .UNSUPPORTED_SCHEMA,
                    "BUNDLE",
                    exportId,
                    "The bundle cannot be canonicalized using a supported version.",
                    false,
                    true
            ));
        } else if (!calculatedBundleHash.get()
                .equals(bundle.storedBundleContentHash())) {
            findings.add(finding(
                    SyirkahEvidenceVerificationFindingCode
                            .BUNDLE_HASH_INVALID,
                    "BUNDLE",
                    exportId,
                    "The stored bundle hash does not match the bundle content.",
                    true,
                    false
            ));
        }

        findings.addAll(referenceChecker.check(
                manifest,
                bundle.artifacts()
        ));

        List<SyirkahEvidenceVerificationFinding> orderedFindings =
                findings.stream()
                        .sorted(Comparator
                                .comparing((SyirkahEvidenceVerificationFinding f) ->
                                        f.code().name())
                                .thenComparing(f -> safe(f.artifactType()))
                                .thenComparing(f -> safe(f.artifactId())))
                        .toList();

        boolean invalid = orderedFindings.stream()
                .anyMatch(SyirkahEvidenceVerificationFinding::invalidatesIntegrity);

        boolean limited = orderedFindings.stream()
                .anyMatch(SyirkahEvidenceVerificationFinding::limitsVerification);

        boolean integrityVerified = !invalid
                && calculatedManifestHash.isPresent()
                && calculatedBundleHash.isPresent();

        boolean crossReferencesVerified = orderedFindings.stream()
                .noneMatch(f ->
                        f.code() == SyirkahEvidenceVerificationFindingCode
                                .CROSS_REFERENCE_MISMATCH
                        || f.code() == SyirkahEvidenceVerificationFindingCode
                                .TENANT_SCOPE_MISMATCH
                        || f.code() == SyirkahEvidenceVerificationFindingCode
                                .LEDGER_SCOPE_MISMATCH);

        boolean historicalEvidenceComplete =
                manifest.complete() && !limited && !invalid;

        SyirkahEvidenceVerificationStatus status;

        if (invalid) {
            status = SyirkahEvidenceVerificationStatus.INVALID;
        } else if (limited) {
            status = SyirkahEvidenceVerificationStatus
                    .VERIFIED_WITH_LIMITATIONS;
        } else if (integrityVerified && crossReferencesVerified
                && historicalEvidenceComplete) {
            status = SyirkahEvidenceVerificationStatus.VERIFIED;
        } else {
            status = SyirkahEvidenceVerificationStatus.INDETERMINATE;
        }

        return result(
                exportId,
                bundle.storedManifestHash(),
                status,
                integrityVerified,
                crossReferencesVerified,
                historicalEvidenceComplete,
                orderedFindings
        );
    }

    private void verifyArtifacts(
            SyirkahEvidenceBundleContent bundle,
            List<SyirkahEvidenceVerificationFinding> findings
    ) {
        for (SyirkahEvidenceArtifactContent artifact : bundle.artifacts()) {
            Optional<String> calculatedHash = hasher.hashArtifact(
                    artifact.artifactType(),
                    artifact.schemaVersion(),
                    artifact.canonicalContent()
            );

            if (calculatedHash.isEmpty()) {
                findings.add(finding(
                        SyirkahEvidenceVerificationFindingCode
                                .UNSUPPORTED_SCHEMA,
                        artifact.artifactType(),
                        artifact.artifactId(),
                        "The artifact schema is not supported by this verifier.",
                        false,
                        true
                ));
                continue;
            }

            if (!calculatedHash.get().equals(artifact.declaredContentHash())) {
                findings.add(finding(
                        SyirkahEvidenceVerificationFindingCode
                                .ARTIFACT_HASH_INVALID,
                        artifact.artifactType(),
                        artifact.artifactId(),
                        "The artifact content does not match its declared hash.",
                        true,
                        false
                ));
            }
        }
    }

    private SyirkahEvidenceVerificationResult result(
            String exportId,
            String manifestHash,
            SyirkahEvidenceVerificationStatus status,
            boolean integrityVerified,
            boolean crossReferencesVerified,
            boolean historicalEvidenceComplete,
            List<SyirkahEvidenceVerificationFinding> findings
    ) {
        Instant now = clock.instant();

        return new SyirkahEvidenceVerificationResult(
                exportId,
                manifestHash,
                status,
                integrityVerified,
                crossReferencesVerified,
                historicalEvidenceComplete,
                findings,
                now,
                verifierVersion
        );
    }

    private static SyirkahEvidenceVerificationFinding finding(
            SyirkahEvidenceVerificationFindingCode code,
            String artifactType,
            String artifactId,
            String detail,
            boolean invalidatesIntegrity,
            boolean limitsVerification
    ) {
        return new SyirkahEvidenceVerificationFinding(
                code,
                artifactType,
                artifactId,
                detail,
                invalidatesIntegrity,
                limitsVerification
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
```

This is an application-layer template, not compiled code. The project’s actual artifact schemas and export-storage API must be wired into the adapters.

Important hardening before production:

- Verify that every manifest artifact reference has a corresponding bundle entry when marked `INCLUDED`; also reject unaccounted-for entries.
- Validate tenant and ledger scope before interpreting artifact content.
- Check required artifact types explicitly. An empty artifact list must not accidentally count as a complete package.
- Distinguish unavailable content from a valid empty payload.
- Use schema-specific canonicalization rules. Unknown schema versions should not be hashed using a guessed fallback.
- Validate the declared hash against canonical content, then compare that content hash to the matching manifest reference. Comparing two untrusted declared hashes alone is insufficient.

### 6. Add acceptance tests

| Test case                                                 | Expected status                                                                               |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Complete package; all hashes and references agree         | `VERIFIED`                                                                                    |
| One artifact’s canonical content is altered               | `INVALID`                                                                                     |
| Manifest content changes without updating its stored hash | `INVALID`                                                                                     |
| Report is absent or available only by reference           | `VERIFIED_WITH_LIMITATIONS` or `INDETERMINATE`, according to the declared verification policy |
| Outbox event points to another snapshot                   | `INVALID` when the contradiction is established                                               |
| Historical policy is unavailable                          | `VERIFIED_WITH_LIMITATIONS` if integrity is intact; historical completeness remains false     |
| Canonicalization version is unsupported                   | `INDETERMINATE` or `VERIFIED_WITH_LIMITATIONS`, never `VERIFIED`                              |
| Bundle cannot be read                                     | `INDETERMINATE`                                                                               |
| Tenant or ledger identity conflicts                       | `INVALID`                                                                                     |
| Bundle is verified twice without modification             | Same substantive result, aside from verification timestamp                                    |

The verifier must be read-only: no journal posting, no snapshot activation, no close-state transitions, and no repair of missing evidence.

### Next slice

Implement evidence export versioning and long-term retention: immutable export revisions, explicit supersession relationships, expiry rules, storage-integrity checks, and a way to verify a previously delivered export after the underlying application data has been archived.

////////////////

