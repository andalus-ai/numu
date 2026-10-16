package tech.kayys.payment.processor.bank.va;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Virtual Account operations.
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class VirtualAccountRepository implements PanacheRepository<VirtualAccount> {

    /**
     * Find Virtual Account by VA number
     */
    public Optional<VirtualAccount> findByVaNumber(String vaNumber) {
        return find("vaNumber", vaNumber).firstResultOptional();
    }

    /**
     * Find Virtual Account by VA number and bank code
     */
    public Optional<VirtualAccount> findByVaNumberAndBankCode(String vaNumber, String bankCode) {
        return find("vaNumber = ?1 and bankCode = ?2", vaNumber, bankCode).firstResultOptional();
    }

    /**
     * Find Virtual Account by external VA ID
     */
    public Optional<VirtualAccount> findByExternalVaId(String externalVaId) {
        return find("externalVaId", externalVaId).firstResultOptional();
    }

    /**
     * Find Virtual Accounts by customer ID
     */
    public List<VirtualAccount> findByCustomerId(String customerId) {
        return list("customerId", customerId);
    }

    /**
     * Find Virtual Accounts by customer email
     */
    public List<VirtualAccount> findByCustomerEmail(String customerEmail) {
        return list("customerEmail", customerEmail);
    }

    /**
     * Find Virtual Accounts by bank code
     */
    public List<VirtualAccount> findByBankCode(String bankCode) {
        return list("bankCode", bankCode);
    }

    /**
     * Find Virtual Accounts by bank code and status
     */
    public List<VirtualAccount> findByBankCodeAndStatus(String bankCode, VirtualAccount.VAStatus status) {
        return find("bankCode = ?1 and status = ?2", bankCode, status).list();
    }

    /**
     * Find active Virtual Accounts
     */
    public List<VirtualAccount> findActive() {
        return find("status", VirtualAccount.VAStatus.ACTIVE).list();
    }

    /**
     * Find active Virtual Accounts by bank
     */
    public List<VirtualAccount> findActiveByBank(String bankCode) {
        return find("bankCode = ?1 and status = ?2", bankCode, VirtualAccount.VAStatus.ACTIVE).list();
    }

    /**
     * Find Virtual Accounts by type
     */
    public List<VirtualAccount> findByVaType(VirtualAccount.VAType vaType) {
        return list("vaType", vaType);
    }

    /**
     * Find static (reusable) Virtual Accounts
     */
    public List<VirtualAccount> findStatic() {
        return find("vaType", VirtualAccount.VAType.STATIC).list();
    }

    /**
     * Find dynamic (single-use) Virtual Accounts
     */
    public List<VirtualAccount> findDynamic() {
        return find("vaType", VirtualAccount.VAType.DYNAMIC).list();
    }

    /**
     * Find expired Virtual Accounts
     */
    public List<VirtualAccount> findExpired() {
        return list("expired", true);
    }

    /**
     * Find Virtual Accounts expiring before a specific time
     */
    public List<VirtualAccount> findExpiringBefore(LocalDateTime threshold) {
        return find("expiryTime < ?1 and expired = false", threshold).list();
    }

    /**
     * Find Virtual Accounts by status
     */
    public List<VirtualAccount> findByStatus(VirtualAccount.VAStatus status) {
        return list("status", status);
    }

    /**
     * Find Virtual Accounts by gateway provider
     */
    public List<VirtualAccount> findByGatewayProvider(String gatewayProvider) {
        return list("gatewayProvider", gatewayProvider);
    }

    /**
     * Find Virtual Accounts created within a date range
     */
    public List<VirtualAccount> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return list("createdAt between ?1 and ?2", startDate, endDate);
    }

    /**
     * Find Virtual Accounts with payments
     */
    public List<VirtualAccount> findWithPayments() {
        return find("paymentCount > 0").list();
    }

    /**
     * Find Virtual Accounts without payments
     */
    public List<VirtualAccount> findWithoutPayments() {
        return find("paymentCount = 0").list();
    }

    /**
     * Count Virtual Accounts by bank code
     */
    public long countByBankCode(String bankCode) {
        return count("bankCode", bankCode);
    }

    /**
     * Count active Virtual Accounts by bank code
     */
    public long countActiveByBankCode(String bankCode) {
        return count("bankCode = ?1 and status = ?2", 
                    bankCode, VirtualAccount.VAStatus.ACTIVE);
    }

    /**
     * Count Virtual Accounts by customer ID
     */
    public long countByCustomerId(String customerId) {
        return count("customerId", customerId);
    }

    /**
     * Count Virtual Accounts by status
     */
    public long countByStatus(VirtualAccount.VAStatus status) {
        return count("status", status);
    }

    /**
     * Count Virtual Accounts by type
     */
    public long countByVaType(VirtualAccount.VAType vaType) {
        return count("vaType", vaType);
    }

    /**
     * Find available (active and not expired) Virtual Accounts
     */
    public List<VirtualAccount> findAvailable() {
        return find("status = ?1 and expired = false", VirtualAccount.VAStatus.ACTIVE).list();
    }

    /**
     * Find available Virtual Accounts for a specific bank
     */
    public List<VirtualAccount> findAvailableByBank(String bankCode) {
        return find("bankCode = ?1 and status = ?2 and expired = false", 
                   bankCode, VirtualAccount.VAStatus.ACTIVE).list();
    }

    /**
     * Find Virtual Accounts that can accept payments
     */
    public List<VirtualAccount> findAcceptingPayments() {
        return find("status = ?1 and expired = false and vaType = ?2", 
                   VirtualAccount.VAStatus.ACTIVE, VirtualAccount.VAType.STATIC).list();
    }

    /**
     * Delete inactive Virtual Accounts older than specified date
     */
    public long deleteInactiveOlderThan(LocalDateTime date) {
        return delete("status = ?1 and createdAt < ?2", 
                     VirtualAccount.VAStatus.INACTIVE, date);
    }

    /**
     * Delete expired Virtual Accounts older than specified date
     */
    public long deleteExpiredOlderThan(LocalDateTime date) {
        return delete("expired = true and createdAt < ?2", date);
    }

    /**
     * Find Virtual Accounts by fixed amount
     */
    public List<VirtualAccount> findByFixedAmount(java.math.BigDecimal fixedAmount) {
        return list("fixedAmount", fixedAmount);
    }

    /**
     * Find customer's active Virtual Accounts
     */
    public List<VirtualAccount> findCustomerActiveAccounts(String customerId) {
        return find("customerId = ?1 and status = ?2 and expired = false", 
                   customerId, VirtualAccount.VAStatus.ACTIVE).list();
    }
}
