package tech.kayys.numou.history.source.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Immutable-revision metadata for a historical source; binary content remains in the shared document capability. */
public record HistoricalSource(
 HistoricalSourceId id, HistoricalSourceKind kind, String title, String subtitle,
 String description, String canonicalUri, String publisher, String publicationDateText,
 String languageTag, Instant createdAt, Instant updatedAt, long revision, HistoricalSourceStatus status) {

 public HistoricalSource {
  Objects.requireNonNull(id, "source id"); Objects.requireNonNull(kind, "source kind");
  title = required(title, "title"); subtitle = optional(subtitle); description = optional(description);
  canonicalUri = validateUri(canonicalUri); publisher = optional(publisher);
  publicationDateText = optional(publicationDateText);
  if (publicationDateText != null && publicationDateText.length() > 1000) throw new IllegalArgumentException("publicationDateText exceeds 1000 characters");
  languageTag = validateLanguage(languageTag);
  Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
  if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
  if (revision < 1) throw new IllegalArgumentException("revision must be positive");
  Objects.requireNonNull(status, "status");
 }
 public static HistoricalSource register(HistoricalSourceId id, HistoricalSourceKind kind, String title,
   String subtitle, String description, String canonicalUri, String publisher, String publicationDateText,
   String languageTag, Instant now) {
  return new HistoricalSource(id,kind,title,subtitle,description,canonicalUri,publisher,publicationDateText,
    languageTag,now,now,1,HistoricalSourceStatus.ACTIVE);
 }
 public HistoricalSource revise(HistoricalSourceKind nextKind, String nextTitle, String nextSubtitle,
   String nextDescription, String nextUri, String nextPublisher, String nextPublicationDate,
   String nextLanguageTag, Instant now) {
  if (status == HistoricalSourceStatus.ARCHIVED) throw new IllegalStateException("archived source cannot be revised");
  if (now.isBefore(updatedAt)) throw new IllegalArgumentException("revision time cannot move backwards");
  return new HistoricalSource(id,nextKind,nextTitle,nextSubtitle,nextDescription,nextUri,nextPublisher,
    nextPublicationDate,nextLanguageTag,createdAt,now,Math.addExact(revision,1),status);
 }
 public HistoricalSource archive(Instant now) {
  if (status == HistoricalSourceStatus.ARCHIVED) return this;
  if (now.isBefore(updatedAt)) throw new IllegalArgumentException("archive time cannot move backwards");
  return new HistoricalSource(id,kind,title,subtitle,description,canonicalUri,publisher,publicationDateText,
    languageTag,createdAt,now,Math.addExact(revision,1),HistoricalSourceStatus.ARCHIVED);
 }
 private static String required(String value,String field) {
  if(value==null || value.isBlank()) throw new IllegalArgumentException(field+" is required");
  String v=value.trim(); if(v.length()>1000) throw new IllegalArgumentException(field+" exceeds 1000 characters"); return v;
 }
 private static String optional(String value) {
  if(value==null || value.isBlank()) return null;
  String v=value.trim(); if(v.length()>4000) throw new IllegalArgumentException("optional metadata exceeds 4000 characters"); return v;
 }
 private static String validateUri(String value) {
  String v=optional(value); if(v==null) return null;
  if(v.length()>2000) throw new IllegalArgumentException("canonical URI exceeds 2000 characters");
  URI uri;
  try { uri=URI.create(v); } catch(IllegalArgumentException e) { throw new IllegalArgumentException("canonical URI is invalid",e); }
  String scheme=uri.getScheme();
  if(!uri.isAbsolute() || scheme==null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("urn") || scheme.equalsIgnoreCase("doi")))
   throw new IllegalArgumentException("canonical URI must use http, https, urn or doi");
  String ascii=uri.toASCIIString();
  if(ascii.length()>2000) throw new IllegalArgumentException("canonical URI exceeds 2000 ASCII characters");
  return ascii;
 }
 private static String validateLanguage(String value) {
  String v=optional(value); if(v==null) return null;
  if(v.length()>64) throw new IllegalArgumentException("languageTag exceeds 64 characters");
  Locale locale=Locale.forLanguageTag(v);
  if(locale.getLanguage().isBlank() || !locale.toLanguageTag().equalsIgnoreCase(v))
   throw new IllegalArgumentException("languageTag must be a valid BCP 47 language tag");
  return locale.toLanguageTag();
 }
}