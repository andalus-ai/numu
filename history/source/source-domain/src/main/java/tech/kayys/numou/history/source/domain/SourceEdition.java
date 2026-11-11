package tech.kayys.numou.history.source.domain;
import java.net.URI; import java.time.Instant; import java.util.Locale; import java.util.Objects; import java.util.UUID;
/** A published or otherwise identifiable manifestation/edition of a source work. */
public record SourceEdition(UUID id,HistoricalSourceId sourceId,String editionDesignation,String publisher,
 String publicationPlace,String publicationDateText,String languageTag,String identifier,String canonicalUri,
 String notes,Instant recordedAt) {
 public SourceEdition { Objects.requireNonNull(id);Objects.requireNonNull(sourceId);editionDesignation=required(editionDesignation,"editionDesignation",500);publisher=optional(publisher,500);publicationPlace=optional(publicationPlace,300);publicationDateText=optional(publicationDateText,200);languageTag=language(languageTag);identifier=optional(identifier,300);canonicalUri=uri(canonicalUri);notes=optional(notes,4000);Objects.requireNonNull(recordedAt); }
 public static SourceEdition create(HistoricalSourceId sourceId,String designation,String publisher,String place,String date,String language,String identifier,String uri,String notes,Instant now){return new SourceEdition(UUID.randomUUID(),sourceId,designation,publisher,place,date,language,identifier,uri,notes,now);}
 private static String required(String v,String f,int n){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>n)throw new IllegalArgumentException(f+" exceeds "+n+" characters");return s;}
 private static String optional(String v,int n){if(v==null||v.isBlank())return null;String s=v.trim();if(s.length()>n)throw new IllegalArgumentException("value exceeds "+n+" characters");return s;}
 private static String language(String v){String s=optional(v,64);if(s==null)return null;var l=Locale.forLanguageTag(s);if(l.getLanguage().isBlank()||!l.toLanguageTag().equalsIgnoreCase(s))throw new IllegalArgumentException("languageTag must be BCP 47");return l.toLanguageTag();}
 private static String uri(String v){String s=optional(v,2000);if(s==null)return null;URI u=URI.create(s);if(!u.isAbsolute()||u.getScheme()==null||!(u.getScheme().equalsIgnoreCase("http")||u.getScheme().equalsIgnoreCase("https")||u.getScheme().equalsIgnoreCase("urn")||u.getScheme().equalsIgnoreCase("doi")))throw new IllegalArgumentException("edition URI must use http, https, urn or doi");return u.toASCIIString();}
}
