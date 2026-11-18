package tech.kayys.numou.history.source.domain;
import java.net.URI; import java.time.Instant; import java.util.Objects; import java.util.UUID;
/** A particular custody/catalogue manifestation at an institution; institution identity stays externally owned. */
public record ArchivalHolding(UUID id,HistoricalSourceId sourceId,String institutionRef,String institutionName,
 String repositoryName,String collectionName,String shelfmark,ArchivalHoldingType holdingType,String catalogUri,
 String accessConditions,String notes,Instant recordedAt) {
 public ArchivalHolding {Objects.requireNonNull(id);Objects.requireNonNull(sourceId);institutionRef=optional(institutionRef,500);institutionName=required(institutionName,"institutionName",500);repositoryName=optional(repositoryName,500);collectionName=optional(collectionName,500);shelfmark=required(shelfmark,"shelfmark",500);Objects.requireNonNull(holdingType);catalogUri=uri(catalogUri);accessConditions=optional(accessConditions,2000);notes=optional(notes,4000);Objects.requireNonNull(recordedAt);}
 public static ArchivalHolding create(HistoricalSourceId sourceId,String institutionRef,String institutionName,String repositoryName,String collectionName,String shelfmark,ArchivalHoldingType type,String catalogUri,String accessConditions,String notes,Instant now){return new ArchivalHolding(UUID.randomUUID(),sourceId,institutionRef,institutionName,repositoryName,collectionName,shelfmark,type,catalogUri,accessConditions,notes,now);}
 private static String required(String v,String f,int n){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>n)throw new IllegalArgumentException(f+" exceeds "+n+" characters");return s;}
 private static String optional(String v,int n){if(v==null||v.isBlank())return null;String s=v.trim();if(s.length()>n)throw new IllegalArgumentException("value exceeds "+n+" characters");return s;}
 private static String uri(String v){String s=optional(v,2000);if(s==null)return null;URI u=URI.create(s);if(!u.isAbsolute()||u.getScheme()==null||!(u.getScheme().equalsIgnoreCase("http")||u.getScheme().equalsIgnoreCase("https")||u.getScheme().equalsIgnoreCase("urn")))throw new IllegalArgumentException("catalog URI must use http, https or urn");return u.toASCIIString();}
}
