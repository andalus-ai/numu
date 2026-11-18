package tech.kayys.numou.advertising.adapter;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.advertising.domain.PartyReference;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class EcosystemPartyVerificationAdapterTest {
  @Test void resolvesActiveAndInactiveCanonicalParties() {
    PartyId id=PartyId.generate();
    Party active=Party.rehydrate(id,PartyKind.ORGANIZATION,PartyStatus.ACTIVE);
    PartyLookupPort lookup=requested->Uni.createFrom().item(Optional.of(active));
    EcosystemPartyVerificationAdapter adapter=new EcosystemPartyVerificationAdapter(lookup);
    assertTrue(adapter.isActiveParty(new PartyReference(id.toString())).toCompletableFuture().join());

    Party inactive=Party.rehydrate(id,PartyKind.ORGANIZATION,PartyStatus.INACTIVE);
    EcosystemPartyVerificationAdapter inactiveAdapter=new EcosystemPartyVerificationAdapter(
      requested->Uni.createFrom().item(Optional.of(inactive)));
    assertFalse(inactiveAdapter.isActiveParty(new PartyReference(id.toString())).toCompletableFuture().join());
  }

  @Test void rejectsMalformedOrUnknownPartyReferences() {
    PartyLookupPort lookup=requested->Uni.createFrom().item(Optional.empty());
    EcosystemPartyVerificationAdapter adapter=new EcosystemPartyVerificationAdapter(lookup);
    assertFalse(adapter.isActiveParty(new PartyReference("not-a-uuid")).toCompletableFuture().join());
    assertFalse(adapter.isActiveParty(new PartyReference("00000000-0000-0000-0000-000000000000")).toCompletableFuture().join());
  }
}
