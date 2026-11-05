package tech.kayys.numou.advertising.application;
import tech.kayys.numou.advertising.domain.TenantId;
import java.util.concurrent.CompletionStage;
/** Adapter to the host identity/authorization capability; tenant and actor are resolved server-side. */
public interface AdvertisingAuthorizationPort {
  CompletionStage<Boolean> mayManageAdvertising(TenantId tenantId,String action);
}
