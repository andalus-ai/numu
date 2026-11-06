package tech.kayys.numou.advertising.domain;

import java.util.Objects;

/** Canonical inventory ownership context resolved from a tenant-scoped placement. */
public record AdServingInventory(AdPlacement placement,PublisherProperty property,PublisherAccount publisher) {
  public AdServingInventory {
    Objects.requireNonNull(placement);Objects.requireNonNull(property);Objects.requireNonNull(publisher);
    if(!placement.tenantId().equals(property.tenantId()) || !property.tenantId().equals(publisher.tenantId()) ||
       !placement.propertyId().equals(property.id()) || !property.publisherId().equals(publisher.id()))
      throw new IllegalArgumentException("placement, property and publisher ownership must match");
  }
}
