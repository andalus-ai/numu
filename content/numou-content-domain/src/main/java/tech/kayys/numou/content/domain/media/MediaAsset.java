package tech.kayys.numou.content.domain.media;

import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** CMS-owned descriptive metadata and lifecycle for an immutable Document version. */
public final class MediaAsset {
 private static final Pattern SHA256=Pattern.compile("(?i)[0-9a-f]{64}");
 private final MediaAssetId id; private final TenantId tenantId;
 private final String documentId, documentVersionId, fileName, contentType, sha256;
 private final MediaKind kind; private final long byteSize;
 private final Instant createdAt; private final String createdBy;
 private final Integer width,height; private final Long durationMillis;
 private final MediaScanStatus scanStatus; private final MediaLifecycleStatus status;
 private final String altText,caption; private final Instant updatedAt;
 private MediaAsset(MediaAssetId id,TenantId tenantId,String documentId,String documentVersionId,
  String fileName,String contentType,MediaKind kind,long byteSize,String sha256,
  Integer width,Integer height,Long durationMillis,MediaScanStatus scanStatus,
  MediaLifecycleStatus status,String altText,String caption,String createdBy,Instant createdAt,Instant updatedAt){
  this.id=Objects.requireNonNull(id);this.tenantId=Objects.requireNonNull(tenantId);
  this.documentId=text(documentId,"documentId",128);this.documentVersionId=text(documentVersionId,"documentVersionId",128);
  this.fileName=text(fileName,"fileName",512);this.contentType=text(contentType,"contentType",255);
  this.kind=Objects.requireNonNull(kind);if(byteSize<0)throw new IllegalArgumentException("byteSize must be non-negative");this.byteSize=byteSize;
  if(sha256==null||!SHA256.matcher(sha256).matches())throw new IllegalArgumentException("sha256 must be a 64-character hexadecimal digest");this.sha256=sha256.toLowerCase();
  if(width!=null&&width<1||height!=null&&height<1||durationMillis!=null&&durationMillis<0)throw new IllegalArgumentException("Invalid media dimensions or duration");
  if(kind==MediaKind.IMAGE&&(width==null||height==null)) { /* dimensions may be populated by a later trusted probe */ }
  this.width=width;this.height=height;this.durationMillis=durationMillis;
  this.scanStatus=Objects.requireNonNull(scanStatus);this.status=Objects.requireNonNull(status);
  this.altText=bounded(altText,1000,"altText");this.caption=bounded(caption,4000,"caption");
  this.createdBy=text(createdBy,"createdBy",128);this.createdAt=Objects.requireNonNull(createdAt);this.updatedAt=Objects.requireNonNull(updatedAt);
  if(status==MediaLifecycleStatus.AVAILABLE&&scanStatus!=MediaScanStatus.CLEAN)throw new IllegalArgumentException("Only clean media may be available");
  if(status==MediaLifecycleStatus.REJECTED&&scanStatus!=MediaScanStatus.INFECTED)throw new IllegalArgumentException("Rejected media must have an infected verdict");
 }
 public static MediaAsset register(TenantId tenantId,String documentId,String documentVersionId,String fileName,String contentType,MediaKind kind,long byteSize,String sha256,String createdBy,Instant now){
  return new MediaAsset(MediaAssetId.generate(),tenantId,documentId,documentVersionId,fileName,contentType,kind,byteSize,sha256,null,null,null,MediaScanStatus.PENDING,MediaLifecycleStatus.QUARANTINED,null,null,createdBy,now,now);
 }
 public static MediaAsset rehydrate(MediaAssetId id,TenantId tenantId,String documentId,String documentVersionId,String fileName,String contentType,MediaKind kind,long byteSize,String sha256,Integer width,Integer height,Long durationMillis,MediaScanStatus scanStatus,MediaLifecycleStatus status,String altText,String caption,String createdBy,Instant createdAt,Instant updatedAt){
  return new MediaAsset(id,tenantId,documentId,documentVersionId,fileName,contentType,kind,byteSize,sha256,width,height,durationMillis,scanStatus,status,altText,caption,createdBy,createdAt,updatedAt);
 }
 public MediaAsset recordScan(MediaScanStatus verdict,Instant now){
  Objects.requireNonNull(verdict);if(verdict==MediaScanStatus.PENDING)throw new IllegalArgumentException("A scan verdict cannot be PENDING");
  if(scanStatus==MediaScanStatus.CLEAN||scanStatus==MediaScanStatus.INFECTED){if(scanStatus==verdict)return this;throw new IllegalStateException("Terminal scan verdict cannot be replaced");}
  MediaLifecycleStatus next=verdict==MediaScanStatus.CLEAN?MediaLifecycleStatus.AVAILABLE:verdict==MediaScanStatus.INFECTED?MediaLifecycleStatus.REJECTED:MediaLifecycleStatus.QUARANTINED;
  return copy(width,height,durationMillis,verdict,next,altText,caption,now);
 }
 public MediaAsset describe(String altText,String caption,Integer width,Integer height,Long durationMillis,Instant now){
  if(status==MediaLifecycleStatus.ARCHIVED||status==MediaLifecycleStatus.REJECTED)throw new IllegalStateException("Archived or rejected media cannot be edited");
  return copy(width,height,durationMillis,scanStatus,status,altText,caption,now);
 }
 public MediaAsset archive(Instant now){if(status==MediaLifecycleStatus.ARCHIVED)return this;return copy(width,height,durationMillis,scanStatus,MediaLifecycleStatus.ARCHIVED,altText,caption,now);}
 private MediaAsset copy(Integer w,Integer h,Long d,MediaScanStatus scan,MediaLifecycleStatus lifecycle,String alt,String cap,Instant now){
  return new MediaAsset(id,tenantId,documentId,documentVersionId,fileName,contentType,kind,byteSize,sha256,w,h,d,scan,lifecycle,alt,cap,createdBy,createdAt,now);
 }
 private static String text(String v,String n,int max){if(v==null||v.isBlank()||v.length()>max)throw new IllegalArgumentException(n+" must contain 1 to "+max+" characters");return v;}
 private static String bounded(String v,int max,String n){if(v!=null&&v.length()>max)throw new IllegalArgumentException(n+" must not exceed "+max+" characters");return v;}
 public MediaAssetId id(){return id;} public TenantId tenantId(){return tenantId;} public String documentId(){return documentId;} public String documentVersionId(){return documentVersionId;}
 public String fileName(){return fileName;} public String contentType(){return contentType;} public MediaKind kind(){return kind;} public long byteSize(){return byteSize;} public String sha256(){return sha256;}
 public Integer width(){return width;} public Integer height(){return height;} public Long durationMillis(){return durationMillis;} public MediaScanStatus scanStatus(){return scanStatus;} public MediaLifecycleStatus status(){return status;}
 public String altText(){return altText;} public String caption(){return caption;} public String createdBy(){return createdBy;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}
