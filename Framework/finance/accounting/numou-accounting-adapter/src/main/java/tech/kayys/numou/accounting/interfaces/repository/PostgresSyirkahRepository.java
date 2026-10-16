package tech.kayys.numou.accounting.interfaces.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.accounting.application.port.SyirkahRepository;
import tech.kayys.numou.accounting.domain.islamic.SyirkahAllocation;
import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculation;
import tech.kayys.numou.accounting.domain.islamic.SyirkahContractVersion;
import tech.kayys.numou.accounting.domain.islamic.SyirkahParty;
import tech.kayys.numou.accounting.domain.islamic.SyirkahPartyRole;
import tech.kayys.numou.accounting.domain.islamic.SyirkahType;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** PostgreSQL implementation. Every mutating operation uses one database transaction. */
@ApplicationScoped
public class PostgresSyirkahRepository implements SyirkahRepository {
    @Inject DataSource dataSource;

    @Override
    public void createContract(SyirkahContractVersion c, String actor) {
        String termsHash = sha256(c.toString());
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_contract
                      (tenant_id, company_id, contract_id, current_version, lifecycle_status, created_by)
                    VALUES (?, ?, ?, ?, 'DRAFT', ?)
                    """)) {
                    s.setString(1, c.tenantId()); s.setString(2, c.companyId());
                    s.setString(3, c.contractId()); s.setInt(4, c.version()); s.setString(5, actor);
                    s.executeUpdate();
                }
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_contract_version
                      (tenant_id, company_id, contract_id, version, currency_code, syirkah_type,
                       effective_from, effective_until, sharia_policy_version, terms_hash, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    s.setString(1, c.tenantId()); s.setString(2, c.companyId()); s.setString(3, c.contractId());
                    s.setInt(4, c.version()); s.setString(5, c.currencyCode()); s.setString(6, c.type().name());
                    s.setDate(7, Date.valueOf(c.effectiveFrom())); s.setDate(8, Date.valueOf(c.effectiveUntil()));
                    s.setString(9, c.shariaPolicyVersion()); s.setString(10, termsHash); s.setString(11, actor);
                    s.executeUpdate();
                }
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_contract_party
                      (tenant_id, company_id, contract_id, contract_version, party_id, display_name,
                       party_role, capital_contribution, profit_nisbah_percent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    for (SyirkahParty p : c.parties()) {
                        s.setString(1, c.tenantId()); s.setString(2, c.companyId()); s.setString(3, c.contractId());
                        s.setInt(4, c.version()); s.setString(5, p.partyId()); s.setString(6, p.displayName());
                        s.setString(7, p.role().name()); s.setBigDecimal(8, p.capitalContribution());
                        s.setBigDecimal(9, p.profitNisbahPercent()); s.addBatch();
                    }
                    s.executeBatch();
                }
                audit(con, c.tenantId(), c.companyId(), "CONTRACT", c.contractId(), c.version(),
                        "CONTRACT_CREATED", actor, "{\"termsHash\":\"" + termsHash + "\"}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("create Syirkah contract", e);
            }
        } catch (SQLException e) {
            throw translate("create Syirkah contract", e);
        }
    }

    @Override
    public void createContractVersion(SyirkahContractVersion c, String actor) {
        String termsHash = sha256(c.toString());
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement lock = con.prepareStatement("""
                    SELECT lifecycle_status FROM accounting.syirkah_contract
                    WHERE tenant_id=? AND company_id=? AND contract_id=? FOR UPDATE
                    """)) {
                    lock.setString(1, c.tenantId()); lock.setString(2, c.companyId()); lock.setString(3, c.contractId());
                    try (ResultSet r = lock.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Contract not found");
                        status = r.getString(1);
                    }
                }
                if (!"ACTIVE".equals(status)) throw new IllegalStateException("Only active contracts can be amended");
                int latest;
                int drafts;
                try (PreparedStatement q = con.prepareStatement("""
                    SELECT COALESCE(MAX(version), 0), COUNT(*) FILTER (WHERE version_status='DRAFT')
                    FROM accounting.syirkah_contract_version
                    WHERE tenant_id=? AND company_id=? AND contract_id=?
                    """)) {
                    q.setString(1, c.tenantId()); q.setString(2, c.companyId()); q.setString(3, c.contractId());
                    try (ResultSet r = q.executeQuery()) { r.next(); latest = r.getInt(1); drafts = r.getInt(2); }
                }
                if (drafts != 0) throw new IllegalStateException("Resolve the existing draft amendment before creating another");
                if (c.version() != latest + 1) {
                    throw new IllegalArgumentException("Contract amendment version must be latest version + 1");
                }
                insertVersionAndParties(con, c, actor, termsHash);
                audit(con, c.tenantId(), c.companyId(), "CONTRACT", c.contractId(), c.version(),
                        "CONTRACT_VERSION_DRAFTED", actor, "{\"termsHash\":\"" + termsHash + "\"}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("create Syirkah contract version", e);
            }
        } catch (SQLException e) {
            throw translate("create Syirkah contract version", e);
        }
    }

    @Override
    public void activateContract(String tenant, String company, String contractId, int version,
                                 String actor, String evidence) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                String creator;
                String contractStatus;
                String versionStatus;
                try (PreparedStatement s = con.prepareStatement("""
                    SELECT v.created_by, c.lifecycle_status, v.version_status
                    FROM accounting.syirkah_contract c
                    JOIN accounting.syirkah_contract_version v
                      ON v.tenant_id=c.tenant_id AND v.company_id=c.company_id AND v.contract_id=c.contract_id
                    WHERE c.tenant_id=? AND c.company_id=? AND c.contract_id=? AND v.version=?
                    FOR UPDATE OF c, v
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, contractId); s.setInt(4, version);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Contract version not found");
                        creator = r.getString(1);
                        contractStatus = r.getString(2);
                        versionStatus = r.getString(3);
                    }
                }
                if (!"DRAFT".equals(versionStatus)) throw new IllegalStateException("Only a draft version can be activated");
                if (!"DRAFT".equals(contractStatus) && !"ACTIVE".equals(contractStatus)) {
                    throw new IllegalStateException("Contract is not in an amendable lifecycle state");
                }
                if (creator.equals(actor)) throw new SecurityException("Contract maker cannot approve their own version");
                int latest;
                try (PreparedStatement q = con.prepareStatement("""
                    SELECT MAX(version) FROM accounting.syirkah_contract_version
                    WHERE tenant_id=? AND company_id=? AND contract_id=?
                    """)) {
                    q.setString(1, tenant); q.setString(2, company); q.setString(3, contractId);
                    try (ResultSet r = q.executeQuery()) { r.next(); latest = r.getInt(1); }
                }
                if (version != latest) throw new IllegalStateException("Only the latest contract version can be activated");

                try (PreparedStatement s = con.prepareStatement("""
                    UPDATE accounting.syirkah_contract_version
                    SET version_status='SUPERSEDED', superseded_at=NOW()
                    WHERE tenant_id=? AND company_id=? AND contract_id=? AND version_status='ACTIVE' AND version<>?
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, contractId); s.setInt(4, version);
                    s.executeUpdate();
                }
                try (PreparedStatement s = con.prepareStatement("""
                    UPDATE accounting.syirkah_contract_version
                    SET signed_evidence_reference=?, version_status='ACTIVE', activated_at=NOW(), activated_by=?
                    WHERE tenant_id=? AND company_id=? AND contract_id=? AND version=? AND version_status='DRAFT'
                    """)) {
                    s.setString(1, evidence); s.setString(2, actor); s.setString(3, tenant);
                    s.setString(4, company); s.setString(5, contractId); s.setInt(6, version);
                    if (s.executeUpdate() != 1) throw new IllegalStateException("Contract version activation race detected");
                }
                try (PreparedStatement s = con.prepareStatement("""
                    UPDATE accounting.syirkah_contract SET lifecycle_status='ACTIVE', current_version=?
                    WHERE tenant_id=? AND company_id=? AND contract_id=? AND lifecycle_status IN ('DRAFT','ACTIVE')
                    """)) {
                    s.setInt(1, version); s.setString(2, tenant); s.setString(3, company); s.setString(4, contractId);
                    if (s.executeUpdate() != 1) throw new IllegalStateException("Contract activation race detected");
                }
                audit(con, tenant, company, "CONTRACT", contractId, version, "CONTRACT_VERSION_ACTIVATED", actor,
                        "{\"signedEvidenceReference\":\"" + jsonEscape(evidence) + "\"}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("activate Syirkah contract", e);
            }
        } catch (SQLException e) {
            throw translate("activate Syirkah contract", e);
        }
    }

    @Override
    public boolean isContractActive(String tenant, String company, String contractId) {
        try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement("""
            SELECT 1 FROM accounting.syirkah_contract
            WHERE tenant_id=? AND company_id=? AND contract_id=? AND lifecycle_status='ACTIVE'
            """)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, contractId);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        } catch (SQLException e) { throw translate("check Syirkah contract status", e); }
    }

    @Override
    public boolean isContractVersionActivated(String tenant, String company, String contractId, int version) {
        try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement("""
            SELECT 1 FROM accounting.syirkah_contract_version
            WHERE tenant_id=? AND company_id=? AND contract_id=? AND version=?
              AND version_status IN ('ACTIVE','SUPERSEDED')
            """)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, contractId); s.setInt(4, version);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        } catch (SQLException e) { throw translate("check Syirkah contract version status", e); }
    }

    @Override
    public Optional<SyirkahContractVersion> findContract(String tenant, String company, String contractId, int version) {
        String sql = """
            SELECT currency_code, syirkah_type, effective_from, effective_until, sharia_policy_version
            FROM accounting.syirkah_contract_version
            WHERE tenant_id=? AND company_id=? AND contract_id=? AND version=?
            """;
        try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement(sql)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, contractId); s.setInt(4, version);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                String currency = r.getString(1); SyirkahType type = SyirkahType.valueOf(r.getString(2));
                LocalDate from = r.getDate(3).toLocalDate(); LocalDate until = r.getDate(4).toLocalDate();
                String policy = r.getString(5);
                List<SyirkahParty> parties = new ArrayList<>();
                try (PreparedStatement p = con.prepareStatement("""
                    SELECT party_id, display_name, party_role, capital_contribution, profit_nisbah_percent
                    FROM accounting.syirkah_contract_party
                    WHERE tenant_id=? AND company_id=? AND contract_id=? AND contract_version=? ORDER BY party_id
                    """)) {
                    p.setString(1, tenant); p.setString(2, company); p.setString(3, contractId); p.setInt(4, version);
                    try (ResultSet pr = p.executeQuery()) {
                        while (pr.next()) parties.add(new SyirkahParty(pr.getString(1), pr.getString(2),
                                SyirkahPartyRole.valueOf(pr.getString(3)), pr.getBigDecimal(4), pr.getBigDecimal(5)));
                    }
                }
                return Optional.of(new SyirkahContractVersion(contractId, version, tenant, company, currency,
                        type, from, until, policy, parties));
            }
        } catch (SQLException e) { throw translate("load Syirkah contract", e); }
    }

    @Override
    public void saveBusinessResult(String tenant, String company, SyirkahBusinessResult r, String actor) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_business_result
                      (tenant_id, company_id, result_id, contract_id, contract_version, period_start, period_end,
                       currency_code, total_revenue, direct_costs, operating_costs, other_costs, evidence_reference, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, r.resultId());
                    s.setString(4, r.contractId()); s.setInt(5, r.contractVersion());
                    s.setDate(6, Date.valueOf(r.periodStart())); s.setDate(7, Date.valueOf(r.periodEnd()));
                    s.setString(8, r.currencyCode()); s.setBigDecimal(9, r.totalRevenue());
                    s.setBigDecimal(10, r.directCosts()); s.setBigDecimal(11, r.operatingCosts());
                    s.setBigDecimal(12, r.otherCosts()); s.setString(13, r.evidenceReference()); s.setString(14, actor);
                    s.executeUpdate();
                }
                audit(con, tenant, company, "BUSINESS_RESULT", r.resultId(), r.contractVersion(),
                        "BUSINESS_RESULT_REGISTERED", actor, "{\"evidenceReference\":\"" + jsonEscape(r.evidenceReference()) + "\"}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("save Syirkah business result", e);
            }
        } catch (SQLException e) { throw translate("save Syirkah business result", e); }
    }

    @Override
    public Optional<SyirkahBusinessResult> findBusinessResult(String tenant, String company, String resultId) {
        try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement("""
            SELECT contract_id, contract_version, period_start, period_end, total_revenue, direct_costs,
                   operating_costs, other_costs, currency_code, evidence_reference
            FROM accounting.syirkah_business_result WHERE tenant_id=? AND company_id=? AND result_id=?
            """)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, resultId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new SyirkahBusinessResult(resultId, r.getString(1), r.getInt(2),
                        r.getDate(3).toLocalDate(), r.getDate(4).toLocalDate(), r.getBigDecimal(5),
                        r.getBigDecimal(6), r.getBigDecimal(7), r.getBigDecimal(8), r.getString(9), r.getString(10)));
            }
        } catch (SQLException e) { throw translate("load Syirkah business result", e); }
    }

    @Override
    public int saveCalculation(String tenant, String company, SyirkahCalculation c, String actor) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement existing = con.prepareStatement("""
                    SELECT calculation_version FROM accounting.syirkah_calculation_snapshot
                    WHERE tenant_id=? AND company_id=? AND result_id=? AND calculation_hash=?
                    """)) {
                    existing.setString(1, tenant); existing.setString(2, company);
                    existing.setString(3, c.resultId()); existing.setString(4, c.calculationHash());
                    try (ResultSet r = existing.executeQuery()) {
                        if (r.next()) { con.commit(); return r.getInt(1); }
                    }
                }
                try (PreparedStatement lock = con.prepareStatement("""
                    SELECT result_id FROM accounting.syirkah_business_result
                    WHERE tenant_id=? AND company_id=? AND result_id=? FOR UPDATE
                    """)) {
                    lock.setString(1, tenant); lock.setString(2, company); lock.setString(3, c.resultId());
                    try (ResultSet r = lock.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Business result not found in scope");
                    }
                }
                int version;
                try (PreparedStatement s = con.prepareStatement("""
                    SELECT COALESCE(MAX(calculation_version),0)+1 FROM accounting.syirkah_calculation_snapshot
                    WHERE tenant_id=? AND company_id=? AND result_id=?
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, c.resultId());
                    try (ResultSet r = s.executeQuery()) { r.next(); version = r.getInt(1); }
                }
                String payload = payload(c);
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_calculation_snapshot
                      (tenant_id, company_id, result_id, calculation_version, contract_id, contract_version,
                       policy_version, currency_code, net_result, calculation_hash, calculation_payload,
                       status, calculated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'CALCULATED', ?)
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, c.resultId()); s.setInt(4, version);
                    s.setString(5, c.contractId()); s.setInt(6, c.contractVersion()); s.setString(7, c.policyVersion());
                    s.setString(8, c.currencyCode()); s.setBigDecimal(9, c.netResult()); s.setString(10, c.calculationHash());
                    s.setString(11, payload); s.setString(12, actor); s.executeUpdate();
                }
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_distribution_obligation
                      (tenant_id, company_id, result_id, calculation_version, party_id, currency_code,
                       amount, settled_amount, status, allocation_basis, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'PENDING', ?, ?)
                    """)) {
                    for (SyirkahAllocation a : c.allocations()) {
                        s.setString(1, tenant); s.setString(2, company); s.setString(3, c.resultId());
                        s.setInt(4, version); s.setString(5, a.partyId()); s.setString(6, a.currencyCode());
                        s.setBigDecimal(7, a.amount()); s.setString(8, a.basis()); s.setString(9, actor); s.addBatch();
                    }
                    s.executeBatch();
                }
                audit(con, tenant, company, "CALCULATION", c.resultId(), version, "CALCULATION_CREATED", actor,
                        "{\"calculationHash\":\"" + c.calculationHash() + "\"}");
                con.commit();
                return version;
            } catch (Exception e) {
                rollback(con, e);
                throw translate("save Syirkah calculation", e);
            }
        } catch (SQLException e) { throw translate("save Syirkah calculation", e); }
    }

    @Override
    public Optional<SyirkahCalculation> findCalculation(String tenant, String company, String resultId, int version) {
        try (Connection con = dataSource.getConnection(); PreparedStatement s = con.prepareStatement("""
            SELECT contract_id, contract_version, policy_version, currency_code, net_result, calculation_hash
            FROM accounting.syirkah_calculation_snapshot
            WHERE tenant_id=? AND company_id=? AND result_id=? AND calculation_version=?
            """)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, resultId); s.setInt(4, version);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                String contractId = r.getString(1); int contractVersion = r.getInt(2); String policy = r.getString(3);
                String currency = r.getString(4); BigDecimal net = r.getBigDecimal(5); String hash = r.getString(6);
                List<SyirkahAllocation> allocations = new ArrayList<>();
                try (PreparedStatement p = con.prepareStatement("""
                    SELECT party_id, amount, currency_code, allocation_basis
                    FROM accounting.syirkah_distribution_obligation
                    WHERE tenant_id=? AND company_id=? AND result_id=? AND calculation_version=? ORDER BY party_id
                    """)) {
                    p.setString(1, tenant); p.setString(2, company); p.setString(3, resultId); p.setInt(4, version);
                    try (ResultSet pr = p.executeQuery()) {
                        while (pr.next()) allocations.add(new SyirkahAllocation(pr.getString(1), pr.getBigDecimal(2),
                                pr.getString(3), pr.getString(4)));
                    }
                }
                return Optional.of(new SyirkahCalculation(resultId, contractId, contractVersion, policy,
                        currency, net, allocations, hash));
            }
        } catch (SQLException e) { throw translate("load Syirkah calculation", e); }
    }

    @Override
    public void approveCalculation(String tenant, String company, String resultId, int version, String approver) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement s = con.prepareStatement("""
                UPDATE accounting.syirkah_calculation_snapshot
                SET status='APPROVED', approved_at=NOW(), approved_by=?
                WHERE tenant_id=? AND company_id=? AND result_id=? AND calculation_version=?
                  AND status='CALCULATED' AND calculated_by<>?
                """)) {
                s.setString(1, approver); s.setString(2, tenant); s.setString(3, company);
                s.setString(4, resultId); s.setInt(5, version); s.setString(6, approver);
                if (s.executeUpdate() != 1) throw new IllegalStateException(
                        "Calculation cannot be approved: missing, already transitioned, or maker-checker violation");
                audit(con, tenant, company, "CALCULATION", resultId, version, "CALCULATION_APPROVED", approver, "{}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("approve Syirkah calculation", e);
            }
        } catch (SQLException e) { throw translate("approve Syirkah calculation", e); }
    }

    @Override
    public void settleDistribution(String tenant, String company, String resultId, int version, String partyId,
                                   BigDecimal amount, String currency, String key, String actor) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement prior = con.prepareStatement("""
                    SELECT result_id, calculation_version, party_id, amount, currency_code
                    FROM accounting.syirkah_distribution_settlement
                    WHERE tenant_id=? AND company_id=? AND idempotency_key=?
                    """)) {
                    prior.setString(1, tenant); prior.setString(2, company); prior.setString(3, key);
                    try (ResultSet r = prior.executeQuery()) {
                        if (r.next()) {
                            boolean same = resultId.equals(r.getString(1)) && version == r.getInt(2)
                                    && partyId.equals(r.getString(3)) && amount.compareTo(r.getBigDecimal(4)) == 0
                                    && currency.equals(r.getString(5));
                            if (!same) throw new IllegalArgumentException("Idempotency key was already used for a different settlement");
                            con.commit(); return;
                        }
                    }
                }
                try (PreparedStatement s = con.prepareStatement("""
                    SELECT o.amount, o.settled_amount, o.currency_code, c.status
                    FROM accounting.syirkah_distribution_obligation o
                    JOIN accounting.syirkah_calculation_snapshot c
                      ON c.tenant_id=o.tenant_id AND c.company_id=o.company_id
                     AND c.result_id=o.result_id AND c.calculation_version=o.calculation_version
                    WHERE o.tenant_id=? AND o.company_id=? AND o.result_id=? AND o.calculation_version=? AND o.party_id=?
                    FOR UPDATE OF o, c
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, resultId);
                    s.setInt(4, version); s.setString(5, partyId);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Distribution obligation not found");
                        BigDecimal obligation = r.getBigDecimal(1), settled = r.getBigDecimal(2);
                        if (!"POSTED".equals(r.getString(4))) throw new IllegalStateException("Only posted obligations can be settled");
                        if (!currency.equals(r.getString(3))) throw new IllegalArgumentException("Settlement currency mismatch");
                        if (settled.add(amount).compareTo(obligation) > 0) throw new IllegalArgumentException("Settlement exceeds obligation");
                    }
                }
                try (PreparedStatement s = con.prepareStatement("""
                    INSERT INTO accounting.syirkah_distribution_settlement
                      (tenant_id, company_id, idempotency_key, result_id, calculation_version, party_id,
                       currency_code, amount, actor)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    s.setString(1, tenant); s.setString(2, company); s.setString(3, key); s.setString(4, resultId);
                    s.setInt(5, version); s.setString(6, partyId); s.setString(7, currency);
                    s.setBigDecimal(8, amount); s.setString(9, actor); s.executeUpdate();
                }
                try (PreparedStatement s = con.prepareStatement("""
                    UPDATE accounting.syirkah_distribution_obligation
                    SET settled_amount=settled_amount+?,
                        status=CASE WHEN settled_amount+? = amount THEN 'SETTLED' ELSE 'PARTIALLY_SETTLED' END
                    WHERE tenant_id=? AND company_id=? AND result_id=? AND calculation_version=? AND party_id=?
                    """)) {
                    s.setBigDecimal(1, amount); s.setBigDecimal(2, amount); s.setString(3, tenant); s.setString(4, company);
                    s.setString(5, resultId); s.setInt(6, version); s.setString(7, partyId);
                    if (s.executeUpdate() != 1) throw new IllegalStateException("Settlement obligation disappeared");
                }
                audit(con, tenant, company, "DISTRIBUTION", resultId, version, "DISTRIBUTION_SETTLED", actor,
                        "{\"partyId\":\"" + jsonEscape(partyId) + "\",\"amount\":\"" + amount.toPlainString() + "\",\"idempotencyKey\":\"" + jsonEscape(key) + "\"}");
                con.commit();
            } catch (Exception e) {
                rollback(con, e);
                throw translate("settle Syirkah distribution", e);
            }
        } catch (SQLException e) { throw translate("settle Syirkah distribution", e); }
    }

    private static void insertVersionAndParties(Connection con, SyirkahContractVersion c,
                                                  String actor, String termsHash) throws SQLException {
        try (PreparedStatement s = con.prepareStatement("""
            INSERT INTO accounting.syirkah_contract_version
              (tenant_id, company_id, contract_id, version, currency_code, syirkah_type,
               effective_from, effective_until, sharia_policy_version, terms_hash, created_by, version_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')
            """)) {
            s.setString(1, c.tenantId()); s.setString(2, c.companyId()); s.setString(3, c.contractId());
            s.setInt(4, c.version()); s.setString(5, c.currencyCode()); s.setString(6, c.type().name());
            s.setDate(7, Date.valueOf(c.effectiveFrom())); s.setDate(8, Date.valueOf(c.effectiveUntil()));
            s.setString(9, c.shariaPolicyVersion()); s.setString(10, termsHash); s.setString(11, actor);
            s.executeUpdate();
        }
        try (PreparedStatement s = con.prepareStatement("""
            INSERT INTO accounting.syirkah_contract_party
              (tenant_id, company_id, contract_id, contract_version, party_id, display_name,
               party_role, capital_contribution, profit_nisbah_percent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            for (SyirkahParty p : c.parties()) {
                s.setString(1, c.tenantId()); s.setString(2, c.companyId()); s.setString(3, c.contractId());
                s.setInt(4, c.version()); s.setString(5, p.partyId()); s.setString(6, p.displayName());
                s.setString(7, p.role().name()); s.setBigDecimal(8, p.capitalContribution());
                s.setBigDecimal(9, p.profitNisbahPercent()); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void audit(Connection con, String tenant, String company, String type, String id, Integer version,
                              String event, String actor, String details) throws SQLException {
        try (PreparedStatement s = con.prepareStatement("""
            INSERT INTO accounting.syirkah_audit_event
              (tenant_id, company_id, aggregate_type, aggregate_id, aggregate_version, event_type, actor, details)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            """)) {
            s.setString(1, tenant); s.setString(2, company); s.setString(3, type); s.setString(4, id);
            if (version == null) s.setNull(5, java.sql.Types.INTEGER); else s.setInt(5, version);
            s.setString(6, event); s.setString(7, actor); s.setString(8, details); s.executeUpdate();
        }
    }

    private static String payload(SyirkahCalculation c) {
        StringBuilder b = new StringBuilder("{\"allocations\":[");
        for (int i = 0; i < c.allocations().size(); i++) {
            SyirkahAllocation a = c.allocations().get(i);
            if (i > 0) b.append(',');
            b.append("{\"partyId\":\"").append(jsonEscape(a.partyId())).append("\",\"amount\":\"")
                    .append(a.amount().toPlainString()).append("\",\"currencyCode\":\"")
                    .append(jsonEscape(a.currencyCode())).append("\",\"basis\":\"")
                    .append(jsonEscape(a.basis())).append("\"}");
        }
        return b.append("],\"calculationHash\":\"").append(c.calculationHash()).append("\"}").toString();
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void rollback(Connection con, Exception original) {
        try { con.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private static IllegalStateException translate(String operation, Exception e) {
        if (e instanceof SecurityException se) throw se;
        if (e instanceof IllegalArgumentException iae) throw iae;
        if (e instanceof IllegalStateException ise) return ise;
        return new IllegalStateException("Unable to " + operation, e);
    }
}
