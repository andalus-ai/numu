package tech.kayys.numou.history.source.domain;
import java.time.Instant;
import java.util.Objects;

/** A proposition about history, deliberately separate from an event or entity fact. */
public record HistoricalClaim(HistoricalClaimId id, String subjectKind, String subjectId,
 String statement, Instant createdAt, long revision) {
 public HistoricalClaim {
  Objects.requireNonNull(id,"claim id"); subjectKind=required(subjectKind,"subjectKind",80);
  subjectId=required(subjectId,"subjectId",200); statement=required(statement,"statement",8000);
  Objects.requireNonNull(createdAt,"createdAt"); if(revision<1) throw new IllegalArgumentException("revision must be positive");
 }
 public static HistoricalClaim create(HistoricalClaimId id,String subjectKind,String subjectId,String statement,Instant now) {
  return new HistoricalClaim(id,subjectKind,subjectId,statement,now,1);
 }
 private static String required(String v,String field,int max) {
  if(v==null||v.isBlank()) throw new IllegalArgumentException(field+" is required");
  String s=v.trim(); if(s.length()>max) throw new IllegalArgumentException(field+" exceeds "+max+" characters"); return s;
 }
}