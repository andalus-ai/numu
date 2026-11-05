package tech.kayys.numou.developer.apicatalog.domain;
import java.util.*;
/** Immutable API contract version; published versions cannot be edited in place. */
public record ApiVersion(String version, ApiSpecification specification, Map<String,ApiScope> scopes, ApiVersionStatus status) {
 public ApiVersion {
  version=text(version,"version");
  if(!version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?")) throw new IllegalArgumentException("version must be semantic version (major.minor.patch)");
  Objects.requireNonNull(specification,"specification cannot be null"); Objects.requireNonNull(scopes,"scopes cannot be null"); Objects.requireNonNull(status,"status cannot be null");
  TreeMap<String,ApiScope> copy=new TreeMap<>(); scopes.forEach((k,v)->{Objects.requireNonNull(v,"scope cannot be null");if(!k.equals(v.name()))throw new IllegalArgumentException("scope map key must match scope name");if(copy.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("duplicate scope: "+k);}); scopes=Map.copyOf(copy);
 }
 public static ApiVersion draft(String version,ApiSpecification spec,Set<ApiScope> scopes){Objects.requireNonNull(scopes,"scopes cannot be null");Map<String,ApiScope> indexed=new TreeMap<>();for(ApiScope s:scopes){Objects.requireNonNull(s,"scope cannot be null");if(indexed.putIfAbsent(s.name(),s)!=null)throw new IllegalArgumentException("duplicate scope: "+s.name());}return new ApiVersion(version,spec,indexed,ApiVersionStatus.DRAFT);}
 public ApiVersion publish(){if(status!=ApiVersionStatus.DRAFT)throw new IllegalStateException("only draft versions can be published");return new ApiVersion(version,specification,scopes,ApiVersionStatus.PUBLISHED);}
 public ApiVersion deprecate(){if(status!=ApiVersionStatus.PUBLISHED)throw new IllegalStateException("only published versions can be deprecated");return new ApiVersion(version,specification,scopes,ApiVersionStatus.DEPRECATED);}
 public ApiVersion retire(){if(status!=ApiVersionStatus.DEPRECATED)throw new IllegalStateException("only deprecated versions can be retired");return new ApiVersion(version,specification,scopes,ApiVersionStatus.RETIRED);}
 private static String text(String v,String f){Objects.requireNonNull(v,f+" cannot be null");String n=v.trim();if(n.isEmpty())throw new IllegalArgumentException(f+" cannot be blank");return n;}
}