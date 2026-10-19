package tech.kayys.numou.support.domain.knowledge;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Knowledge article aggregate root.
 * Represents a self-service knowledge base article.
 */
public final class KnowledgeArticle extends AbstractAggregateRoot<KnowledgeArticleId> {
    
    private static final long serialVersionUID = 1L;
    
    private String title;
    private String summary;
    private String content;
    private List<String> tags;
    private String category;
    private String status; // DRAFT, PUBLISHED, ARCHIVED
    private String author;
    private int viewCount;
    private int helpfulCount;
    private int notHelpfulCount;
    private List<String> relatedArticles;
    private Instant publishedAt;
    private String notes;

    private KnowledgeArticle(KnowledgeArticleId id) {
        super(id);
        this.tags = new ArrayList<>();
        this.relatedArticles = new ArrayList<>();
        this.status = "DRAFT";
        this.viewCount = 0;
        this.helpfulCount = 0;
        this.notHelpfulCount = 0;
    }

    private KnowledgeArticle() {
        super();
    }

    /**
     * Factory method to create a new knowledge article.
     */
    public static KnowledgeArticle create(
            KnowledgeArticleId id,
            String title,
            String content,
            String category,
            String author) {
        KnowledgeArticle article = new KnowledgeArticle(id);
        article.title = title;
        article.content = content;
        article.category = category;
        article.author = author;
        return article;
    }

    /**
     * Publishes the article.
     */
    public void publish() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Archives the article.
     */
    public void archive() {
        this.status = "ARCHIVED";
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a view.
     */
    public void recordView() {
        this.viewCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records as helpful.
     */
    public void recordHelpful() {
        this.helpfulCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records as not helpful.
     */
    public void recordNotHelpful() {
        this.notHelpfulCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a tag.
     */
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Adds a related article.
     */
    public void addRelatedArticle(String articleId) {
        if (!relatedArticles.contains(articleId)) {
            relatedArticles.add(articleId);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Gets the helpfulness ratio.
     */
    public double getHelpfulnessRatio() {
        int total = helpfulCount + notHelpfulCount;
        if (total == 0) {
            return 0.0;
        }
        return (double) helpfulCount / total * 100.0;
    }

    // Getters
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public String getAuthor() { return author; }
    public int getViewCount() { return viewCount; }
    public int getHelpfulCount() { return helpfulCount; }
    public int getNotHelpfulCount() { return notHelpfulCount; }
    public List<String> getRelatedArticles() { return Collections.unmodifiableList(relatedArticles); }
    public Instant getPublishedAt() { return publishedAt; }
    public String getNotes() { return notes; }

    public void setSummary(String summary) {
        this.summary = summary;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "KnowledgeArticle{" +
                "id=" + getId() +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", views=" + viewCount +
                '}';
    }
}