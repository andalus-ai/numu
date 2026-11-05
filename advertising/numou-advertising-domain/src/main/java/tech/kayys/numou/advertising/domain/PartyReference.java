package tech.kayys.numou.advertising.domain;
import java.util.Objects;
/** Stable opaque reference to a Party/Organization owned by another capability. */
public record PartyReference(String value) {
  public PartyReference {
    Objects.requireNonNull(value, "party reference");
    value=value.trim();
    if(value.isEmpty() || value.length()>255) throw new IllegalArgumentException("party reference must contain 1..255 characters");
  }
}
