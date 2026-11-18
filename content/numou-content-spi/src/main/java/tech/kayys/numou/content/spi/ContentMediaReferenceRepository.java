package tech.kayys.numou.content.spi;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.media.*;
import java.util.List;
import java.util.UUID;
public interface ContentMediaReferenceRepository {
 boolean attach(ContentMediaReference reference);
 boolean detach(TenantId tenantId,UUID referenceId);
 List<ContentMediaReference> findByContent(TenantId tenantId,ContentId contentId,int revisionNumber);
}
