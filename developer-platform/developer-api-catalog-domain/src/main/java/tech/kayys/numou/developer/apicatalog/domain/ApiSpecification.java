package tech.kayys.numou.developer.apicatalog.domain;
import java.util.Locale;
import java.util.Objects;
/** Immutable pointer to externally stored, validated OpenAPI bytes. */
public record ApiSpecification(String openApiVersion, String artifactReference, String sha256) {
 public ApiSpecification {
  openApiVersion=text(openApiVersion,"openApiVersion"); artifactReference=text(artifactReference,"artifactReference"); sha256=text(sha256,"sha256").toLowerCase(Locale.ROOT);
  if(!openApiVersion.equals("3.0")&&!openApiVersion.equals("3.1")) throw new IllegalArgumentException("only OpenAPI 3.0 and 3.1 are supported");
  if(!sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("sha256 must be a 64-character hexadecimal digest");
  if(artifactReference.length()>1024||artifactReference.contains("..")) throw new IllegalArgumentException("artifactReference is invalid");
 }
 private static String text(String v,String f){Objects.requireNonNull(v,f+" cannot be null");String n=v.trim();if(n.isEmpty())throw new IllegalArgumentException(f+" cannot be blank");return n;}
}