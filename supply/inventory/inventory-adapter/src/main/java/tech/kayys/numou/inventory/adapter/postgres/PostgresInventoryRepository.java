package tech.kayys.numou.inventory.adapter.postgres;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.location.FacilityId;
import tech.kayys.numou.inventory.domain.location.SiteId;
import tech.kayys.numou.inventory.domain.location.StorageLocationId;
import tech.kayys.numou.inventory.domain.reservation.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PostgreSQL inventory adapter. Construct one tenant-bound instance per tenant repository scope;
 * every query still includes tenant_id as defense in depth.
 */
public final class PostgresInventoryRepository implements InventoryRepository {
    private final DataSource dataSource;
    private final String tenantId;

    public PostgresInventoryRepository(DataSource dataSource, String tenantId) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource is required");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant is required").trim();
        if (this.tenantId.isBlank() || this.tenantId.length() > 80) {
            throw new IllegalArgumentException("Tenant must contain 1..80 characters");
        }
    }

    @Override
    public void registerItem(InventoryItem item) {
        Objects.requireNonNull(item);
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory_items(tenant_id,item_id,sku,name,valuation_method,tracking_method,uom)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (tenant_id,item_id) DO UPDATE SET sku=EXCLUDED.sku,name=EXCLUDED.name,
                  valuation_method=EXCLUDED.valuation_method,tracking_method=EXCLUDED.tracking_method,uom=EXCLUDED.uom
                """)) {
                ps.setString(1, tenantId); ps.setString(2, item.id().value()); ps.setString(3, item.sku());
                ps.setString(4, item.name()); ps.setString(5, item.valuationMethod().name());
                ps.setString(6, item.trackingMethod().name()); ps.setString(7, item.uom()); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void registerWarehouse(Warehouse w) {
        Objects.requireNonNull(w);
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory_warehouses(tenant_id,warehouse_id,code,name,location,site_id,facility_id)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (tenant_id,warehouse_id) DO UPDATE SET code=EXCLUDED.code,name=EXCLUDED.name,
                  location=EXCLUDED.location,site_id=EXCLUDED.site_id,facility_id=EXCLUDED.facility_id
                """)) {
                ps.setString(1, tenantId); ps.setString(2, w.id().value()); ps.setString(3, w.code());
                ps.setString(4, w.name()); ps.setString(5, w.location());
                ps.setString(6, w.siteId() == null ? null : w.siteId().value());
                ps.setString(7, w.facilityId() == null ? null : w.facilityId().value()); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override public void saveMovement(InventoryMovement m) {
        tx(c -> { lockStock(c, m.itemId(), m.warehouseId()); insertMovement(c, m); return null; });
    }

    @Override public InventoryMovement saveMovementIdempotently(InventoryCommandKey key, InventoryMovement m) {
        return tx(c -> {
            lockStock(c, m.itemId(), m.warehouseId());
            String fp = fingerprint(movementPayload(m));
            List<InventoryMovement> replay = readReceipt(c, key, "MOVEMENT", fp);
            if (replay != null) return replay.get(0);
            insertMovement(c, m); writeReceipt(c, key, "MOVEMENT", fp, List.of(m.id().value()));
            return m;
        });
    }

    @Override public void postIssueIfAvailable(InventoryMovement m, BigDecimal quantity) {
        tx(c -> { lockStock(c, m.itemId(), m.warehouseId()); requireAvailable(c,m.itemId(),m.warehouseId(),quantity);
            validateIssue(m, quantity); insertMovement(c,m); return null; });
    }

    @Override public InventoryMovement postIssueIfAvailable(InventoryCommandKey key, InventoryMovement m, BigDecimal quantity) {
        return tx(c -> {
            lockStock(c,m.itemId(),m.warehouseId()); validateIssue(m,quantity);
            String fp=fingerprint(movementPayload(m)+"|required="+quantity.stripTrailingZeros().toPlainString());
            List<InventoryMovement> replay=readReceipt(c,key,"ISSUE",fp); if(replay!=null)return replay.get(0);
            requireAvailable(c,m.itemId(),m.warehouseId(),quantity); insertMovement(c,m);
            writeReceipt(c,key,"ISSUE",fp,List.of(m.id().value())); return m;
        });
    }

    @Override public void postTransferIfAvailable(InventoryMovement out, InventoryMovement in, BigDecimal quantity) {
        tx(c -> { lockStockPair(c,out.itemId(),out.warehouseId(),in.warehouseId()); validateTransfer(out,in,quantity);
            requireAvailable(c,out.itemId(),out.warehouseId(),quantity); insertMovement(c,out); insertMovement(c,in); return null; });
    }

    @Override public List<InventoryMovement> postTransferIfAvailable(InventoryCommandKey key, InventoryMovement out,
                                                                      InventoryMovement in, BigDecimal quantity) {
        return tx(c -> {
            lockStockPair(c,out.itemId(),out.warehouseId(),in.warehouseId()); validateTransfer(out,in,quantity);
            String fp=fingerprint(movementPayload(out)+"|"+movementPayload(in)+"|required="+quantity.stripTrailingZeros().toPlainString());
            List<InventoryMovement> replay=readReceipt(c,key,"TRANSFER",fp); if(replay!=null)return replay;
            requireAvailable(c,out.itemId(),out.warehouseId(),quantity); insertMovement(c,out); insertMovement(c,in);
            writeReceipt(c,key,"TRANSFER",fp,List.of(out.id().value(),in.id().value())); return List.of(out,in);
        });
    }

    @Override public InventoryReservation reserveIfAvailable(InventoryReservation r) {
        return tx(c -> { requireTenant(r); lockStock(c,r.itemId(),r.warehouseId()); requireAvailable(c,r.itemId(),r.warehouseId(),r.quantity());
            insertReservation(c,r); return r; });
    }

    @Override public InventoryReservation reserveIfAvailable(InventoryCommandKey key, InventoryReservation r) {
        return tx(c -> {
            requireTenant(r); lockStock(c,r.itemId(),r.warehouseId());
            String fp=fingerprint(reservationPayload(r));
            InventoryReservation prior=findReservationCommand(c,key,fp);
            if(prior!=null)return prior;
            requireAvailable(c,r.itemId(),r.warehouseId(),r.quantity());
            insertReservation(c,r);
            try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO inventory_reservation_commands(tenant_id,command_scope,command_key,payload_fingerprint,reservation_id)
                VALUES (?,?,?,?,?)
                """)) {
                ps.setString(1,tenantId); ps.setString(2,key.scope()); ps.setString(3,key.key()); ps.setString(4,fp);
                ps.setString(5,r.id().value()); ps.executeUpdate();
            }
            return r;
        });
    }

    @Override public Optional<InventoryReservation> findReservationByCommandKey(InventoryCommandKey key) {
        return tx(c -> {
            try(PreparedStatement ps=c.prepareStatement("""
                SELECT r.* FROM inventory_reservation_commands cmd JOIN inventory_reservations r
                  ON r.tenant_id=cmd.tenant_id AND r.reservation_id=cmd.reservation_id
                WHERE cmd.tenant_id=? AND cmd.command_scope=? AND cmd.command_key=?
                """)) {
                ps.setString(1,tenantId);ps.setString(2,key.scope());ps.setString(3,key.key());
                try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(readReservation(rs)):Optional.empty();}
            }
        });
    }

    @Override public InventoryReservation findReservation(ReservationId id) {
        return tx(c -> selectReservation(c,id,true));
    }

    @Override public InventoryReservation updateReservation(InventoryReservation updated) {
        return tx(c -> {
            requireTenant(updated); lockStock(c,updated.itemId(),updated.warehouseId());
            InventoryReservation current=selectReservation(c,updated.id(),true);
            if(current.version()!=updated.version()-1)throw new IllegalStateException("Reservation version conflict");
            if(!sameReservationIdentity(current,updated))throw new IllegalArgumentException("Reservation identity and quantity are immutable");
            boolean valid=current.status()==ReservationStatus.ACTIVE
                && (updated.status()==ReservationStatus.RELEASED||updated.status()==ReservationStatus.EXPIRED
                    );
            if(!valid)throw new IllegalStateException("Invalid reservation transition "+current.status()+" -> "+updated.status());
            try(PreparedStatement ps=c.prepareStatement("""
                UPDATE inventory_reservations SET status=?,version=?
                WHERE tenant_id=? AND reservation_id=? AND version=? AND status='ACTIVE'
                """)) {
                ps.setString(1,updated.status().name());ps.setInt(2,updated.version());ps.setString(3,tenantId);
                ps.setString(4,updated.id().value());ps.setInt(5,current.version());
                if(ps.executeUpdate()!=1)throw new IllegalStateException("Reservation version conflict");
            }
            return updated;
        });
    }

    @Override public List<InventoryReservation> findReservations(ItemId item, WarehouseId warehouse) {
        return tx(c -> {
            List<InventoryReservation> result=new ArrayList<>();
            try(PreparedStatement ps=c.prepareStatement("""
                SELECT * FROM inventory_reservations WHERE tenant_id=? AND item_id=? AND warehouse_id=? ORDER BY created_at,reservation_id
                """)) {
                ps.setString(1,tenantId);ps.setString(2,item.value());ps.setString(3,warehouse.value());
                try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(readReservation(rs));}
            }
            return List.copyOf(result);
        });
    }

    @Override public InventoryMovement postReservedIssue(InventoryCommandKey key, ReservationId id,
                                                           InventoryMovement movement, BigDecimal required) {
        return tx(c -> {
            lockStock(c,movement.itemId(),movement.warehouseId()); validateIssue(movement,required);
            String fp=fingerprint("reservation="+id.value()+"|"+movementPayload(movement)+"|required="+required.stripTrailingZeros().toPlainString());
            List<InventoryMovement> replay=readReceipt(c,key,"RESERVED_ISSUE",fp); if(replay!=null)return replay.get(0);
            InventoryReservation reservation=selectReservation(c,id,true);
            if(reservation.status()!=ReservationStatus.ACTIVE || (reservation.expiresAt()!=null&&!reservation.expiresAt().isAfter(Instant.now())))
                throw new IllegalStateException("Reservation is not active");
            if(!reservation.itemId().equals(movement.itemId())||!reservation.warehouseId().equals(movement.warehouseId())
                ||reservation.quantity().compareTo(required)!=0)throw new IllegalArgumentException("Issue does not match reservation");
            if(onHand(c,movement.itemId(),movement.warehouseId()).compareTo(required)<0)
                throw new IllegalStateException("Insufficient on-hand stock for reserved issue");
            insertMovement(c,movement);
            InventoryReservation committed=reservation.commit();
            try(PreparedStatement ps=c.prepareStatement("UPDATE inventory_reservations SET status='COMMITTED',version=? WHERE tenant_id=? AND reservation_id=? AND version=? AND status='ACTIVE'")) {
                ps.setInt(1,committed.version());ps.setString(2,tenantId);ps.setString(3,id.value());ps.setInt(4,reservation.version());
                if(ps.executeUpdate()!=1)throw new IllegalStateException("Reservation version conflict");
            }
            writeReceipt(c,key,"RESERVED_ISSUE",fp,List.of(movement.id().value())); return movement;
        });
    }

    @Override public InventoryMovement postReservedIssue(ReservationId id, InventoryMovement movement, BigDecimal required) {
        // Compatibility path intentionally remains atomic but does not create an idempotency receipt.
        return tx(c -> {
            lockStock(c,movement.itemId(),movement.warehouseId()); validateIssue(movement,required);
            InventoryReservation r=selectReservation(c,id,true);
            if(r.status()!=ReservationStatus.ACTIVE || (r.expiresAt()!=null&&!r.expiresAt().isAfter(Instant.now())))
                throw new IllegalStateException("Reservation is not active");
            if(!r.itemId().equals(movement.itemId())||!r.warehouseId().equals(movement.warehouseId())||r.quantity().compareTo(required)!=0)
                throw new IllegalArgumentException("Issue does not match reservation");
            if(onHand(c,movement.itemId(),movement.warehouseId()).compareTo(required)<0)throw new IllegalStateException("Insufficient on-hand stock");
            insertMovement(c,movement);
            try(PreparedStatement ps=c.prepareStatement("UPDATE inventory_reservations SET status='COMMITTED',version=? WHERE tenant_id=? AND reservation_id=? AND version=? AND status='ACTIVE'")) {
                ps.setInt(1,r.version()+1);ps.setString(2,tenantId);ps.setString(3,id.value());ps.setInt(4,r.version());
                if(ps.executeUpdate()!=1)throw new IllegalStateException("Reservation version conflict");
            }
            return movement;
        });
    }

    @Override public List<InventoryMovement> findByItemAndWarehouse(ItemId item, WarehouseId warehouse) {
        return queryMovements("SELECT * FROM inventory_stock_movements WHERE tenant_id=? AND item_id=? AND warehouse_id=? ORDER BY occurred_at,movement_id",
                item.value(),warehouse.value());
    }
    @Override public List<InventoryMovement> findByItem(ItemId item) {
        return queryMovements("SELECT * FROM inventory_stock_movements WHERE tenant_id=? AND item_id=? ORDER BY occurred_at,movement_id",item.value());
    }

    private List<InventoryMovement> queryMovements(String sql,String... values) {
        return tx(c -> { List<InventoryMovement> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,tenantId);for(int i=0;i<values.length;i++)ps.setString(i+2,values[i]);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(readMovement(rs));}}return List.copyOf(out);});
    }

    private void insertMovement(Connection c,InventoryMovement m)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("""
            INSERT INTO inventory_stock_movements(tenant_id,movement_id,item_id,warehouse_id,movement_type,quantity,unit_cost,currency,reference_number,occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """)) {
            ps.setString(1,tenantId);ps.setString(2,m.id().value());ps.setString(3,m.itemId().value());ps.setString(4,m.warehouseId().value());
            ps.setString(5,m.type().name());ps.setBigDecimal(6,m.quantity());ps.setBigDecimal(7,m.unitCost());ps.setString(8,m.currency());
            ps.setString(9,m.referenceNumber());ps.setTimestamp(10,Timestamp.from(m.occurredAt()));ps.executeUpdate();
        }
    }

    private void insertReservation(Connection c,InventoryReservation r)throws SQLException {
        if(r.status()!=ReservationStatus.ACTIVE)throw new IllegalArgumentException("Only ACTIVE reservations may be created");
        try(PreparedStatement ps=c.prepareStatement("""
            INSERT INTO inventory_reservations(tenant_id,reservation_id,item_id,warehouse_id,storage_location_id,quantity,uom,status,source_reference,created_at,expires_at,version)
            VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?,?,?)
            """)) {
            ps.setString(1,tenantId);ps.setString(2,r.id().value());ps.setString(3,r.itemId().value());ps.setString(4,r.warehouseId().value());
            ps.setString(5,r.storageLocationId()==null?null:r.storageLocationId().value());ps.setBigDecimal(6,r.quantity());ps.setString(7,r.uom());
            ps.setString(8,r.sourceReference());ps.setTimestamp(9,Timestamp.from(r.createdAt()));
            if(r.expiresAt()==null)ps.setNull(10,Types.TIMESTAMP_WITH_TIMEZONE);else ps.setTimestamp(10,Timestamp.from(r.expiresAt()));
            ps.setInt(11,r.version());ps.executeUpdate();
        }
    }

    private InventoryReservation findReservationCommand(Connection c,InventoryCommandKey key,String fp)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT payload_fingerprint,reservation_id FROM inventory_reservation_commands WHERE tenant_id=? AND command_scope=? AND command_key=? FOR UPDATE")) {
            ps.setString(1,tenantId);ps.setString(2,key.scope());ps.setString(3,key.key());
            try(ResultSet rs=ps.executeQuery()){if(!rs.next())return null;if(!fp.equals(rs.getString(1)))throw new IllegalArgumentException("Idempotency key payload mismatch");return selectReservation(c,ReservationId.of(rs.getString(2)),false);}
        }
    }

    private InventoryReservation selectReservation(Connection c,ReservationId id,boolean lock)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT * FROM inventory_reservations WHERE tenant_id=? AND reservation_id=?"+(lock?" FOR UPDATE":""))) {
            ps.setString(1,tenantId);ps.setString(2,id.value());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Unknown reservation: "+id.value());return readReservation(rs);}
        }
    }

    private InventoryReservation readReservation(ResultSet rs)throws SQLException {
        Timestamp expires=rs.getTimestamp("expires_at");
        String storage=rs.getString("storage_location_id");
        return new InventoryReservation(ReservationId.of(rs.getString("reservation_id")),rs.getString("tenant_id"),
            ItemId.of(rs.getString("item_id")),WarehouseId.of(rs.getString("warehouse_id")),
            storage==null?null:StorageLocationId.of(storage),rs.getBigDecimal("quantity"),rs.getString("uom"),
            ReservationStatus.valueOf(rs.getString("status")),rs.getString("source_reference"),
            rs.getTimestamp("created_at").toInstant(),expires==null?null:expires.toInstant(),rs.getInt("version"));
    }

    private InventoryMovement readMovement(ResultSet rs)throws SQLException {
        return new InventoryMovement(MovementId.of(rs.getString("movement_id")),ItemId.of(rs.getString("item_id")),
            WarehouseId.of(rs.getString("warehouse_id")),MovementType.valueOf(rs.getString("movement_type")),
            rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_cost"),rs.getString("currency"),
            rs.getString("reference_number"),rs.getTimestamp("occurred_at").toInstant());
    }

    private BigDecimal onHand(Connection c,ItemId item,WarehouseId warehouse)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(quantity),0) FROM inventory_stock_movements WHERE tenant_id=? AND item_id=? AND warehouse_id=?")) {
            ps.setString(1,tenantId);ps.setString(2,item.value());ps.setString(3,warehouse.value());
            try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}
        }
    }
    private BigDecimal activeReserved(Connection c,ItemId item,WarehouseId warehouse)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("""
            SELECT COALESCE(SUM(quantity),0) FROM inventory_reservations WHERE tenant_id=? AND item_id=? AND warehouse_id=?
              AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>?)
            """)) {
            ps.setString(1,tenantId);ps.setString(2,item.value());ps.setString(3,warehouse.value());ps.setTimestamp(4,Timestamp.from(Instant.now()));
            try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}
        }
    }
    private void requireAvailable(Connection c,ItemId item,WarehouseId warehouse,BigDecimal quantity)throws SQLException {
        if(quantity==null||quantity.signum()<=0)throw new IllegalArgumentException("Positive quantity is required");
        BigDecimal available=onHand(c,item,warehouse).subtract(activeReserved(c,item,warehouse));
        if(available.compareTo(quantity)<0)throw new IllegalStateException("Insufficient available stock: available="+available+", requested="+quantity);
    }
    private void lockCommand(Connection c,InventoryCommandKey key)throws SQLException {\n        Objects.requireNonNull(key,"Command key is required");\n        try(PreparedStatement ps=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {\n            ps.setString(1,tenantId+"|"+key.scope()+"|"+key.key());ps.execute();\n        }\n    }\n    private void lockStock(Connection c,ItemId item,WarehouseId warehouse)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO inventory_stock_locks(tenant_id,item_id,warehouse_id) VALUES (?,?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1,tenantId);ps.setString(2,item.value());ps.setString(3,warehouse.value());ps.executeUpdate();
        }
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM inventory_stock_locks WHERE tenant_id=? AND item_id=? AND warehouse_id=? FOR UPDATE")) {
            ps.setString(1,tenantId);ps.setString(2,item.value());ps.setString(3,warehouse.value());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Unable to acquire inventory stock lock");}
        }
    }
    private void lockStockPair(Connection c,ItemId item,WarehouseId a,WarehouseId b)throws SQLException {
        List<WarehouseId> ids=new ArrayList<>(List.of(a,b));ids.sort(Comparator.comparing(WarehouseId::value));
        for(WarehouseId id:ids)lockStock(c,item,id);
    }
    private List<InventoryMovement> readReceipt(Connection c,InventoryCommandKey key,String type,String fp)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT command_type,payload_fingerprint,result_ids FROM inventory_command_receipts WHERE tenant_id=? AND command_scope=? AND command_key=? FOR UPDATE")) {
            ps.setString(1,tenantId);ps.setString(2,key.scope());ps.setString(3,key.key());
            try(ResultSet rs=ps.executeQuery()){if(!rs.next())return null;
                if(!type.equals(rs.getString(1))||!fp.equals(rs.getString(2)))throw new IllegalArgumentException("Idempotency key payload mismatch");
                List<InventoryMovement> result=new ArrayList<>();
                Array idsArray=rs.getArray(3);\n                String[] ids=(String[])idsArray.getArray();\n                for(String id:ids){try(PreparedStatement find=c.prepareStatement("SELECT * FROM inventory_stock_movements WHERE tenant_id=? AND movement_id=?")){
                    find.setString(1,tenantId);find.setString(2,id);try(ResultSet row=find.executeQuery()){if(!row.next())throw new IllegalStateException("Command receipt points to missing movement");result.add(readMovement(row));}}}
                return List.copyOf(result);
            }
        }
    }
    private void writeReceipt(Connection c,InventoryCommandKey key,String type,String fp,List<String> ids)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO inventory_command_receipts(tenant_id,command_scope,command_key,command_type,payload_fingerprint,result_ids) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1,tenantId);ps.setString(2,key.scope());ps.setString(3,key.key());ps.setString(4,type);ps.setString(5,fp);\n            ps.setArray(6,c.createArrayOf("text",ids.toArray(String[]::new)));ps.executeUpdate();
        }
    }

    private void requireTenant(InventoryReservation r) {
        if(!tenantId.equals(r.tenantId()))throw new SecurityException("Reservation tenant does not match repository tenant");
    }
    private static boolean sameReservationIdentity(InventoryReservation a,InventoryReservation b) {
        return a.tenantId().equals(b.tenantId())&&a.itemId().equals(b.itemId())&&a.warehouseId().equals(b.warehouseId())
            &&a.quantity().compareTo(b.quantity())==0&&a.uom().equals(b.uom())&&a.sourceReference().equals(b.sourceReference())
            &&Objects.equals(a.expiresAt(),b.expiresAt());
    }
    private static void validateIssue(InventoryMovement m,BigDecimal q) {
        if(m==null||m.type()!=MovementType.ISSUE||q==null||q.signum()<=0||m.quantity().compareTo(q.negate())!=0)
            throw new IllegalArgumentException("Issue movement and matching positive required quantity are required");
    }
    private static void validateTransfer(InventoryMovement out,InventoryMovement in,BigDecimal q) {
        validateIssue(out,q);
        if(in==null||in.type()!=MovementType.RECEIPT||!out.itemId().equals(in.itemId())||out.warehouseId().equals(in.warehouseId())
            ||in.quantity().compareTo(q)!=0||!out.currency().equals(in.currency())||out.unitCost().compareTo(in.unitCost())!=0)
            throw new IllegalArgumentException("Transfer movements are inconsistent");
    }
    private static String movementPayload(InventoryMovement m) {
        return m.itemId().value()+"|"+m.warehouseId().value()+"|"+m.type()+"|"+m.quantity().stripTrailingZeros().toPlainString()
            +"|"+m.unitCost().stripTrailingZeros().toPlainString()+"|"+m.currency()+"|"+Objects.toString(m.referenceNumber(),"")+"|"+m.occurredAt();
    }
    private static String reservationPayload(InventoryReservation r) {
        return r.tenantId()+"|"+r.itemId().value()+"|"+r.warehouseId().value()+"|"+r.quantity().stripTrailingZeros().toPlainString()
            +"|"+r.uom()+"|"+r.sourceReference()+"|"+r.expiresAt();
    }
    private static String fingerprint(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    private <T>T tx(SqlWork<T> work) {
        try(Connection c=dataSource.getConnection()) {
            boolean auto=c.getAutoCommit();c.setAutoCommit(false);
            try {T result=work.apply(c);c.commit();c.setAutoCommit(auto);return result;}
            catch(Exception e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
            catch(Error e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
        } catch(RuntimeException e){throw e;} catch(Exception e){throw new IllegalStateException("Inventory PostgreSQL operation failed",e);}
    }
    @FunctionalInterface private interface SqlWork<T>{T apply(Connection c)throws Exception;}
}
