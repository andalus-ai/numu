package tech.kayys.numou.workspace.persistence.jdbc;

import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * PostgreSQL/JDBC adapter for workspace, space, page, revision and audit ports.
 * Configure a pooled DataSource in the host application. All resource reads are tenant-scoped.
 */
public final class JdbcWorkspaceStore implements WorkspaceRepository, SpaceRepository, PageRepository, PageLinkRepository, PageTemplateRepository, PageSearchRepository, AuditSink, AuditedMutationRepository, AuditedPageCommentRepository {
    private final DataSource dataSource;

    public JdbcWorkspaceStore(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); }

    @Override public boolean save(Workspace value) {
        String sql = "INSERT INTO workspace_workspaces(id,tenant_id,name,description,created_at) VALUES(?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1,value.id()); p.setObject(2,value.tenantId()); p.setString(3,value.name());
            p.setString(4,value.description()); p.setTimestamp(5,Timestamp.from(value.createdAt())); p.executeUpdate(); return true;
        } catch (SQLException e) { if (isConstraintViolation(e)) return false; throw failure("save workspace",e); }
    }

    @Override public Optional<Workspace> findWorkspace(UUID tenantId, UUID workspaceId) {
        String sql = "SELECT id,tenant_id,name,description,created_at FROM workspace_workspaces WHERE tenant_id=? AND id=?";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenantId);p.setObject(2,workspaceId);
            try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(new Workspace(uuid(r,1),uuid(r,2),r.getString(3),r.getString(4),instant(r,5))):Optional.empty();}
        } catch(SQLException e){throw failure("find workspace",e);}
    }

    @Override public List<Workspace> findByTenant(UUID tenantId) {
        String sql = "SELECT id,tenant_id,name,description,created_at FROM workspace_workspaces WHERE tenant_id=? ORDER BY created_at,id";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1, tenantId);
            List<Workspace> result = new ArrayList<>();
            try (ResultSet r=p.executeQuery()) {
                while (r.next()) result.add(new Workspace(uuid(r,1),uuid(r,2),r.getString(3),r.getString(4),instant(r,5)));
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("list tenant workspaces", e); }
    }

    @Override public boolean save(Space value) {
        String sql="INSERT INTO workspace_spaces(id,tenant_id,workspace_id,name,slug,created_at) VALUES(?,?,?,?,?,?)";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,value.id());p.setObject(2,value.tenantId());p.setObject(3,value.workspaceId());p.setString(4,value.name());p.setString(5,value.slug());p.setTimestamp(6,Timestamp.from(value.createdAt()));p.executeUpdate();return true;
        }catch(SQLException e){if(isConstraintViolation(e))return false;throw failure("save space",e);}
    }

    @Override public Optional<Space> find(UUID tenantId,UUID workspaceId,UUID spaceId) {
        String sql="SELECT id,tenant_id,workspace_id,name,slug,created_at FROM workspace_spaces WHERE tenant_id=? AND workspace_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,workspaceId);p.setObject(3,spaceId);
            try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(new Space(uuid(r,1),uuid(r,2),uuid(r,3),r.getString(4),r.getString(5),instant(r,6))):Optional.empty();}
        }catch(SQLException e){throw failure("find space",e);}
    }

    @Override public List<Space> findByWorkspace(UUID tenantId, UUID workspaceId) {
        String sql = "SELECT id,tenant_id,workspace_id,name,slug,created_at FROM workspace_spaces WHERE tenant_id=? AND workspace_id=? ORDER BY created_at,id";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, workspaceId);
            List<Space> result = new ArrayList<>();
            try (ResultSet r=p.executeQuery()) {
                while (r.next()) result.add(new Space(uuid(r,1),uuid(r,2),uuid(r,3),r.getString(4),r.getString(5),instant(r,6)));
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("list workspace spaces", e); }
    }

    @Override public boolean slugExists(UUID tenantId,UUID workspaceId,String slug) {
        String sql="SELECT 1 FROM workspace_spaces WHERE tenant_id=? AND workspace_id=? AND lower(slug)=lower(?)";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenantId);p.setObject(2,workspaceId);p.setString(3,slug);try(ResultSet r=p.executeQuery()){return r.next();}}
        catch(SQLException e){throw failure("check space slug",e);}
    }

    @Override public Optional<Page> findPage(UUID tenantId,UUID pageId) {
        String sql="SELECT id,tenant_id,workspace_id,space_id,parent_page_id,title,revision,created_at,updated_at FROM workspace_pages WHERE tenant_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,pageId);
            try(ResultSet r=p.executeQuery()){if(!r.next())return Optional.empty();return Optional.of(readPageRow(c,r));}
        }catch(SQLException e){throw failure("find page",e);}
    }

    @Override public List<Page> findChildren(UUID tenantId,UUID spaceId,UUID parentPageId) {
        String sql=parentPageId==null
                ?"SELECT id,tenant_id,workspace_id,space_id,parent_page_id,title,revision,created_at,updated_at FROM workspace_pages WHERE tenant_id=? AND space_id=? AND parent_page_id IS NULL ORDER BY updated_at DESC"
                :"SELECT id,tenant_id,workspace_id,space_id,parent_page_id,title,revision,created_at,updated_at FROM workspace_pages WHERE tenant_id=? AND space_id=? AND parent_page_id=? ORDER BY updated_at DESC";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,spaceId);if(parentPageId!=null)p.setObject(3,parentPageId);
            List<Page> pages=new ArrayList<>();try(ResultSet r=p.executeQuery()){while(r.next())pages.add(readPageRow(c,r));}return List.copyOf(pages);
        }catch(SQLException e){throw failure("list child pages",e);}
    }

    @Override public boolean saveWithRevision(Page page,PageRevision revision,long expectedRevision) {
        validateRevisionSnapshot(page, revision);
        if(expectedRevision<0||page.revision()!=expectedRevision+1)throw new IllegalArgumentException("revision must advance exactly once");
        try(Connection c=dataSource.getConnection()){
            c.setAutoCommit(false);
            try {
                if(expectedRevision==0) insertPage(c,page);
                else if(updatePageCompareAndSet(c,page,expectedRevision)!=1){c.rollback();return false;}
                replaceBlocks(c,"workspace_page_blocks",page.tenantId(),page.id(),page.blocks(),null);
                insertRevision(c,revision);
                replaceBlocks(c,"workspace_page_revision_blocks",page.tenantId(),page.id(),revision.blocks(),revision.revision());
                c.commit();return true;
            }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}
        }catch(SQLException e){if(isConstraintViolation(e))return false;throw failure("save page revision atomically",e);}
    }

    @Override public List<PageRevision> history(UUID tenantId,UUID pageId) {
        String sql="SELECT revision,title,actor_id,recorded_at,change_summary FROM workspace_page_revisions WHERE tenant_id=? AND page_id=? ORDER BY revision";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,pageId);List<PageRevision> out=new ArrayList<>();
            try(ResultSet r=p.executeQuery()){while(r.next()){long rev=r.getLong(1);out.add(new PageRevision(tenantId,pageId,rev,r.getString(2),readBlocks(c,"workspace_page_revision_blocks",tenantId,pageId,rev),uuid(r,3),instant(r,4),r.getString(5)));}}
            return List.copyOf(out);
        }catch(SQLException e){throw failure("read page history",e);}
    }

    @Override public void record(AuditEntry entry) {
        String sql="INSERT INTO workspace_audit_events(tenant_id,actor_id,action,resource_type,resource_id,revision,summary,occurred_at) VALUES(?,?,?,?,?,?,?,?)";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,entry.tenantId());p.setObject(2,entry.actorId());p.setString(3,entry.action());p.setString(4,entry.resourceType());
            p.setObject(5,entry.resourceId());p.setLong(6,entry.revision());p.setString(7,entry.summary());p.setTimestamp(8,Timestamp.from(entry.occurredAt()));p.executeUpdate();
        }catch(SQLException e){throw failure("record workspace audit event",e);}
    }

    private static void validateRevisionSnapshot(Page page, PageRevision revision) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(revision, "revision");
        if (!page.tenantId().equals(revision.tenantId())
                || !page.id().equals(revision.pageId())
                || page.revision() != revision.revision()
                || !page.title().equals(revision.title())
                || !page.blocks().equals(revision.blocks())) {
            throw new IllegalArgumentException("page and revision snapshot do not match");
        }
    }

    private void insertPage(Connection c,Page p)throws SQLException{
        String sql="INSERT INTO workspace_pages(id,tenant_id,workspace_id,space_id,parent_page_id,title,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setObject(1,p.id());s.setObject(2,p.tenantId());s.setObject(3,p.workspaceId());s.setObject(4,p.spaceId());s.setObject(5,p.parentPageId());s.setString(6,p.title());s.setLong(7,p.revision());s.setTimestamp(8,Timestamp.from(p.createdAt()));s.setTimestamp(9,Timestamp.from(p.updatedAt()));s.executeUpdate();}
    }
    private int updatePageCompareAndSet(Connection c,Page p,long expected)throws SQLException{
        String sql="UPDATE workspace_pages SET title=?,revision=?,updated_at=? WHERE tenant_id=? AND id=? AND revision=? AND workspace_id=? AND space_id=? AND parent_page_id IS NOT DISTINCT FROM ?";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setString(1,p.title());s.setLong(2,p.revision());s.setTimestamp(3,Timestamp.from(p.updatedAt()));s.setObject(4,p.tenantId());s.setObject(5,p.id());s.setLong(6,expected);s.setObject(7,p.workspaceId());s.setObject(8,p.spaceId());s.setObject(9,p.parentPageId());return s.executeUpdate();}
    }
    private void insertRevision(Connection c,PageRevision r)throws SQLException{
        String sql="INSERT INTO workspace_page_revisions(tenant_id,page_id,revision,title,actor_id,recorded_at,change_summary) VALUES(?,?,?,?,?,?,?)";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setObject(1,r.tenantId());s.setObject(2,r.pageId());s.setLong(3,r.revision());s.setString(4,r.title());s.setObject(5,r.actorId());s.setTimestamp(6,Timestamp.from(r.recordedAt()));s.setString(7,r.changeSummary());s.executeUpdate();}
    }
    private void replaceBlocks(Connection c,String table,UUID tenant,UUID page,List<PageBlock> blocks,Long revision)throws SQLException{
        String delete=revision==null?"DELETE FROM "+table+" WHERE tenant_id=? AND page_id=?":"DELETE FROM "+table+" WHERE tenant_id=? AND page_id=? AND revision=?";
        try(PreparedStatement d=c.prepareStatement(delete)){d.setObject(1,tenant);d.setObject(2,page);if(revision!=null)d.setLong(3,revision);d.executeUpdate();}
        String sql=revision==null?"INSERT INTO "+table+"(tenant_id,page_id,block_id,block_type,text_content,position,attributes_encoded) VALUES(?,?,?,?,?,?,?)"
                :"INSERT INTO "+table+"(tenant_id,page_id,revision,block_id,block_type,text_content,position,attributes_encoded) VALUES(?,?,?,?,?,?,?,?)";
        try(PreparedStatement s=c.prepareStatement(sql)){for(PageBlock b:blocks){int i=1;s.setObject(i++,tenant);s.setObject(i++,page);if(revision!=null)s.setLong(i++,revision);s.setObject(i++,b.id());s.setString(i++,b.type().name());s.setString(i++,b.text());s.setInt(i++,b.position());s.setString(i,BlockAttributesCodec.encode(b.attributes()));s.addBatch();}s.executeBatch();}
    }
    private Page readPageRow(Connection c,ResultSet r)throws SQLException{
        UUID id=uuid(r,1),tenant=uuid(r,2),workspace=uuid(r,3),space=uuid(r,4);Object parentValue=r.getObject(5);
        return new Page(id,tenant,workspace,space,parentValue==null?null:(UUID)parentValue,r.getString(6),readBlocks(c,"workspace_page_blocks",tenant,id,null),r.getLong(7),instant(r,8),instant(r,9));
    }
    private List<PageBlock> readBlocks(Connection c,String table,UUID tenant,UUID page,Long revision)throws SQLException{
        String sql=revision==null?"SELECT block_id,block_type,text_content,position,attributes_encoded FROM "+table+" WHERE tenant_id=? AND page_id=? ORDER BY position"
                :"SELECT block_id,block_type,text_content,position,attributes_encoded FROM "+table+" WHERE tenant_id=? AND page_id=? AND revision=? ORDER BY position";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant);p.setObject(2,page);if(revision!=null)p.setLong(3,revision);List<PageBlock> out=new ArrayList<>();try(ResultSet r=p.executeQuery()){while(r.next())out.add(new PageBlock(uuid(r,1),BlockType.valueOf(r.getString(2)),r.getString(3),r.getInt(4),BlockAttributesCodec.decode(r.getString(5))));}return List.copyOf(out);}
    }
    private static UUID uuid(ResultSet r,int i)throws SQLException{Object value=r.getObject(i);return value instanceof UUID u?u:UUID.fromString(value.toString());}
    private static Instant instant(ResultSet r,int i)throws SQLException{return r.getTimestamp(i).toInstant();}
    private static boolean isConstraintViolation(SQLException e){for(SQLException x=e;x!=null;x=x.getNextException())if(x.getSQLState()!=null&&x.getSQLState().startsWith("23"))return true;return false;}
    private static IllegalStateException failure(String operation,SQLException e){return new IllegalStateException("Unable to "+operation+": "+e.getMessage(),e);}
    private static void rollback(Connection c,Throwable original){try{c.rollback();}catch(SQLException rollback){original.addSuppressed(rollback);}}

    @Override public boolean save(PageLink link) {
        String sql="INSERT INTO workspace_page_links(id,tenant_id,source_page_id,target_page_id,created_by,label,created_at) VALUES(?,?,?,?,?,?,?)";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,link.id());p.setObject(2,link.tenantId());p.setObject(3,link.sourcePageId());p.setObject(4,link.targetPageId());
            p.setObject(5,link.createdBy());p.setString(6,link.label());p.setTimestamp(7,Timestamp.from(link.createdAt()));p.executeUpdate();return true;
        }catch(SQLException e){if(isConstraintViolation(e))return false;throw failure("save page link",e);}
    }

    @Override public Optional<PageLink> findLink(UUID tenantId,UUID linkId) {
        String sql="SELECT id,tenant_id,source_page_id,target_page_id,created_by,label,created_at FROM workspace_page_links WHERE tenant_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,linkId);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(readPageLink(r)):Optional.empty();}
        }catch(SQLException e){throw failure("find page link",e);}
    }

    @Override public boolean delete(UUID tenantId,UUID linkId) {
        String sql="DELETE FROM workspace_page_links WHERE tenant_id=? AND id=?";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenantId);p.setObject(2,linkId);return p.executeUpdate()==1;}
        catch(SQLException e){throw failure("delete page link",e);}
    }

    @Override public List<PageLink> outgoing(UUID tenantId,UUID sourcePageId) {
        return findPageLinks("SELECT id,tenant_id,source_page_id,target_page_id,created_by,label,created_at FROM workspace_page_links WHERE tenant_id=? AND source_page_id=? ORDER BY created_at,id",tenantId,sourcePageId);
    }

    @Override public List<PageLink> backlinks(UUID tenantId,UUID targetPageId) {
        return findPageLinks("SELECT id,tenant_id,source_page_id,target_page_id,created_by,label,created_at FROM workspace_page_links WHERE tenant_id=? AND target_page_id=? ORDER BY created_at DESC,id",tenantId,targetPageId);
    }

    private List<PageLink> findPageLinks(String sql,UUID tenantId,UUID pageId) {
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,pageId);List<PageLink> result=new ArrayList<>();
            try(ResultSet r=p.executeQuery()){while(r.next())result.add(readPageLink(r));}return List.copyOf(result);
        }catch(SQLException e){throw failure("list page links",e);}
    }


    @Override public List<PageSearchHit> searchPages(UUID tenantId, UUID workspaceId, UUID spaceId, String query, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        boolean scopedToSpace = spaceId != null;
        String spaceClause = scopedToSpace ? " AND p.space_id=?" : "";
        String sql = "SELECT p.id,p.workspace_id,p.space_id,p.parent_page_id,p.title,p.updated_at," +
                "ts_rank(to_tsvector('simple',p.title),plainto_tsquery('simple',?)) + " +
                "CASE WHEN EXISTS (SELECT 1 FROM workspace_page_blocks sb WHERE sb.tenant_id=p.tenant_id AND sb.page_id=p.id " +
                "AND to_tsvector('simple',sb.text_content) @@ plainto_tsquery('simple',?)) THEN 0.25 ELSE 0 END AS search_rank " +
                "FROM workspace_pages p WHERE p.tenant_id=? AND p.workspace_id=?" + spaceClause +
                " AND (to_tsvector('simple',p.title) @@ plainto_tsquery('simple',?) OR EXISTS " +
                "(SELECT 1 FROM workspace_page_blocks b WHERE b.tenant_id=p.tenant_id AND b.page_id=p.id " +
                "AND to_tsvector('simple',b.text_content) @@ plainto_tsquery('simple',?))) " +
                "ORDER BY search_rank DESC,p.updated_at DESC,p.id LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            p.setString(i++, query);
            p.setString(i++, query);
            p.setObject(i++, tenantId);
            p.setObject(i++, workspaceId);
            if (scopedToSpace) p.setObject(i++, spaceId);
            p.setString(i++, query);
            p.setString(i++, query);
            p.setInt(i, limit);
            List<PageSearchHit> result = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Object parent = r.getObject(4);
                    result.add(new PageSearchHit(uuid(r,1), uuid(r,2), uuid(r,3),
                            parent == null ? null : uuid(r,4), r.getString(5), instant(r,6)));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("search workspace pages", e); }
    }

    @Override public boolean save(PageTemplate template) {
        String insert = "INSERT INTO workspace_page_templates(id,tenant_id,workspace_id,created_by,name,name_normalized,title,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement(insert)) {
                    p.setObject(1, template.id()); p.setObject(2, template.tenantId()); p.setObject(3, template.workspaceId());
                    p.setObject(4, template.createdBy()); p.setString(5, template.name());
                    p.setString(6, template.name().trim().toLowerCase(Locale.ROOT)); p.setString(7, template.title());
                    p.setTimestamp(8, Timestamp.from(template.createdAt())); p.setTimestamp(9, Timestamp.from(template.updatedAt()));
                    p.executeUpdate();
                }
                insertTemplateBlocks(c, template);
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                if (e instanceof SQLException sql && isConstraintViolation(sql)) return false;
                throw e;
            }
        } catch (SQLException e) { throw failure("save page template atomically", e); }
    }

    @Override public Optional<PageTemplate> findTemplate(UUID tenantId, UUID templateId) {
        String sql = "SELECT id,tenant_id,workspace_id,created_by,name,title,created_at,updated_at FROM workspace_page_templates WHERE tenant_id=? AND id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, templateId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(readTemplate(c, r));
            }
        } catch (SQLException e) { throw failure("find page template", e); }
    }

    @Override public List<PageTemplate> findByWorkspace(UUID tenantId, UUID workspaceId) {
        String sql = "SELECT id,tenant_id,workspace_id,created_by,name,title,created_at,updated_at FROM workspace_page_templates WHERE tenant_id=? AND workspace_id=? ORDER BY name_normalized,id";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, workspaceId);
            List<PageTemplate> result = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(readTemplate(c, r)); }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("list page templates", e); }
    }

    private void insertTemplateBlocks(Connection c, PageTemplate template) throws SQLException {
        String sql = "INSERT INTO workspace_page_template_blocks(tenant_id,template_id,block_id,block_type,text_content,position,attributes_encoded) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (PageBlock block : template.blocks()) {
                p.setObject(1, template.tenantId()); p.setObject(2, template.id()); p.setObject(3, block.id());
                p.setString(4, block.type().name()); p.setString(5, block.text()); p.setInt(6, block.position());
                p.setString(7, BlockAttributesCodec.encode(block.attributes())); p.addBatch();
            }
            p.executeBatch();
        }
    }

    private PageTemplate readTemplate(Connection c, ResultSet r) throws SQLException {
        UUID id = uuid(r, 1), tenant = uuid(r, 2), workspace = uuid(r, 3), actor = uuid(r, 4);
        String name = r.getString(5), title = r.getString(6);
        Instant created = instant(r, 7), updated = instant(r, 8);
        List<PageBlock> blocks = new ArrayList<>();
        String sql = "SELECT block_id,block_type,text_content,position,attributes_encoded FROM workspace_page_template_blocks WHERE tenant_id=? AND template_id=? ORDER BY position";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenant); p.setObject(2, id);
            try (ResultSet rows = p.executeQuery()) {
                while (rows.next()) blocks.add(new PageBlock(uuid(rows, 1), BlockType.valueOf(rows.getString(2)),
                        rows.getString(3), rows.getInt(4), BlockAttributesCodec.decode(rows.getString(5))));
            }
        }
        return new PageTemplate(id, tenant, workspace, actor, name, title, blocks, created, updated);
    }

    private static PageLink readPageLink(ResultSet r)throws SQLException {
        return new PageLink(uuid(r,1),uuid(r,2),uuid(r,3),uuid(r,4),uuid(r,5),r.getString(6),instant(r,7));
    }

    /*
     * Audited mutations share one JDBC transaction. Do not replace these with a resource
     * save followed by AuditSink.record(): that creates a partial-success failure mode.
     */
    @FunctionalInterface private interface SqlMutation { boolean run(Connection connection) throws SQLException; }

    private boolean atomicMutation(String operation, SqlMutation mutation) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!mutation.run(c)) { c.rollback(); return false; }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                if (e instanceof SQLException sql && isConstraintViolation(sql)) return false;
                if (e instanceof SQLException sql) throw failure(operation, sql);
                throw e;
            }
        } catch (SQLException e) {
            if (isConstraintViolation(e)) return false;
            throw failure(operation, e);
        }
    }

    private static void validateAudit(AuditEntry audit, UUID tenantId, UUID resourceId,
                                      String expectedResourceType, String expectedAction,
                                      long expectedRevision, UUID actorId) {
        Objects.requireNonNull(audit, "audit");
        if (audit.tenantId() == null || !tenantId.equals(audit.tenantId()))
            throw new IllegalArgumentException("audit tenant does not match mutation tenant");
        if (audit.resourceId() == null || !resourceId.equals(audit.resourceId()))
            throw new IllegalArgumentException("audit resource does not match mutation resource");
        if (audit.actorId() == null)
            throw new IllegalArgumentException("audit actor is required");
        if (!expectedAction.equals(audit.action()))
            throw new IllegalArgumentException("audit action does not match mutation action");
        if (!expectedResourceType.equals(audit.resourceType()))
            throw new IllegalArgumentException("audit resource type does not match mutation resource type");
        if (audit.occurredAt() == null)
            throw new IllegalArgumentException("audit timestamp is required");
        if (audit.revision() != expectedRevision)
            throw new IllegalArgumentException("audit revision does not match mutation revision");
        if (actorId != null && !actorId.equals(audit.actorId()))
            throw new IllegalArgumentException("audit actor does not match mutation actor");
    }

    private void insertAudit(Connection c, AuditEntry entry) throws SQLException {
        String sql = "INSERT INTO workspace_audit_events(tenant_id,actor_id,action,resource_type,resource_id,revision,summary,occurred_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, entry.tenantId()); p.setObject(2, entry.actorId()); p.setString(3, entry.action());
            p.setString(4, entry.resourceType()); p.setObject(5, entry.resourceId()); p.setLong(6, entry.revision());
            p.setString(7, entry.summary() == null ? "" : entry.summary());
            p.setTimestamp(8, Timestamp.from(entry.occurredAt())); p.executeUpdate();
        }
    }

    @Override public boolean saveWorkspace(Workspace value, AuditEntry audit) {
        validateAudit(audit, value.tenantId(), value.id(), "WORKSPACE", "WORKSPACE_CREATED", 0, null);
        return atomicMutation("save workspace with audit", c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO workspace_workspaces(id,tenant_id,name,description,created_at) VALUES(?,?,?,?,?)")) {
                p.setObject(1,value.id()); p.setObject(2,value.tenantId()); p.setString(3,value.name());
                p.setString(4,value.description()); p.setTimestamp(5,Timestamp.from(value.createdAt())); p.executeUpdate();
            }
            insertAudit(c, audit); return true;
        });
    }

    @Override public boolean saveSpace(Space value, AuditEntry audit) {
        validateAudit(audit, value.tenantId(), value.id(), "SPACE", "SPACE_CREATED", 0, null);
        return atomicMutation("save space with audit", c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO workspace_spaces(id,tenant_id,workspace_id,name,slug,created_at) VALUES(?,?,?,?,?,?)")) {
                p.setObject(1,value.id()); p.setObject(2,value.tenantId()); p.setObject(3,value.workspaceId());
                p.setString(4,value.name()); p.setString(5,value.slug()); p.setTimestamp(6,Timestamp.from(value.createdAt())); p.executeUpdate();
            }
            insertAudit(c, audit); return true;
        });
    }

    @Override public boolean savePageWithRevision(Page page, PageRevision revision, long expectedRevision, AuditEntry audit) {
        validateAudit(audit, page.tenantId(), page.id(), "PAGE", expectedRevision == 0 ? "PAGE_CREATED" : "PAGE_UPDATED", page.revision(), revision.actorId());
        validateRevisionSnapshot(page, revision);
        if (expectedRevision < 0 || page.revision() != expectedRevision + 1)
            throw new IllegalArgumentException("revision must advance exactly once");
        return atomicMutation("save page, revision and audit atomically", c -> {
            if (expectedRevision == 0) insertPage(c, page);
            else if (updatePageCompareAndSet(c, page, expectedRevision) != 1) return false;
            replaceBlocks(c, "workspace_page_blocks", page.tenantId(), page.id(), page.blocks(), null);
            insertRevision(c, revision);
            replaceBlocks(c, "workspace_page_revision_blocks", page.tenantId(), page.id(), revision.blocks(), revision.revision());
            insertAudit(c, audit); return true;
        });
    }

    @Override public boolean saveTemplate(PageTemplate template, AuditEntry audit) {
        validateAudit(audit, template.tenantId(), template.id(), "PAGE_TEMPLATE", "PAGE_TEMPLATE_CREATED", 1, template.createdBy());
        return atomicMutation("save page template with audit", c -> {
            String insert = "INSERT INTO workspace_page_templates(id,tenant_id,workspace_id,created_by,name,name_normalized,title,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(insert)) {
                p.setObject(1, template.id()); p.setObject(2, template.tenantId()); p.setObject(3, template.workspaceId());
                p.setObject(4, template.createdBy()); p.setString(5, template.name());
                p.setString(6, template.name().trim().toLowerCase(Locale.ROOT)); p.setString(7, template.title());
                p.setTimestamp(8, Timestamp.from(template.createdAt())); p.setTimestamp(9, Timestamp.from(template.updatedAt()));
                p.executeUpdate();
            }
            insertTemplateBlocks(c, template); insertAudit(c, audit); return true;
        });
    }

    @Override public boolean saveLink(PageLink link, AuditEntry audit) {
        validateAudit(audit, link.tenantId(), link.id(), "PAGE_LINK", "PAGE_LINK_CREATED", 0, link.createdBy());
        return atomicMutation("save page link with audit", c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO workspace_page_links(id,tenant_id,source_page_id,target_page_id,created_by,label,created_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1,link.id()); p.setObject(2,link.tenantId()); p.setObject(3,link.sourcePageId());
                p.setObject(4,link.targetPageId()); p.setObject(5,link.createdBy()); p.setString(6,link.label());
                p.setTimestamp(7,Timestamp.from(link.createdAt())); p.executeUpdate();
            }
            insertAudit(c, audit); return true;
        });
    }

    @Override public boolean deleteLink(UUID tenantId, UUID linkId, AuditEntry audit) {
        validateAudit(audit, tenantId, linkId, "PAGE_LINK", "PAGE_LINK_REMOVED", 0, null);
        return atomicMutation("delete page link with audit", c -> {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM workspace_page_links WHERE tenant_id=? AND id=?")) {
                p.setObject(1,tenantId); p.setObject(2,linkId);
                if (p.executeUpdate() != 1) return false;
            }
            insertAudit(c, audit); return true;
        });
    }


    @Override public Optional<PageCommentThread> findThread(UUID tenantId, UUID pageId, UUID threadId) {
        String sql = "SELECT id,tenant_id,page_id,resolved,revision,resolved_by,resolved_at,created_at,updated_at " +
                "FROM workspace_page_comment_threads WHERE tenant_id=? AND page_id=? AND id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, pageId); p.setObject(3, threadId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(readCommentThread(c, r)) : Optional.empty();
            }
        } catch (SQLException e) { throw failure("find page comment thread", e); }
    }

    @Override public List<PageCommentThread> findThreads(UUID tenantId, UUID pageId) {
        String sql = "SELECT id,tenant_id,page_id,resolved,revision,resolved_by,resolved_at,created_at,updated_at " +
                "FROM workspace_page_comment_threads WHERE tenant_id=? AND page_id=? ORDER BY created_at DESC,id";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setObject(2, pageId);
            List<PageCommentThread> result = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(readCommentThread(c, r));
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure("list page comment threads", e); }
    }

    private PageCommentThread readCommentThread(Connection c, ResultSet r) throws SQLException {
        UUID id = uuid(r, 1), tenant = uuid(r, 2), page = uuid(r, 3);
        boolean resolved = r.getBoolean(4);
        long revision = r.getLong(5);
        Object resolverValue = r.getObject(6);
        UUID resolver = resolverValue == null ? null : uuid(r, 6);
        Instant resolvedAt = r.getTimestamp(7) == null ? null : instant(r, 7);
        Instant created = instant(r, 8), updated = instant(r, 9);
        List<PageComment> entries = new ArrayList<>();
        String commentsSql = "SELECT id,tenant_id,page_id,thread_id,parent_comment_id,author_id,body,created_at " +
                "FROM workspace_page_comments WHERE tenant_id=? AND page_id=? AND thread_id=? ORDER BY created_at,id";
        try (PreparedStatement p = c.prepareStatement(commentsSql)) {
            p.setObject(1, tenant); p.setObject(2, page); p.setObject(3, id);
            try (ResultSet rows = p.executeQuery()) {
                while (rows.next()) {
                    Object parentValue = rows.getObject(5);
                    entries.add(new PageComment(uuid(rows, 1), uuid(rows, 2), uuid(rows, 3), uuid(rows, 4),
                            parentValue == null ? null : uuid(rows, 5), uuid(rows, 6),
                            rows.getString(7), instant(rows, 8)));
                }
            }
        }
        return new PageCommentThread(id, tenant, page, resolved, revision, resolver,
                resolvedAt, created, updated, entries);
    }

    @Override public boolean createThread(PageCommentThread thread, AuditEntry audit) {
        PageComment root = thread.comments().stream().filter(comment -> comment.parentCommentId() == null)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("thread root comment is required"));
        validateAudit(audit, thread.tenantId(), thread.id(), "PAGE_COMMENT_THREAD",
                "PAGE_COMMENT_THREAD_CREATED", thread.revision(), root.authorId());
        if (thread.resolved() || thread.revision() != 1 || !root.threadId().equals(thread.id())
                || !root.pageId().equals(thread.pageId()) || !root.tenantId().equals(thread.tenantId())) {
            throw new IllegalArgumentException("invalid initial comment thread");
        }
        return atomicMutation("create page comment thread with audit", c -> {
            String insertThread = "INSERT INTO workspace_page_comment_threads(id,tenant_id,page_id,resolved,revision,resolved_by,resolved_at,created_at,updated_at) VALUES(?,?,?,false,1,NULL,NULL,?,?)";
            try (PreparedStatement p = c.prepareStatement(insertThread)) {
                p.setObject(1, thread.id()); p.setObject(2, thread.tenantId()); p.setObject(3, thread.pageId());
                p.setTimestamp(4, Timestamp.from(thread.createdAt())); p.setTimestamp(5, Timestamp.from(thread.updatedAt()));
                p.executeUpdate();
            }
            insertPageComment(c, root);
            insertAudit(c, audit);
            return true;
        });
    }

    @Override public boolean appendComment(UUID tenantId, UUID pageId, UUID threadId,
                                           PageComment comment, AuditEntry audit) {
        validateAudit(audit, tenantId, comment.id(), "PAGE_COMMENT", "PAGE_COMMENT_REPLIED", 0, comment.authorId());
        if (!tenantId.equals(comment.tenantId()) || !pageId.equals(comment.pageId())
                || !threadId.equals(comment.threadId()) || comment.parentCommentId() == null) {
            throw new IllegalArgumentException("reply scope or parent is invalid");
        }
        return atomicMutation("append page comment with audit", c -> {
            String lock = "SELECT resolved FROM workspace_page_comment_threads WHERE tenant_id=? AND page_id=? AND id=? FOR UPDATE";
            try (PreparedStatement p = c.prepareStatement(lock)) {
                p.setObject(1, tenantId); p.setObject(2, pageId); p.setObject(3, threadId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || r.getBoolean(1)) return false;
                }
            }
            String parent = "SELECT 1 FROM workspace_page_comments WHERE tenant_id=? AND page_id=? AND thread_id=? AND id=?";
            try (PreparedStatement p = c.prepareStatement(parent)) {
                p.setObject(1, tenantId); p.setObject(2, pageId); p.setObject(3, threadId);
                p.setObject(4, comment.parentCommentId());
                try (ResultSet r = p.executeQuery()) { if (!r.next()) return false; }
            }
            insertPageComment(c, comment);
            insertAudit(c, audit);
            return true;
        });
    }

    private void insertPageComment(Connection c, PageComment comment) throws SQLException {
        String sql = "INSERT INTO workspace_page_comments(id,tenant_id,page_id,thread_id,parent_comment_id,author_id,body,created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, comment.id()); p.setObject(2, comment.tenantId()); p.setObject(3, comment.pageId());
            p.setObject(4, comment.threadId()); p.setObject(5, comment.parentCommentId());
            p.setObject(6, comment.authorId()); p.setString(7, comment.body());
            p.setTimestamp(8, Timestamp.from(comment.createdAt())); p.executeUpdate();
        }
    }

    @Override public boolean updateThreadResolution(PageCommentThread thread, long expectedRevision, AuditEntry audit) {
        String action = thread.resolved() ? "PAGE_COMMENT_THREAD_RESOLVED" : "PAGE_COMMENT_THREAD_REOPENED";
        validateAudit(audit, thread.tenantId(), thread.id(), "PAGE_COMMENT_THREAD",
                action, thread.revision(), audit.actorId());
        if (expectedRevision < 1 || thread.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("comment thread revision must advance exactly once");
        }
        return atomicMutation("update page comment thread resolution with audit", c -> {
            String sql = "UPDATE workspace_page_comment_threads SET resolved=?,revision=?,resolved_by=?,resolved_at=?,updated_at=? " +
                    "WHERE tenant_id=? AND page_id=? AND id=? AND revision=?";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setBoolean(1, thread.resolved()); p.setLong(2, thread.revision());
                p.setObject(3, thread.resolvedBy());
                if (thread.resolvedAt() == null) p.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
                else p.setTimestamp(4, Timestamp.from(thread.resolvedAt()));
                p.setTimestamp(5, Timestamp.from(thread.updatedAt())); p.setObject(6, thread.tenantId());
                p.setObject(7, thread.pageId()); p.setObject(8, thread.id()); p.setLong(9, expectedRevision);
                if (p.executeUpdate() != 1) return false;
            }
            insertAudit(c, audit);
            return true;
        });
    }

}
