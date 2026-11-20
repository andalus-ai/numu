package tech.kayys.numou.advertising.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Creative metadata and review lifecycle; binary content is stored by the Documents capability. */
public record Creative(CreativeId id, TenantId tenantId, AdvertisingAccountId advertiserId,
                       String name, CreativeFormat format, String headline, String body,
                       URI destinationUri, String documentReference, CreativeStatus status,
                       String reviewNote, Instant createdAt, long version) {
  public Creative {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(advertiserId);
    name=AdvertiserAccount.required(name,"creative name");
    Objects.requireNonNull(format); Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    headline=optionalText(headline,200,"headline");
    body=optionalText(body,2000,"body");
    if(destinationUri!=null && (!destinationUri.isAbsolute() || destinationUri.getHost()==null ||
        !("http".equalsIgnoreCase(destinationUri.getScheme()) || "https".equalsIgnoreCase(destinationUri.getScheme())) ||
        destinationUri.toString().length()>2048))
      throw new IllegalArgumentException("destination must be an absolute HTTP(S) URI of at most 2048 characters");
    documentReference=optionalText(documentReference,512,"document reference");
    if(format!=CreativeFormat.TEXT && documentReference==null)
      throw new IllegalArgumentException("image, video and native creatives require a document reference");
    reviewNote=optionalText(reviewNote,1000,"review note");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static Creative draft(CreativeId id,TenantId tenant,AdvertisingAccountId advertiser,String name,
                               CreativeFormat format,String headline,String body,URI destination,String documentRef,Instant now) {
    return new Creative(id,tenant,advertiser,name,format,headline,body,destination,documentRef,
      CreativeStatus.DRAFT,null,now,0);
  }
  public Creative submit() {
    if(status!=CreativeStatus.DRAFT && status!=CreativeStatus.REJECTED)
      throw new IllegalStateException("only draft or rejected creatives can be submitted");
    if(destinationUri==null) throw new IllegalStateException("creative destination is required before submission");
    if(headline==null && body==null) throw new IllegalStateException("creative needs a headline or body");
    return next(CreativeStatus.SUBMITTED,null);
  }
  public Creative approve() {
    if(status!=CreativeStatus.SUBMITTED) throw new IllegalStateException("only submitted creatives can be approved");
    return next(CreativeStatus.APPROVED,null);
  }
  public Creative reject(String reason) {
    if(status!=CreativeStatus.SUBMITTED) throw new IllegalStateException("only submitted creatives can be rejected");
    return next(CreativeStatus.REJECTED,AdvertiserAccount.required(reason,"rejection reason"));
  }
  public Creative archive() {
    if(status==CreativeStatus.ARCHIVED) return this;
    if(status==CreativeStatus.SUBMITTED) throw new IllegalStateException("submitted creative must be reviewed before archiving");
    return next(CreativeStatus.ARCHIVED,reviewNote);
  }
  private Creative next(CreativeStatus next,String note) {
    if(status==CreativeStatus.ARCHIVED) throw new IllegalStateException("archived creative cannot transition");
    return new Creative(id,tenantId,advertiserId,name,format,headline,body,destinationUri,documentReference,
      next,note,createdAt,version+1);
  }
  private static String optionalText(String value,int max,String label) {
    if(value==null) return null;
    String v=value.trim();
    if(v.isEmpty()) return null;
    if(v.length()>max) throw new IllegalArgumentException(label+" exceeds "+max+" characters");
    return v;
  }
}
