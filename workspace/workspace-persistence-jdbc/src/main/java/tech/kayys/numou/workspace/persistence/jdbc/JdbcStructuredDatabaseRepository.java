package tech.kayys.numou.workspace.persistence.jdbc;

import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.application.WorkspacePorts.StructuredDatabaseRepository;
import tech.kayys.numou.workspace.domain.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * PostgreSQL adapter for the structured workspace database port.
 * Each write and its audit event share one transaction. All reads are tenant scoped.
 */
public final class JdbcStructuredDatabaseRepository implements StructuredDatabaseRepository {
    private final DataSource dataSource;
    public JdbcStructuredDatabaseRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override public boolean saveDatabase(WorkspaceDatabase value, AuditEntry audit) {
        validateAudit(audit, value.tenantId(), value.id(), "DATABASE", "DATABASE_CREATED", 1);
        return atomic("save database", c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO workspace_databases(id,tenant_id,workspace_id,name,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1,value.id()); p.setObject(2,value.tenantId()); p.setObject(3,value.workspaceId());
                p.setString(4,value.name()); p.setObject(5,value.createdBy()); p.setTimestamp(6,Timestamp.from(value.createdAt())); p.setTimestamp(7,Timestamp.from(value.updatedAt())); p.executeUpdate();
            }
            insertAudit(c,audit); return true;
        });
    }

    @Override public Optional<WorkspaceDatabase> findDatabase(UUID tenantId, UUID databaseId) {
        String sql="SELECT id,tenant_id,workspace_id,name,created_by,created_at,updated_at FROM workspace_databases WHERE tenant_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenantId);p.setObject(2,databaseId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                return Optional.of(new WorkspaceDatabase(uuid(r,1),uuid(r,2),uuid(r,3),r.getString(4),uuid(r,5),instant(r,6),instant(r,7)));
            }
        } catch(SQLException e) { throw failure("find workspace database",e); }
    }

    @Override public boolean saveTable(DatabaseTable table, AuditEntry audit) {
        validateAudit(audit,table.tenantId(),table.id(),"DATABASE_TABLE","DATABASE_TABLE_CREATED",1);
        return atomic("save database table and properties",c -> {
            try(PreparedStatement p=c.prepareStatement("INSERT INTO workspace_database_tables(id,tenant_id,database_id,name,schema_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1,table.id());p.setObject(2,table.tenantId());p.setObject(3,table.databaseId());p.setString(4,table.name());
                p.setLong(5,table.schemaVersion());Timestamp now=Timestamp.from(audit.occurredAt());p.setTimestamp(6,now);p.setTimestamp(7,now);p.executeUpdate();
            }
            String sql="INSERT INTO workspace_database_properties(tenant_id,database_id,table_id,property_id,property_key,label,property_type,required,options,related_table_id) VALUES(?,?,?,?,?,?,?, ?, CAST(? AS jsonb), ?)";
            try(PreparedStatement p=c.prepareStatement(sql)) {
                for(DatabaseProperty property:table.properties()) {
                    p.setObject(1,table.tenantId());p.setObject(2,table.databaseId());p.setObject(3,table.id());p.setObject(4,property.id());
                    p.setString(5,property.key());p.setString(6,property.label());p.setString(7,property.type().name());p.setBoolean(8,property.required());
                    p.setString(9,Json.write(property.options()));p.setObject(10,property.relatedTableId());p.addBatch();
                }
                p.executeBatch();
            }
            insertAudit(c,audit);return true;
        });
    }

    @Override public Optional<DatabaseTable> findTable(UUID tenantId,UUID databaseId,UUID tableId) {
        String sql="SELECT id,tenant_id,database_id,name,schema_version FROM workspace_database_tables WHERE tenant_id=? AND database_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenantId);p.setObject(2,databaseId);p.setObject(3,tableId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                UUID id=uuid(r,1),tenant=uuid(r,2),db=uuid(r,3);String name=r.getString(4);long version=r.getLong(5);
                return Optional.of(new DatabaseTable(id,tenant,db,name,version,readProperties(c,tenant,db,id)));
            }
        } catch(SQLException e) { throw failure("find database table",e); }
    }

    @Override public Optional<DatabaseRecord> findRecord(UUID tenantId,UUID recordId) {
        String sql="SELECT id,tenant_id,database_id,table_id,revision,record_data::text,created_at,updated_at FROM workspace_database_records WHERE tenant_id=? AND id=? AND archived_at IS NULL";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenantId);p.setObject(2,recordId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                UUID id=uuid(r,1),tenant=uuid(r,2),db=uuid(r,3),tableId=uuid(r,4);
                long revision=r.getLong(5);String data=r.getString(6);Instant created=instant(r,7),updated=instant(r,8);
                DatabaseTable table=findTable(c,tenant,db,tableId).orElseThrow(()->new SQLException("record table missing"));
                return Optional.of(new DatabaseRecord(id,tenant,db,tableId,revision,decodeValues(table,data),created,updated));
            }
        } catch(SQLException e) { throw failure("find database record",e); }
    }

    @Override public boolean saveRecord(DatabaseRecord record,long expectedRevision,AuditEntry audit) {
        if(expectedRevision<0 || record.revision()!=expectedRevision+1) throw new IllegalArgumentException("record revision must advance exactly once");
        String action=expectedRevision==0?"DATABASE_RECORD_CREATED":"DATABASE_RECORD_UPDATED";
        validateAudit(audit,record.tenantId(),record.id(),"DATABASE_RECORD",action,record.revision());
        return atomic("save database record with optimistic revision and audit",c -> {
            int changed;
            if(expectedRevision==0) {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO workspace_database_records(id,tenant_id,database_id,table_id,revision,record_data,created_at,updated_at) VALUES(?,?,?,?,?,CAST(? AS jsonb),?,?)")) {
                    p.setObject(1,record.id());p.setObject(2,record.tenantId());p.setObject(3,record.databaseId());p.setObject(4,record.tableId());p.setLong(5,record.revision());
                    p.setString(6,Json.write(record.values()));p.setTimestamp(7,Timestamp.from(record.createdAt()));p.setTimestamp(8,Timestamp.from(record.updatedAt()));
                    try { changed=p.executeUpdate(); } catch(SQLException e) { if(isConstraint(e)) return false; throw e; }
                }
            } else {
                try(PreparedStatement p=c.prepareStatement("UPDATE workspace_database_records SET revision=?,record_data=CAST(? AS jsonb),updated_at=? WHERE tenant_id=? AND id=? AND revision=? AND archived_at IS NULL")) {
                    p.setLong(1,record.revision());p.setString(2,Json.write(record.values()));p.setTimestamp(3,Timestamp.from(record.updatedAt()));
                    p.setObject(4,record.tenantId());p.setObject(5,record.id());p.setLong(6,expectedRevision);
                    changed=p.executeUpdate();
                }
            }
            if(changed!=1) return false;
            insertAudit(c,audit);return true;
        });
    }

    @Override public DatabaseRecordPage queryRecords(UUID tenantId, UUID databaseId, UUID tableId,
                                                       Map<String, Object> filters, int limit, int offset) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        Map<String, Object> safeFilters = filters == null ? Map.of() : filters;
        StringBuilder filterSql = new StringBuilder();
        safeFilters.keySet().stream().sorted().forEach(key -> {
            if (key == null || !key.matches("[a-z][a-z0-9_]{0,62}")) {
                throw new IllegalArgumentException("invalid structured property key in query");
            }
            filterSql.append(" AND record_data -> ? = CAST(? AS jsonb)");
        });
        String where = "tenant_id=? AND database_id=? AND table_id=? AND archived_at IS NULL" + filterSql;
        try (Connection c = dataSource.getConnection()) {
            DatabaseTable schema = findTable(c, tenantId, databaseId, tableId)
                    .orElseThrow(() -> new SQLException("table not found in tenant database"));
            for (String key : safeFilters.keySet()) {
                if (schema.properties().stream().noneMatch(property -> property.key().equals(key))) {
                    throw new IllegalArgumentException("unknown filter property: " + key);
                }
            }
            long total;
            try (PreparedStatement count = c.prepareStatement("SELECT count(*) FROM workspace_database_records WHERE " + where)) {
                int index = bindRecordQuery(count, tenantId, databaseId, tableId, safeFilters);
                try (ResultSet r = count.executeQuery()) { r.next(); total = r.getLong(1); }
            }
            List<DatabaseRecord> records = new ArrayList<>();
            String sql = "SELECT id,tenant_id,database_id,table_id,revision,record_data::text,created_at,updated_at FROM workspace_database_records WHERE "
                    + where + " ORDER BY created_at ASC,id ASC LIMIT ? OFFSET ?";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                int index = bindRecordQuery(p, tenantId, databaseId, tableId, safeFilters);
                p.setInt(index++, limit); p.setInt(index, offset);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        UUID id=uuid(r,1), tenant=uuid(r,2), db=uuid(r,3), table=uuid(r,4);
                        long revision=r.getLong(5); String data=r.getString(6);
                        records.add(new DatabaseRecord(id,tenant,db,table,revision,decodeValues(schema,data),instant(r,7),instant(r,8)));
                    }
                }
            }
            return new DatabaseRecordPage(records, total, limit, offset);
        } catch (SQLException e) { throw failure("query structured database records", e); }
    }

    private static int bindRecordQuery(PreparedStatement statement, UUID tenantId, UUID databaseId,
                                       UUID tableId, Map<String, Object> filters) throws SQLException {
        int index = 1;
        statement.setObject(index++, tenantId);
        statement.setObject(index++, databaseId);
        statement.setObject(index++, tableId);
        for (String key : filters.keySet().stream().sorted().toList()) {
            statement.setString(index++, key);
            statement.setString(index++, Json.write(filters.get(key)));
        }
        return index;
    }

    @Override public boolean saveView(DatabaseView view, AuditEntry audit) {
        validateAudit(audit, view.tenantId(), view.id(), "DATABASE_VIEW", "DATABASE_VIEW_CREATED", 1);
        return atomic("save structured database view", c -> {
            String sql = "INSERT INTO workspace_database_views(id,tenant_id,database_id,table_id,created_by,name,view_type,filters,sorts,visible_properties,created_at,updated_at) VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?,?)";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setObject(1, view.id()); p.setObject(2, view.tenantId()); p.setObject(3, view.databaseId());
                p.setObject(4, view.tableId()); p.setObject(5, view.createdBy()); p.setString(6, view.name());
                p.setString(7, view.type().name()); p.setString(8, Json.write(view.filters()));
                List<Map<String, Object>> sorts = view.sorts().stream().map(s -> Map.<String,Object>of(
                        "propertyKey", s.propertyKey(), "direction", s.direction().name())).toList();
                p.setString(9, Json.write(sorts)); p.setString(10, Json.write(view.visibleProperties()));
                p.setTimestamp(11, Timestamp.from(view.createdAt())); p.setTimestamp(12, Timestamp.from(view.updatedAt()));
                try { p.executeUpdate(); } catch (SQLException e) { if (isConstraint(e)) return false; throw e; }
            }
            insertAudit(c, audit);
            return true;
        });
    }

    @Override public Optional<DatabaseView> findView(UUID tenantId, UUID databaseId, UUID viewId) {
        String sql = "SELECT id,tenant_id,database_id,table_id,created_by,name,view_type,filters::text,sorts::text,visible_properties::text,created_at,updated_at FROM workspace_database_views WHERE tenant_id=? AND database_id=? AND id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, databaseId); p.setObject(3, viewId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(decodeView(r));
            }
        } catch (SQLException e) { throw failure("find structured database view", e); }
    }

    @Override public List<DatabaseView> findViews(UUID tenantId, UUID databaseId, UUID tableId, UUID actorId) {
        String sql = "SELECT id,tenant_id,database_id,table_id,created_by,name,view_type,filters::text,sorts::text,visible_properties::text,created_at,updated_at FROM workspace_database_views WHERE tenant_id=? AND database_id=? AND table_id=? AND created_by=? ORDER BY name,id";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, databaseId); p.setObject(3, tableId); p.setObject(4, actorId);
            try (ResultSet r = p.executeQuery()) {
                List<DatabaseView> result = new ArrayList<>();
                while (r.next()) result.add(decodeView(r));
                return List.copyOf(result);
            }
        } catch (SQLException e) { throw failure("list structured database views", e); }
    }

    @SuppressWarnings("unchecked")
    private static DatabaseView decodeView(ResultSet r) throws SQLException {
        Map<String,Object> filters = (Map<String,Object>) Json.read(r.getString(8));
        List<?> rawSorts = (List<?>) Json.read(r.getString(9));
        List<DatabaseViewSort> sorts = rawSorts.stream().map(raw -> {
            Map<?,?> item = (Map<?,?>) raw;
            return new DatabaseViewSort((String) item.get("propertyKey"),
                    DatabaseViewSort.Direction.valueOf((String) item.get("direction")));
        }).toList();
        List<String> visible = ((List<?>) Json.read(r.getString(10))).stream().map(String.class::cast).toList();
        return new DatabaseView(uuid(r,1),uuid(r,2),uuid(r,3),uuid(r,4),uuid(r,5),r.getString(6),
                DatabaseViewType.valueOf(r.getString(7)),filters,sorts,visible,instant(r,11),instant(r,12));
    }

    private List<DatabaseProperty> readProperties(Connection c,UUID tenant,UUID db,UUID table) throws SQLException {
        List<DatabaseProperty> out=new ArrayList<>();
        String sql="SELECT property_id,property_key,label,property_type,required,options::text,related_table_id FROM workspace_database_properties WHERE tenant_id=? AND database_id=? AND table_id=? ORDER BY property_key";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenant);p.setObject(2,db);p.setObject(3,table);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    @SuppressWarnings("unchecked") List<String> options=(List<String>)Json.read(r.getString(6));
                    out.add(new DatabaseProperty(uuid(r,1),r.getString(2),r.getString(3),DatabasePropertyType.valueOf(r.getString(4)),r.getBoolean(5),options,nullableUuid(r,7)));
                }
            }
        }
        return List.copyOf(out);
    }

    private Optional<DatabaseTable> findTable(Connection c,UUID tenant,UUID db,UUID table) throws SQLException {
        String sql="SELECT id,tenant_id,database_id,name,schema_version FROM workspace_database_tables WHERE tenant_id=? AND database_id=? AND id=?";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenant);p.setObject(2,db);p.setObject(3,table);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                UUID id=uuid(r,1);return Optional.of(new DatabaseTable(id,uuid(r,2),uuid(r,3),r.getString(4),r.getLong(5),readProperties(c,tenant,db,id)));
            }
        }
    }

    private static Map<String,Object> decodeValues(DatabaseTable table,String json) {
        Object decoded=Json.read(json);
        if(!(decoded instanceof Map<?,?> raw)) throw new IllegalStateException("record_data must be a JSON object");
        Map<String,Object> values=new LinkedHashMap<>();
        for(DatabaseProperty property:table.properties()) {
            Object value=raw.get(property.key()); if(value==null) continue;
            switch(property.type()) {
                case NUMBER -> values.put(property.key(),value instanceof BigDecimal?value:new BigDecimal(value.toString()));
                case DATE -> values.put(property.key(),LocalDate.parse(value.toString()));
                case DATE_TIME -> values.put(property.key(),Instant.parse(value.toString()));
                case RELATION -> {
                    if(!(value instanceof List<?> list)) throw new IllegalStateException("relation JSON must be an array");
                    values.put(property.key(),list.stream().map(v->UUID.fromString(v.toString())).toList());
                }
                default -> values.put(property.key(),value);
            }
        }
        return Map.copyOf(values);
    }

    private interface SqlWork { boolean run(Connection connection) throws SQLException; }
    private boolean atomic(String operation,SqlWork work) {
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try { boolean result=work.run(c);c.commit();return result; }
            catch(SQLException|RuntimeException e) { try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
        } catch(SQLException e) { if(isConstraint(e)) return false; throw failure(operation,e); }
    }
    private static void insertAudit(Connection c,AuditEntry a) throws SQLException {
        String sql="INSERT INTO workspace_audit_events(tenant_id,actor_id,action,resource_type,resource_id,revision,summary,occurred_at) VALUES(?,?,?,?,?,?,?,?)";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,a.tenantId());p.setObject(2,a.actorId());p.setString(3,a.action());p.setString(4,a.resourceType());p.setObject(5,a.resourceId());
            p.setLong(6,a.revision());p.setString(7,a.summary());p.setTimestamp(8,Timestamp.from(a.occurredAt()));p.executeUpdate();
        }
    }
    private static void validateAudit(AuditEntry a,UUID tenant,UUID id,String type,String action,long revision) {
        Objects.requireNonNull(a,"audit");
        if(!tenant.equals(a.tenantId())||!id.equals(a.resourceId())||!type.equals(a.resourceType())||!action.equals(a.action())||revision!=a.revision())
            throw new IllegalArgumentException("audit entry does not describe structured database mutation");
    }
    private static boolean isConstraint(SQLException e) { return e.getSQLState()!=null && e.getSQLState().startsWith("23"); }
    private static UUID uuid(ResultSet r,int col)throws SQLException { Object v=r.getObject(col);return v instanceof UUID u?u:UUID.fromString(v.toString()); }
    private static UUID nullableUuid(ResultSet r,int col)throws SQLException { Object v=r.getObject(col);return v==null?null:(v instanceof UUID u?u:UUID.fromString(v.toString())); }
    private static Instant instant(ResultSet r,int col)throws SQLException { return r.getTimestamp(col).toInstant(); }
    private static RuntimeException failure(String operation,SQLException e) { return new IllegalStateException("Failed to "+operation+" (SQLState="+e.getSQLState()+")",e); }

    /** Minimal JSON codec for the deliberately small set of canonical workspace property values. */
    static final class Json {
        private Json() {}
        static String write(Object value) {
            if(value==null)return "null";
            if(value instanceof String s)return quote(s);
            if(value instanceof UUID || value instanceof LocalDate || value instanceof Instant)return quote(value.toString());
            if(value instanceof Boolean)return value.toString();
            if(value instanceof Number n)return new BigDecimal(n.toString()).toPlainString();
            if(value instanceof Map<?,?> map) {
                StringJoiner j=new StringJoiner(",","{","}");
                map.entrySet().stream().sorted(Comparator.comparing(e->String.valueOf(e.getKey()))).forEach(e->j.add(quote(String.valueOf(e.getKey()))+":"+write(e.getValue())));
                return j.toString();
            }
            if(value instanceof Iterable<?> values) { StringJoiner j=new StringJoiner(",","[","]");for(Object item:values)j.add(write(item));return j.toString(); }
            throw new IllegalArgumentException("unsupported JSON value "+value.getClass().getName());
        }
        private static String quote(String s) {
            StringBuilder b=new StringBuilder("\"");
            for(int i=0;i<s.length();i++){char ch=s.charAt(i);switch(ch){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(ch<0x20)b.append(String.format("\\u%04x",(int)ch));else b.append(ch);}}}
            return b.append('"').toString();
        }
        static Object read(String json) { Parser p=new Parser(json);Object value=p.value();p.space();if(p.i!=json.length())throw new IllegalArgumentException("trailing JSON content");return value; }
        private static final class Parser {
            final String s;int i;Parser(String s){this.s=Objects.requireNonNull(s);}
            void space(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
            Object value(){space();if(i>=s.length())throw bad();char c=s.charAt(i);if(c=='"')return string();if(c=='{')return object();if(c=='[')return array();if(s.startsWith("true",i)){i+=4;return true;}if(s.startsWith("false",i)){i+=5;return false;}if(s.startsWith("null",i)){i+=4;return null;}return number();}
            Map<String,Object> object(){i++;Map<String,Object> m=new LinkedHashMap<>();space();if(take('}'))return m;do{space();if(i>=s.length()||s.charAt(i)!='"')throw bad();String k=string();space();if(!take(':'))throw bad();if(m.containsKey(k))throw bad();m.put(k,value());space();if(take('}'))return m;if(!take(','))throw bad();}while(true);}
            List<Object> array(){i++;List<Object> a=new ArrayList<>();space();if(take(']'))return a;do{a.add(value());space();if(take(']'))return a;if(!take(','))throw bad();}while(true);}
            String string(){i++;StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())throw bad();char e=s.charAt(i++);switch(e){case '"','\\','/'->b.append(e);case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{if(i+4>s.length())throw bad();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->throw bad();}}else{if(c<0x20)throw bad();b.append(c);}}throw bad();}
            BigDecimal number(){int start=i;if(take('-')&&i>=s.length())throw bad();if(take('0')){if(i<s.length()&&Character.isDigit(s.charAt(i)))throw bad();}else{if(i>=s.length()||s.charAt(i)<'1'||s.charAt(i)>'9')throw bad();while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}if(take('.')){int fraction=i;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i==fraction)throw bad();}if(i<s.length()&&(s.charAt(i)=='e'||s.charAt(i)=='E')){i++;if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++;int exponent=i;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i==exponent)throw bad();}try{return new BigDecimal(s.substring(start,i));}catch(RuntimeException e){throw bad();}}
            boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
            IllegalArgumentException bad(){return new IllegalArgumentException("invalid JSON at offset "+i);}
        }
    }
}
