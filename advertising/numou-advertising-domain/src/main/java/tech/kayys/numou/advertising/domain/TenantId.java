package tech.kayys.numou.advertising.domain;
import java.util.Objects;
/** Opaque tenant scope. Advertising never resolves or owns tenant identity. */
public record TenantId(String value) {
  public TenantId { Objects.requireNonNull(value, "tenant id"); value=value.trim(); if(value.isEmpty()) throw new IllegalArgumentException("tenant id cannot be blank"); }
}
