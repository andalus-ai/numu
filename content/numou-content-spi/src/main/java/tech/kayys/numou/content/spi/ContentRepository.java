package tech.kayys.numou.content.spi;
import tech.kayys.numou.content.domain.*;
import java.util.Optional;
public interface ContentRepository {
 void create(ContentEntry entry);
 Optional<ContentEntry> find(TenantId tenantId, ContentId contentId);
 boolean appendRevision(ContentEntry revised, int expectedRevision);
 boolean updateStatus(TenantId tenantId, ContentId contentId, ContentStatus status, java.time.Instant updatedAt);
}