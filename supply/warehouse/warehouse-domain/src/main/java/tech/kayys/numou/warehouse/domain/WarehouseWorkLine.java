package tech.kayys.numou.warehouse.domain;
import java.math.BigDecimal;
import java.util.Objects;
/** Quantity-safe line for one warehouse operation. */
public record WarehouseWorkLine(String lineId,String itemReference,String uom,BigDecimal plannedQuantity,BigDecimal executedQuantity,String sourceLocation,String destinationLocation,String packageReference) {
 public WarehouseWorkLine {
  lineId=req(lineId,"lineId");itemReference=req(itemReference,"itemReference");uom=req(uom,"uom");
  Objects.requireNonNull(plannedQuantity);Objects.requireNonNull(executedQuantity);
  if(plannedQuantity.signum()<=0||executedQuantity.signum()<0||executedQuantity.compareTo(plannedQuantity)>0)throw new IllegalArgumentException("Invalid planned/executed quantity");
 }
 public static WarehouseWorkLine planned(String id,String item,String uom,BigDecimal q){return new WarehouseWorkLine(id,item,uom,q,BigDecimal.ZERO,null,null,null);}
 public WarehouseWorkLine execute(BigDecimal q,String context,WarehouseWorkType type){
  Objects.requireNonNull(q);if(q.signum()<=0||executedQuantity.add(q).compareTo(plannedQuantity)>0)throw new IllegalArgumentException("Execution quantity must be positive and within planned quantity");
  String source=sourceLocation,dest=destinationLocation,pack=packageReference;
  if(type==WarehouseWorkType.PICKING)source=req(context,"sourceLocation");
  if(type==WarehouseWorkType.PUT_AWAY)dest=req(context,"destinationLocation");
  if(type==WarehouseWorkType.PACKING)pack=req(context,"packageReference");
  return new WarehouseWorkLine(lineId,itemReference,uom,plannedQuantity,executedQuantity.add(q),source,dest,pack);
 }
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" required");return v.trim();}
}