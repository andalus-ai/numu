package tech.kayys.numou.organization.application.command;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.organization.application.port.PartyOrganizationEligibilityPort;
import tech.kayys.numou.organization.domain.Organization;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociation;
import tech.kayys.numou.organization.spi.OrganizationPartyAssociationRepository;
import tech.kayys.numou.organization.spi.OrganizationRepository;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
/** Activates a pending association after rechecking tenant and Party eligibility. */
public final class ActivateOrganizationPartyAssociationHandler {
 private final OrganizationRepository organizations;
 private final OrganizationPartyAssociationRepository associations;
 private final PartyOrganizationEligibilityPort parties;
 public ActivateOrganizationPartyAssociationHandler(OrganizationRepository organizations,
  OrganizationPartyAssociationRepository associations,PartyOrganizationEligibilityPort parties) {
  this.organizations=Objects.requireNonNull(organizations); this.associations=Objects.requireNonNull(associations);
  this.parties=Objects.requireNonNull(parties);
 }
 public CompletionStage<Result<OrganizationPartyAssociation>> handle(ActivateOrganizationPartyAssociationCommand c) {
  Objects.requireNonNull(c,"command"); Objects.requireNonNull(c.associationId(),"associationId");
  Objects.requireNonNull(c.tenantId(),"tenantId"); Objects.requireNonNull(c.activatedAt(),"activatedAt");
  Objects.requireNonNull(c.actorId(),"actorId");
  return associations.findById(c.associationId()).thenCompose(found -> {
   if(found.isEmpty()) return failure("ORGANIZATION_PARTY_ASSOCIATION_NOT_FOUND",
    "Organization-Party association was not found");
   OrganizationPartyAssociation association=found.get();
   return organizations.findById(association.organizationId()).thenCompose(orgFound -> {
    if(orgFound.isEmpty()) return failure("ORGANIZATION_NOT_FOUND","Organization was not found");
    Organization org=orgFound.get();
    if(!org.getTenantId().equals(c.tenantId())) return failure("ORGANIZATION_TENANT_MISMATCH",
     "Organization does not belong to the supplied tenant");
    if(!org.isActive()) return failure("ORGANIZATION_NOT_ACTIVE",
     "Only an active Organization can activate an association");
    return parties.findEligibility(association.partyId()).thenCompose(e -> {
     if(e==null || !e.canRepresentOrganization()) return failure("PARTY_NOT_ELIGIBLE_FOR_ORGANIZATION",
      "Party must exist, be active, and have kind ORGANIZATION");
     try {
      OrganizationPartyAssociation activated=association.activate(c.activatedAt(),c.actorId());
      return associations.save(activated, c.tenantId()).thenApply(Result::success);
     } catch(IllegalStateException | IllegalArgumentException invalid) {
      return failure("ORGANIZATION_PARTY_ASSOCIATION_INVALID_STATE",invalid.getMessage());
     }
    });
   });
  });
 }
 private static <T> CompletionStage<Result<T>> failure(String code,String message) {
  return CompletableFuture.completedFuture(Result.failure(ApplicationError.of(code,message)));
 }
}
