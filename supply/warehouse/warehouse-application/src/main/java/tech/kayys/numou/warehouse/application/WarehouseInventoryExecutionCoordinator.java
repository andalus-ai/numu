package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.warehouse.domain.*;
import java.math.BigDecimal;
import java.util.Objects;
/**
 * Coordinates physical work with the inventory ledger using retry-safe inventory commands.
 * Inventory posting happens first; if the warehouse commit fails, retrying the same command
 * replays the inventory movement and retries the warehouse execution without double-posting.
 */
public final class WarehouseInventoryExecutionCoordinator {
 private final WarehouseWorkRepository repository;
 private final WarehouseExecutionService warehouse;
 private final InventoryWarehouseGateway inventory;
 public WarehouseInventoryExecutionCoordinator(WarehouseWorkRepository repository,WarehouseExecutionService warehouse,InventoryWarehouseGateway inventory){
  this.repository=Objects.requireNonNull(repository);this.warehouse=Objects.requireNonNull(warehouse);this.inventory=Objects.requireNonNull(inventory);
 }
 public WarehouseWorkOrder receive(String tenant,String workOrderId,String lineId,String itemId,String warehouseId,
   BigDecimal quantity,BigDecimal unitCost,String currency,String commandKey){
  WarehouseWorkOrder order=load(tenant,workOrderId);
  if(order.type()!=WarehouseWorkType.RECEIVING)throw new IllegalStateException("Inventory receipt requires a RECEIVING work order");
  inventory.receive(tenant,itemId,warehouseId,quantity,unitCost,currency,order.reference()+":"+lineId,commandKey,workOrderId,lineId);
  return warehouse.execute(tenant,workOrderId,lineId,quantity,null,commandKey);
 }
 public WarehouseWorkOrder pick(String tenant,String workOrderId,String lineId,String itemId,String warehouseId,String reservationId,
   BigDecimal quantity,String currency,String commandKey){
  WarehouseWorkOrder order=load(tenant,workOrderId);
  if(order.type()!=WarehouseWorkType.PICKING)throw new IllegalStateException("Reserved issue requires a PICKING work order");
  inventory.issueReserved(tenant,reservationId,itemId,warehouseId,quantity,currency,order.reference()+":"+lineId,commandKey,workOrderId,lineId);
  return warehouse.execute(tenant,workOrderId,lineId,quantity,warehouseId,commandKey);
 }
 private WarehouseWorkOrder load(String tenant,String id){
  if(tenant==null||tenant.isBlank()||id==null||id.isBlank())throw new IllegalArgumentException("Tenant and work order id are required");
  return repository.find(tenant.trim(),id.trim()).orElseThrow(()->new IllegalArgumentException("Warehouse work order not found"));
 }
}