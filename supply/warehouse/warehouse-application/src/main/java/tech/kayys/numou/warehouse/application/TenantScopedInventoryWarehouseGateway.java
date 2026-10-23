package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.inventory.application.TenantScopedInventoryService;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.ReservationId;
import java.math.BigDecimal;
import java.util.Objects;
/** Adapter delegates every stock mutation to the tenant-scoped inventory application service. */
public final class TenantScopedInventoryWarehouseGateway implements InventoryWarehouseGateway {
 private final TenantScopedInventoryService inventory;
 public TenantScopedInventoryWarehouseGateway(TenantScopedInventoryService inventory){this.inventory=Objects.requireNonNull(inventory);}
 @Override public void receive(String tenant,String item,String warehouse,BigDecimal quantity,BigDecimal unitCost,String currency,String reference,String commandKey,String workOrder,String line){
  inventory.receive(req(tenant,"tenantId"),key(workOrder,line,"receive",commandKey),ItemId.of(req(item,"itemId")),WarehouseId.of(req(warehouse,"warehouseId")),quantity,unitCost,currency,reference);
 }
 @Override public void issueReserved(String tenant,String reservation,String item,String warehouse,BigDecimal quantity,String currency,String reference,String commandKey,String workOrder,String line){
  inventory.issueReserved(req(tenant,"tenantId"),key(workOrder,line,"issue-reserved",commandKey),ReservationId.of(req(reservation,"reservationId")),ItemId.of(req(item,"itemId")),WarehouseId.of(req(warehouse,"warehouseId")),quantity,currency,reference);
 }
 private static InventoryCommandKey key(String workOrder,String line,String operation,String commandKey){
  return new InventoryCommandKey("warehouse:"+req(workOrder,"workOrderId")+":"+req(line,"lineId")+":"+operation,req(commandKey,"commandKey"));
 }
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}