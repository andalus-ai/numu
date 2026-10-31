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
/** Validates tenant ownership and Party eligibility before persisting a proposal. */
public final class ProposeOrganizationPartyAssociationHandler {
 private final OrganizationRepository organizations;
 private final OrganizationPartyAssociationRepository associations;
 private final PartyOrganizationEligibilityPort parties;
 public ProposeOrganizationPartyAssociationHandler(OrganizationRepository organizations,
  OrganizationPartyAssociationRepository associations, PartyOrganizationEligibilityPort parties) {
  this.organizations=Objects.requireNonNull(organizations); this.associations=Objects.requireNonNull(associations);
  this.parties=Objects.requireNonNull(parties);
 }
 public CompletionStage<Result<OrganizationPartyAssociation>> handle(ProposeOrganizationPartyAssociationCommand c) {
  Objects.requireNonNull(c,"command"); Objects.requireNonNull(c.associationId(),"associationId");
  Objects.requireNonNull(c.tenantId(),"tenantId"); Objects.requireNonNull(c.organizationId(),"organizationId");
  Objects.requireNonNull(c.partyId(),"partyId"); Objects.requireNonNull(c.proposedAt(),"proposedAt");
  Objects.requireNonNull(c.actorId(),"actorId");
  // Tenant ownership is established before reading the idempotency key.
  return organizations.findById(c.organizationId()).thenCompose(found -> {
   if(found.isEmpty()) return failure("ORGANIZATION_NOT_FOUND","Organization was not found");
   Organization org=found.get();
   if(!org.getTenantId().equals(c.tenantId())) return failure("ORGANIZATION_TENANT_MISMATCH",
    "Organization does not belong to the supplied tenant");
   if(!org.isActive()) return failure("ORGANIZATION_NOT_ACTIVE","Only an active Organization can be associated");
   return associations.findById(c.associationId()).thenCompose(existing -> {
    if(existing.isPresent()) {
     OrganizationPartyAssociation prior=existing.get();
     if(prior.organizationId().equals(c.organizationId()) && prior.partyId().equals(c.partyId()))
      return CompletableFuture.completedFuture(Result.success(prior));
     return failure("ORGANIZATION_PARTY_IDEMPOTENCY_CONFLICT",
      "The associationId is already used for a different Organization or Party");
    }
    return parties.findEligibility(c.partyId()).thenCompose(e -> {
     if(e==null || !e.canRepresentOrganization()) return failure("PARTY_NOT_ELIGIBLE_FOR_ORGANIZATION",
      "Party must exist, be active, and have kind ORGANIZATION");
     return associations.findCurrentByOrganizationId(c.organizationId()).thenCompose(byOrg -> {
      if(byOrg.isPresent()) return failure("ORGANIZATION_ALREADY_ASSOCIATED",
       "Organization already has a current Party association");
      return associations.findCurrentByPartyId(c.partyId()).thenCompose(byParty -> {
       if(byParty.isPresent()) return failure("PARTY_ALREADY_ASSOCIATED",
        "Party already has a current Organization association");
       OrganizationPartyAssociation proposal=OrganizationPartyAssociation.propose(c.associationId(),
        c.organizationId(),c.partyId(),c.proposedAt(),c.actorId(),c.evidenceReference());
       return associations.save(proposal).thenApply(Result::success);
      });
     });
    });
   });
  });
 }
 private static <T> CompletionStage<Result<T>> failure(String code,String message) {
  return CompletableFuture.completedFuture(Result.failure(ApplicationError.of(code,message)));
 }
}
