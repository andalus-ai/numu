package tech.kayys.numou.content.spi;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.media.*;
import java.util.Optional;
public interface MediaAssetRepository {
 void create(MediaAsset asset);
 Optional<MediaAsset> find(TenantId tenantId,MediaAssetId id);
 Optional<MediaAsset> findByDocumentVersion(TenantId tenantId,String documentId,String documentVersionId);
 boolean update(MediaAsset asset,MediaScanStatus expectedScanStatus,MediaLifecycleStatus expectedStatus);
}
