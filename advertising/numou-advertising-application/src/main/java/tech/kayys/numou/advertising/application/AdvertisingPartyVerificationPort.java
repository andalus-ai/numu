package tech.kayys.numou.advertising.application;
import tech.kayys.numou.advertising.domain.PartyReference;
import java.util.concurrent.CompletionStage;
/** Verifies that an opaque Party reference resolves to an active canonical Party owned by Ecosystem. */
public interface AdvertisingPartyVerificationPort {
  CompletionStage<Boolean> isActiveParty(PartyReference partyReference);
}
