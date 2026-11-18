package tech.kayys.numou.content.spi;
import tech.kayys.numou.content.domain.*;
import java.time.Instant;
import java.util.Optional;
public interface ContentRepository {
 void create(ContentEntry entry);
 Optional<ContentEntry> find(TenantId tenantId, ContentId contentId);
 boolean appendRevision(ContentEntry revised, int expectedRevision);
 /** Tenant-scoped compare-and-set; prevents stale commands overwriting a concurrent lifecycle transition. */
 boolean transitionStatus(TenantId tenantId, ContentId contentId, ContentStatus expected, ContentStatus target, Instant updatedAt);
}
