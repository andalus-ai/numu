package tech.kayys.numou.developer.apicatalog.domain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ApiProductTest {
 private static final Instant NOW=Instant.parse("2026-11-04T09:00:00Z");
 private static ApiSpecification spec(){return new ApiSpecification("3.1","openapi/tenant-a/orders/1.0.0.yaml","a".repeat(64));}
 private static ApiProduct product(){return ApiProduct.create(ApiProductId.newId(),UUID.randomUUID(),"Orders API","orders","Order lifecycle API",NOW);}
 private static ApiVersion version(){return ApiVersion.draft("1.0.0",spec(),Set.of(new ApiScope("orders.read","Read orders"),new ApiScope("orders.write","Create and update orders")));}
 @Test void createsTenantOwnedDraftProductAndEmitsEvent(){var p=product();assertNotNull(p.providerTenantId());assertEquals(ApiProductStatus.DRAFT,p.status());assertEquals(1,p.revision());assertEquals("developer.api-product.created",p.pullDomainEvents().getFirst().eventType());}
 @Test void publishesVersionAndMakesProductActive(){var p=product();p.addVersion(version(),NOW.plusSeconds(1));p.publishVersion("1.0.0",NOW.plusSeconds(2));assertEquals(ApiProductStatus.ACTIVE,p.status());assertEquals(ApiVersionStatus.PUBLISHED,p.versions().get("1.0.0").status());assertEquals(3,p.revision());}
 @Test void productMustBeSuspendedBeforeRetirement(){var p=product();assertThrows(IllegalStateException.class,()->p.retire(NOW.plusSeconds(1)));p.suspend(NOW.plusSeconds(2));p.retire(NOW.plusSeconds(3));assertEquals(ApiProductStatus.RETIRED,p.status());}
 @Test void versionMustDeprecateBeforeRetirement(){var v=version().publish();assertThrows(IllegalStateException.class,v::retire);assertEquals(ApiVersionStatus.DEPRECATED,v.deprecate().status());assertEquals(ApiVersionStatus.RETIRED,v.deprecate().retire().status());}
 @Test void rejectsAlreadyPublishedVersionAtInsertion(){var p=product();assertThrows(IllegalArgumentException.class,()->p.addVersion(version().publish(),NOW.plusSeconds(1)));}
 @Test void rejectsDuplicateVersionsAndInvalidDigest(){var p=product();p.addVersion(version(),NOW.plusSeconds(1));assertThrows(IllegalStateException.class,()->p.addVersion(version(),NOW.plusSeconds(2)));assertThrows(IllegalArgumentException.class,()->new ApiSpecification("3.1","spec.yaml","not-a-digest"));}
 @Test void suspensionPreventsPublishingAndReactivationPreservesCatalog(){var p=product();p.addVersion(version(),NOW.plusSeconds(1));p.suspend(NOW.plusSeconds(2));assertThrows(IllegalStateException.class,()->p.publishVersion("1.0.0",NOW.plusSeconds(3)));p.reactivate(NOW.plusSeconds(4));assertEquals(ApiProductStatus.DRAFT,p.status());assertEquals(1,p.versions().size());}
 @Test void rejectsInvalidSlugAndTenant(){assertThrows(NullPointerException.class,()->ApiProduct.create(ApiProductId.newId(),null,"Orders","orders","",NOW));assertThrows(IllegalArgumentException.class,()->ApiProduct.create(ApiProductId.newId(),UUID.randomUUID(),"Orders","../orders","",NOW));}
}