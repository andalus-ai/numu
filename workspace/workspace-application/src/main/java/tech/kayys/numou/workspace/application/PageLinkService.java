package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageLink;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authorized page-link use cases; backlinks are derived from persisted directed links. */
public final class PageLinkService {
 private final PageRepository pages; private final PageLinkRepository links; private final AccessPolicy access; private final AuditSink audit; private final Clock clock;
 public PageLinkService(PageRepository pages,PageLinkRepository links,AccessPolicy access,AuditSink audit,Clock clock) {
  this.pages=Objects.requireNonNull(pages);this.links=Objects.requireNonNull(links);this.access=Objects.requireNonNull(access);this.audit=Objects.requireNonNull(audit);this.clock=Objects.requireNonNull(clock);
 }
 public PageLink link(UUID tenantId,UUID actorId,UUID sourcePageId,UUID targetPageId,String label) {
  identity(tenantId,actorId);Page source=pages.findPage(tenantId,sourcePageId).orElseThrow(()->new IllegalArgumentException("Source page not found for tenant"));
  Page target=pages.findPage(tenantId,targetPageId).orElseThrow(()->new IllegalArgumentException("Target page not found for tenant"));
  access.require(tenantId,actorId,Resource.PAGE,source.id(),Action.UPDATE);
  access.require(tenantId,actorId,Resource.PAGE,target.id(),Action.READ);
  PageLink link=PageLink.create(tenantId,sourcePageId,targetPageId,actorId,label,clock.instant());
  if(!links.save(link))throw new IllegalStateException("Page link already exists or could not be saved");
  audit.record(new AuditEntry(tenantId,actorId,"PAGE_LINK_CREATED","PAGE_LINK",link.id(),0,source.title()+" -> "+target.title(),clock.instant()));
  return link;
 }
 public void unlink(UUID tenantId,UUID actorId,UUID linkId) {
  identity(tenantId,actorId);PageLink link=links.find(tenantId,linkId).orElseThrow(()->new IllegalArgumentException("Page link not found for tenant"));
  pages.findPage(tenantId,link.sourcePageId()).orElseThrow(()->new IllegalArgumentException("Source page not found for tenant"));
  access.require(tenantId,actorId,Resource.PAGE,link.sourcePageId(),Action.UPDATE);
  if(!links.delete(tenantId,linkId))throw new IllegalStateException("Page link could not be removed");
  audit.record(new AuditEntry(tenantId,actorId,"PAGE_LINK_REMOVED","PAGE_LINK",link.id(),0,link.sourcePageId()+" -> "+link.targetPageId(),clock.instant()));
 }
 public List<PageLink> outgoing(UUID tenantId,UUID actorId,UUID pageId) {
  identity(tenantId,actorId);pages.findPage(tenantId,pageId).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));
  access.require(tenantId,actorId,Resource.PAGE,pageId,Action.READ);List<PageLink> result=links.outgoing(tenantId,pageId);
  result.forEach(l->access.require(tenantId,actorId,Resource.PAGE,l.targetPageId(),Action.READ));return result;
 }
 public List<Page> backlinks(UUID tenantId,UUID actorId,UUID pageId) {
  identity(tenantId,actorId);pages.findPage(tenantId,pageId).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));
  access.require(tenantId,actorId,Resource.PAGE,pageId,Action.READ);
  return links.backlinks(tenantId,pageId).stream().map(PageLink::sourcePageId).distinct()
   .map(id->pages.findPage(tenantId,id).orElse(null)).filter(Objects::nonNull)
   .filter(page->{try{access.require(tenantId,actorId,Resource.PAGE,page.id(),Action.READ);return true;}catch(SecurityException denied){return false;}}).toList();
 }
 private static void identity(UUID tenantId,UUID actorId){Objects.requireNonNull(tenantId,"tenantId");Objects.requireNonNull(actorId,"actorId");}
}
