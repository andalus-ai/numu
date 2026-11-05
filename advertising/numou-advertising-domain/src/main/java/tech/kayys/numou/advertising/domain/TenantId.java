package tech.kayys.numou.advertising.domain;
import java.util.Objects;
/** Opaque tenant scope. Advertising never resolves or owns tenant identity. */
public record TenantId(String value) {
  public TenantId {
    Objects.requireNonNull(value, "tenant id");
    value=value.trim();
    if(value.isEmpty() || value.length()>200) throw new IllegalArgumentException("tenant id must contain 1..200 characters");
  }
}
