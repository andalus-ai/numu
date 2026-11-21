package tech.kayys.numou.advertising.domain;

import java.util.Objects;
import java.util.Optional;

/** Public serving outcome. No-fill is a successful, explicit result rather than an exception. */
public record AdServingResult(AdDecision decision,Optional<Creative> creative) {
  public AdServingResult {
    Objects.requireNonNull(decision);creative=Objects.requireNonNull(creative);
    if(decision.status()==AdDecisionStatus.FILLED && creative.isEmpty())
      throw new IllegalArgumentException("filled result requires creative metadata");
    if(decision.status()==AdDecisionStatus.NO_FILL && creative.isPresent())
      throw new IllegalArgumentException("no-fill result cannot include creative metadata");
    if(creative.isPresent() && !creative.get().id().equals(decision.creativeId()))
      throw new IllegalArgumentException("creative does not match decision");
  }
  public static AdServingResult filled(AdDecision decision,Creative creative) {
    return new AdServingResult(decision,Optional.of(creative));
  }
  public static AdServingResult noFill(AdDecision decision) {
    return new AdServingResult(decision,Optional.empty());
  }
}
