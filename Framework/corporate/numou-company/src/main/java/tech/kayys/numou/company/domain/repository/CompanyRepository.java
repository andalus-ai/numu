package tech.kayys.syirkah.company.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.company.domain.identifier.CompanyId;
import tech.kayys.syirkah.company.domain.model.Company;
import tech.kayys.syirkah.company.domain.valueobject.CompanyStatus;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Repository for Company aggregates.
 */
public interface CompanyRepository extends Repository<Company, CompanyId> {

    /**
     * Finds companies by status.
     */
    CompletionStage<List<Company>> findByStatus(CompanyStatus status);

    /**
     * Finds active companies.
     */
    default CompletionStage<List<Company>> findActiveCompanies() {
        return findByStatus(CompanyStatus.ACTIVE);
    }

    /**
     * Finds companies by name containing text.
     */
    CompletionStage<List<Company>> findByNameContaining(String name);

    /**
     * Finds companies by domain.
     */
    CompletionStage<Company> findByDomain(String domain);

    /**
     * Finds companies by tax ID.
     */
    CompletionStage<Company> findByTaxId(String taxId);

    /**
     * Finds companies by registration number.
     */
    CompletionStage<Company> findByRegistrationNumber(String registrationNumber);

    /**
     * Checks if a company name is unique.
     */
    CompletionStage<Boolean> isCompanyNameUnique(String name);

    /**
     * Gets the total number of companies.
     */
    CompletionStage<Long> countCompanies();
}
