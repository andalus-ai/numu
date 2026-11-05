package tech.kayys.numou.advertising.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Untrusted publisher-slot request after tenant scope has been resolved by a trusted host adapter. */
public record AdServingRequest(TenantId tenantId,AdPlacementId placementId,URI pageUri,String format) {
  public AdServingRequest {
    Objects.requireNonNull(tenantId,"tenantId");
    Objects.requireNonNull(placementId,"placementId");
    Objects.requireNonNull(pageUri,"pageUri");
    if(!pageUri.isAbsolute() || pageUri.getHost()==null || pageUri.getScheme()==null ||
       !(pageUri.getScheme().equalsIgnoreCase("http") || pageUri.getScheme().equalsIgnoreCase("https")) ||
       pageUri.toString().length()>2048)
      throw new IllegalArgumentException("page URI must be an absolute HTTP(S) URI with a host and at most 2048 characters");
    Objects.requireNonNull(format,"format");
    format=format.trim().toUpperCase(Locale.ROOT);
    if(format.isEmpty() || format.length()>100 || !format.matches("[A-Z0-9][A-Z0-9._-]*"))
      throw new IllegalArgumentException("invalid requested ad format");
  }
}
