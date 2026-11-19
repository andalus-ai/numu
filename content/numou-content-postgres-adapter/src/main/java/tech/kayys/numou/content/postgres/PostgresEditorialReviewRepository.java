package tech.kayys.numou.content.postgres;

import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.editorial.*;
import tech.kayys.numou.content.spi.EditorialReviewRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** JDBC adapter. Writes commit review, content lifecycle, history, and dispatch intent together. */
public final class PostgresEditorialReviewRepository implements EditorialReviewRepository {
    private final DataSource dataSource;
    public PostgresEditorialReviewRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource); }

    @Override public void submit(EditorialReview review) {
        String transition="UPDATE content_entry SET status='IN_REVIEW', updated_at=? WHERE tenant_id=? AND content_id=? AND status='DRAFT' AND current_revision=?";
        String insert="INSERT INTO content_editorial_review(tenant_id,review_id,content_id,revision_number,policy_mode,required_approvals,status,aggregate_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,0,?,?)";
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement(transition)) {
                    p.setTimestamp(1,Timestamp.from(review.createdAt())); p.setObject(2,review.tenantId().value());
                    p.setObject(3,review.contentId().value()); p.setInt(4,review.revisionNumber());
                    if(p.executeUpdate()!=1) throw new IllegalStateException("Content is not the expected draft revision");
                }
                try(PreparedStatement p=c.prepareStatement(insert)) {
                    p.setObject(1,review.tenantId().value()); p.setObject(2,review.id().value());
                    p.setObject(3,review.contentId().value()); p.setInt(4,review.revisionNumber());
                    p.setString(5,review.policy().mode().name()); p.setInt(6,review.policy().requiredApprovals());
                    p.setString(7,review.status().name()); p.setTimestamp(8,Timestamp.from(review.createdAt()));
                    p.setTimestamp(9,Timestamp.from(review.updatedAt())); p.executeUpdate();
                }
                insertReviewers(c,review); appendOutbox(c,review,"REVIEW_SUBMITTED"); c.commit();
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw failure("submit editorial review",e); }
    }

    @Override public Optional<EditorialReview> find(TenantId tenantId, EditorialReviewId reviewId) {
        try(Connection c=dataSource.getConnection()) { return load(c,tenantId,reviewId); }
        catch(SQLException e) { throw failure("find editorial review",e); }
    }

    @Override public Optional<EditorialReview> findActiveForRevision(TenantId tenantId, ContentId contentId, int revisionNumber) {
        String sql="SELECT review_id FROM content_editorial_review WHERE tenant_id=? AND content_id=? AND revision_number=? AND status='IN_REVIEW'";
        try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenantId.value()); p.setObject(2,contentId.value()); p.setInt(3,revisionNumber);
            try(ResultSet r=p.executeQuery()) { return r.next()?load(c,tenantId,new EditorialReviewId(r.getObject(1,UUID.class))):Optional.empty(); }
        } catch(SQLException e) { throw failure("find active editorial review",e); }
    }

    @Override public boolean update(EditorialReview review, long expectedVersion) {
        String update="UPDATE content_editorial_review SET status=?, aggregate_version=?, updated_at=? WHERE tenant_id=? AND review_id=? AND aggregate_version=? AND status='IN_REVIEW'";
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EditorialReview previous=load(c,review.tenantId(),review.id()).orElse(null);
                if(previous==null || previous.version()!=expectedVersion) { c.rollback(); return false; }
                // Child-history insert guards require the review row to remain active until
                // the append has succeeded. The following CAS and all writes share this transaction.
                insertNewHistory(c,previous,review);
                try(PreparedStatement p=c.prepareStatement(update)) {
                    p.setString(1,review.status().name()); p.setLong(2,review.version());
                    p.setTimestamp(3,Timestamp.from(review.updatedAt())); p.setObject(4,review.tenantId().value());
                    p.setObject(5,review.id().value()); p.setLong(6,expectedVersion);
                    if(p.executeUpdate()!=1) { c.rollback(); return false; }
                }
                if(review.status()!=EditorialReview.Status.IN_REVIEW) {
                    String target=review.status()==EditorialReview.Status.APPROVED?"APPROVED":"DRAFT";
                    try(PreparedStatement p=c.prepareStatement("UPDATE content_entry SET status=?, updated_at=? WHERE tenant_id=? AND content_id=? AND status='IN_REVIEW'")) {
                        p.setString(1,target); p.setTimestamp(2,Timestamp.from(review.updatedAt()));
                        p.setObject(3,review.tenantId().value()); p.setObject(4,review.contentId().value());
                        if(p.executeUpdate()!=1) throw new IllegalStateException("Content lifecycle no longer matches active review");
                    }
                }
                appendOutbox(c,review,"REVIEW_"+(review.status()==EditorialReview.Status.IN_REVIEW?"ACTIVITY":review.status().name()));
                c.commit(); return true;
            } catch(Exception e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw failure("update editorial review",e); }
    }

    private Optional<EditorialReview> load(Connection c,TenantId tenant,EditorialReviewId id)throws SQLException {
        String sql="SELECT content_id,revision_number,policy_mode,required_approvals,status,aggregate_version,created_at,updated_at FROM content_editorial_review WHERE tenant_id=? AND review_id=?";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setObject(1,tenant.value()); p.setObject(2,id.value());
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                ContentId content=new ContentId(r.getObject(1,UUID.class)); int revision=r.getInt(2);
                EditorialReviewPolicy.Mode mode=EditorialReviewPolicy.Mode.valueOf(r.getString(3));
                int threshold=r.getInt(4); EditorialReview.Status status=EditorialReview.Status.valueOf(r.getString(5));
                long version=r.getLong(6); Instant created=r.getTimestamp(7).toInstant(),updated=r.getTimestamp(8).toInstant();
                List<UUID> reviewers=new ArrayList<>();
                try(PreparedStatement q=c.prepareStatement("SELECT reviewer_id FROM content_editorial_reviewer WHERE tenant_id=? AND review_id=? ORDER BY reviewer_order")) {
                    q.setObject(1,tenant.value()); q.setObject(2,id.value());
                    try(ResultSet x=q.executeQuery()){while(x.next())reviewers.add(x.getObject(1,UUID.class));}
                }
                EditorialReviewPolicy policy=new EditorialReviewPolicy(mode,reviewers,threshold);
                Map<UUID,EditorialReview.ReviewDecision> decisions=new LinkedHashMap<>();
                try(PreparedStatement q=c.prepareStatement("SELECT reviewer_id,decision,comment,decided_at FROM content_editorial_decision WHERE tenant_id=? AND review_id=? ORDER BY decided_at,reviewer_id")) {
                    q.setObject(1,tenant.value()); q.setObject(2,id.value());
                    try(ResultSet x=q.executeQuery()){while(x.next()){
                        UUID reviewer=x.getObject(1,UUID.class);
                        decisions.put(reviewer,new EditorialReview.ReviewDecision(reviewer,EditorialReview.Decision.valueOf(x.getString(2)),x.getString(3),x.getTimestamp(4).toInstant()));
                    }}
                }
                List<EditorialReview.ReviewComment> comments=new ArrayList<>();
                try(PreparedStatement q=c.prepareStatement("SELECT comment_id,author_id,body,created_at FROM content_editorial_comment WHERE tenant_id=? AND review_id=? ORDER BY created_at,comment_id")) {
                    q.setObject(1,tenant.value()); q.setObject(2,id.value());
                    try(ResultSet x=q.executeQuery()){while(x.next())comments.add(new EditorialReview.ReviewComment(
                        x.getObject(1,UUID.class),x.getObject(2,UUID.class),x.getString(3),x.getTimestamp(4).toInstant()));}
                }
                return Optional.of(EditorialReview.rehydrate(id,tenant,content,revision,policy,status,decisions,comments,version,created,updated));
            }
        }
    }
    private void insertReviewers(Connection c,EditorialReview review)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO content_editorial_reviewer(tenant_id,review_id,reviewer_id,reviewer_order) VALUES (?,?,?,?)")) {
            for(int i=0;i<review.policy().reviewers().size();i++){
                p.setObject(1,review.tenantId().value());p.setObject(2,review.id().value());
                p.setObject(3,review.policy().reviewers().get(i));p.setInt(4,i);p.addBatch();
            } p.executeBatch();
        }
    }
    private void insertNewHistory(Connection c,EditorialReview before,EditorialReview after)throws SQLException {
        for(var e:after.decisions().entrySet()) if(!before.decisions().containsKey(e.getKey())) {
            var d=e.getValue();
            try(PreparedStatement p=c.prepareStatement("INSERT INTO content_editorial_decision(tenant_id,review_id,reviewer_id,decision,comment,decided_at) VALUES (?,?,?,?,?,?)")) {
                p.setObject(1,after.tenantId().value());p.setObject(2,after.id().value());p.setObject(3,d.reviewerId());
                p.setString(4,d.decision().name());p.setString(5,d.comment());p.setTimestamp(6,Timestamp.from(d.decidedAt()));p.executeUpdate();
            }
        }
        for(var comment:after.comments()) if(before.comments().stream().noneMatch(x->x.id().equals(comment.id()))) {
            try(PreparedStatement p=c.prepareStatement("INSERT INTO content_editorial_comment(tenant_id,review_id,comment_id,author_id,body,created_at) VALUES (?,?,?,?,?,?)")) {
                p.setObject(1,after.tenantId().value());p.setObject(2,after.id().value());p.setObject(3,comment.id());
                p.setObject(4,comment.authorId());p.setString(5,comment.body());p.setTimestamp(6,Timestamp.from(comment.createdAt()));p.executeUpdate();
            }
        }
    }
    private void appendOutbox(Connection c,EditorialReview review,String event)throws SQLException {
        String payload="{\"reviewId\":\""+review.id().value()+"\",\"contentId\":\""+review.contentId().value()+"\",\"revision\":"+review.revisionNumber()+",\"status\":\""+review.status().name()+"\"}";
        try(PreparedStatement p=c.prepareStatement("INSERT INTO content_editorial_outbox(tenant_id,event_id,review_id,event_type,payload,created_at) VALUES (?,?,?,?,?::jsonb,?)")) {
            p.setObject(1,review.tenantId().value());p.setObject(2,UUID.randomUUID());p.setObject(3,review.id().value());
            p.setString(4,event);p.setString(5,payload);p.setTimestamp(6,Timestamp.from(review.updatedAt()));p.executeUpdate();
        }
    }
    private RuntimeException failure(String operation,SQLException e){return new IllegalStateException("Unable to "+operation+" (SQLState="+e.getSQLState()+")",e);}
}
