package tech.kayys.numou.advertising.domain;

import java.util.Objects;
import java.util.UUID;

public record AdvertisingAccountId(UUID value) {
  public AdvertisingAccountId { Objects.requireNonNull(value, "account id"); }
  public static AdvertisingAccountId generate(){ return new AdvertisingAccountId(UUID.randomUUID()); }
}
