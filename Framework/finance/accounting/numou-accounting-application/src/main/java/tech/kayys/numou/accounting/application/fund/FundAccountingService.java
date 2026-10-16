package tech.kayys.syirkah.accounting.application.fund;

import tech.kayys.syirkah.accounting.domain.fund.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for managing non-profit and public sector fund accounting.
 */
public final class FundAccountingService {

    private final Map<FundId, Fund> funds = new ConcurrentHashMap<>();
    private final Map<GrantId, Grant> grants = new ConcurrentHashMap<>();

    public Fund createFund(FundId id, String code, String name, FundType type, BigDecimal initialBalance) {
        Fund fund = new Fund(id, code, name, type, initialBalance);
        funds.put(fund.id(), fund);
        return fund;
    }

    public Grant registerGrant(GrantId id, String code, String donorName, FundId linkedFundId, BigDecimal awardedAmount, LocalDate expiryDate) {
        if (!funds.containsKey(linkedFundId)) {
            throw new IllegalArgumentException("Linked fund does not exist: " + linkedFundId.value());
        }
        Grant grant = new Grant(id, code, donorName, linkedFundId, awardedAmount, expiryDate);
        grants.put(grant.id(), grant);
        // Credit the linked fund with the awarded grant amount
        funds.get(linkedFundId).credit(awardedAmount);
        return grant;
    }

    public void recordGrantExpenditure(GrantId grantId, BigDecimal amount) {
        Grant grant = grants.get(grantId);
        if (grant == null) throw new IllegalArgumentException("Grant not found: " + grantId.value());
        grant.recordExpenditure(amount);

        Fund linkedFund = funds.get(grant.linkedFundId());
        if (linkedFund != null) {
            linkedFund.debit(amount);
        }
    }

    public void transfer(FundId sourceFundId, FundId targetFundId, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        Fund source = funds.get(sourceFundId);
        Fund target = funds.get(targetFundId);
        if (source == null) throw new IllegalArgumentException("Source fund not found: " + sourceFundId.value());
        if (target == null) throw new IllegalArgumentException("Target fund not found: " + targetFundId.value());

        if (source.type() == FundType.PERMANENTLY_RESTRICTED) {
            throw new IllegalStateException("Cannot transfer out of PERMANENTLY_RESTRICTED endowment principal fund: " + source.code());
        }

        source.debit(amount);
        target.credit(amount);
    }

    public Optional<Fund> findFund(FundId id) { return Optional.ofNullable(funds.get(id)); }
    public Optional<Grant> findGrant(GrantId id) { return Optional.ofNullable(grants.get(id)); }
}
