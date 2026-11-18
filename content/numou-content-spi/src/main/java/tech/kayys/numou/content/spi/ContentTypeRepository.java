package tech.kayys.numou.content.spi;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import tech.kayys.numou.content.domain.model.ContentTypeSchema;
import java.util.Optional;
public interface ContentTypeRepository {
 void createType(ContentTypeSchema firstVersion);
 void createVersion(ContentTypeSchema nextVersion);
 Optional<ContentTypeSchema> findVersion(TenantId tenantId, ContentTypeId typeId, int version);
 boolean publish(TenantId tenantId, ContentTypeId typeId, int version, java.time.Instant publishedAt);
}
