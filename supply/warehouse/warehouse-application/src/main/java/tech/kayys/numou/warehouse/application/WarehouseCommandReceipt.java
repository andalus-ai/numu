package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.warehouse.domain.WarehouseWorkOrder;
import java.time.Instant;
public record WarehouseCommandReceipt(String tenantId,String commandKey,String fingerprint,WarehouseWorkOrder result,Instant committedAt) {
 public WarehouseCommandReceipt {if(tenantId==null||tenantId.isBlank()||commandKey==null||commandKey.isBlank()||fingerprint==null||fingerprint.isBlank()||result==null||committedAt==null)throw new IllegalArgumentException("Receipt fields required");if(!tenantId.equals(result.tenantId()))throw new IllegalArgumentException("Tenant mismatch");}
}