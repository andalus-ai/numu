package tech.kayys.numou.organization.application.port;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import java.util.concurrent.CompletionStage;
/** Public cross-capability query contract; adapters must use Ecosystem's public API. */
public interface PartyOrganizationEligibilityPort {
 CompletionStage<Eligibility> findEligibility(PartyId partyId);
 record Eligibility(boolean exists, boolean active, boolean organizationKind) {
  public boolean canRepresentOrganization() { return exists && active && organizationKind; }
 }
}
