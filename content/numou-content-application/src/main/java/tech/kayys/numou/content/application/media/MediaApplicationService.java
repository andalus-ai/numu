package tech.kayys.numou.content.application.media;
import tech.kayys.numou.content.application.ContentCommandContext;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.media.*;
import tech.kayys.numou.content.spi.*;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
/** CMS media metadata and revision-reference use cases; binary bytes remain in Document storage. */
public final class MediaApplicationService {
 private final MediaAssetRepository assets;private final ContentMediaReferenceRepository references;private final ContentRepository content;private final Clock clock;
 public MediaApplicationService(MediaAssetRepository assets,ContentMediaReferenceRepository references,ContentRepository content,Clock clock){this.assets=Objects.requireNonNull(assets);this.references=Objects.requireNonNull(references);this.content=Objects.requireNonNull(content);this.clock=Objects.requireNonNull(clock);}
 public MediaAsset registerUploadedDocument(ContentCommandContext context,String documentId,String versionId,String fileName,String contentType,MediaKind kind,long size,String sha256){
  var asset=MediaAsset.register(context.tenantId(),documentId,versionId,fileName,contentType,kind,size,sha256,context.actorId().toString(),clock.instant());assets.create(asset);return asset;
 }
 public MediaAsset recordScanVerdict(ContentCommandContext context,MediaAssetId id,MediaScanStatus verdict){
  var current=assets.find(context.tenantId(),id).orElseThrow(()->new IllegalArgumentException("Media asset not found"));
  var next=current.recordScan(verdict,clock.instant());
  if(next==current)return current;
  if(!assets.update(next,current.scanStatus(),current.status()))throw new IllegalStateException("Media scan state changed concurrently");
  return next;
 }
 public MediaAsset applyDocumentScanVerdict(tech.kayys.numou.content.domain.TenantId tenant,String documentId,String versionId,MediaScanStatus verdict){
  var current=assets.findByDocumentVersion(tenant,documentId,versionId).orElseThrow(()->new IllegalArgumentException("Media asset for Document version not found"));
  var next=current.recordScan(verdict,clock.instant());if(next==current)return current;
  if(!assets.update(next,current.scanStatus(),current.status()))throw new IllegalStateException("Media scan state changed concurrently");return next;
 }
 public MediaAsset describe(ContentCommandContext context,MediaAssetId id,String altText,String caption,Integer width,Integer height,Long durationMillis){
  var current=assets.find(context.tenantId(),id).orElseThrow(()->new IllegalArgumentException("Media asset not found"));
  var next=current.describe(altText,caption,width,height,durationMillis,clock.instant());
  if(!assets.update(next,current.scanStatus(),current.status()))throw new IllegalStateException("Media metadata changed concurrently");
  return next;
 }
 public ContentMediaReference attach(ContentCommandContext context,MediaAssetId assetId,ContentId contentId,int revision,String role){
  var contentEntry=content.find(context.tenantId(),contentId).orElseThrow(()->new IllegalArgumentException("Content not found"));
  if(revision<1||revision>contentEntry.currentRevisionNumber())throw new IllegalArgumentException("Content revision not found");
  var asset=assets.find(context.tenantId(),assetId).orElseThrow(()->new IllegalArgumentException("Media asset not found"));
  if(asset.status()!=MediaLifecycleStatus.AVAILABLE||asset.scanStatus()!=MediaScanStatus.CLEAN)throw new IllegalStateException("Media cannot be attached until its exact document version has a CLEAN scan verdict");
  var ref=ContentMediaReference.create(context.tenantId(),contentId,revision,assetId,role,clock.instant());
  if(!references.attach(ref))throw new IllegalStateException("Media reference already exists or could not be attached");
  return ref;
 }
 public boolean detach(ContentCommandContext context,UUID referenceId){return references.detach(context.tenantId(),referenceId);}
 public List<ContentMediaReference> findByContent(ContentCommandContext context,ContentId id,int revision){return references.findByContent(context.tenantId(),id,revision);}
 public MediaAsset get(ContentCommandContext context,MediaAssetId id){return assets.find(context.tenantId(),id).orElseThrow(()->new IllegalArgumentException("Media asset not found"));}
}
