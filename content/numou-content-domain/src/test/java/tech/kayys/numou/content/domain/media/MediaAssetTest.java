package tech.kayys.numou.content.domain.media;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MediaAssetTest {
 private final TenantId tenant=new TenantId(UUID.randomUUID());private final Instant now=Instant.parse("2026-01-01T00:00:00Z");private final String hash="a".repeat(64);
 private MediaAsset pending(){return MediaAsset.register(tenant,"doc-1","ver-1","photo.jpg","image/jpeg",MediaKind.IMAGE,100,hash,"actor-1",now);}
 @Test void mediaIsQuarantinedUntilExactVersionIsClean(){var asset=pending();assertEquals(MediaLifecycleStatus.QUARANTINED,asset.status());assertThrows(IllegalStateException.class,()->asset.archive(now));var clean=asset.recordScan(MediaScanStatus.CLEAN,now.plusSeconds(1));assertEquals(MediaLifecycleStatus.AVAILABLE,clean.status());assertEquals(MediaScanStatus.CLEAN,clean.scanStatus());}
 @Test void infectedAndFailedVerdictsNeverBecomeAvailable(){assertEquals(MediaLifecycleStatus.REJECTED,pending().recordScan(MediaScanStatus.INFECTED,now).status());assertEquals(MediaLifecycleStatus.QUARANTINED,pending().recordScan(MediaScanStatus.FAILED,now).status());}
 @Test void terminalScanVerdictCannotBeReplaced(){var clean=pending().recordScan(MediaScanStatus.CLEAN,now);assertThrows(IllegalStateException.class,()->clean.recordScan(MediaScanStatus.INFECTED,now));}
 @Test void rejectsInvalidChecksumAndNegativeSize(){assertThrows(IllegalArgumentException.class,()->MediaAsset.register(tenant,"d","v","f","x/y",MediaKind.DOCUMENT,1,"bad","a",now));assertThrows(IllegalArgumentException.class,()->MediaAsset.register(tenant,"d","v","f","x/y",MediaKind.DOCUMENT,-1,hash,"a",now));}
}
