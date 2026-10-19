package tech.kayys.numou.analytics.domain.model;

import tech.kayys.numou.analytics.domain.identifier.DashboardId;
import tech.kayys.numou.analytics.domain.valueobject.ChartType;
import tech.kayys.numou.analytics.domain.valueobject.DashboardStatus;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregate root.
 * Represents a configurable dashboard with widgets.
 */
public final class Dashboard extends AbstractAggregateRoot<DashboardId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private DashboardStatus status;
    private List<Widget> widgets;
    private String category;
    private String owner;
    private List<String> sharedWith;
    private String layout;
    private boolean active;
    private String refreshInterval;
    private String notes;

    private Dashboard(DashboardId id) {
        super(id);
        this.widgets = new ArrayList<>();
        this.sharedWith = new ArrayList<>();
        this.status = DashboardStatus.DRAFT;
        this.active = true;
        this.layout = "GRID";
        this.refreshInterval = "5m";
    }

    private Dashboard() {
        super();
    }

    /**
     * Factory method to create a new dashboard.
     */
    public static Dashboard create(
            DashboardId id,
            String name,
            String description,
            String category,
            String owner) {
        Dashboard dashboard = new Dashboard(id);
        dashboard.name = name;
        dashboard.description = description;
        dashboard.category = category;
        dashboard.owner = owner;
        return dashboard;
    }

    /**
     * Adds a widget to the dashboard.
     */
    public void addWidget(Widget widget) {
        if (status == DashboardStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot modify published dashboard");
        }
        widgets.add(widget);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Removes a widget from the dashboard.
     */
    public void removeWidget(String widgetId) {
        if (status == DashboardStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot modify published dashboard");
        }
        widgets.removeIf(w -> w.getId().equals(widgetId));
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Publishes the dashboard.
     */
    public void publish() {
        if (widgets.isEmpty()) {
            throw new IllegalStateException("Dashboard must have at least one widget");
        }
        this.status = DashboardStatus.PUBLISHED;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Archives the dashboard.
     */
    public void archive() {
        this.status = DashboardStatus.ARCHIVED;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Shares the dashboard with users.
     */
    public void shareWith(List<String> users) {
        this.sharedWith = new ArrayList<>(users);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a user to share list.
     */
    public void addSharedUser(String userId) {
        if (!sharedWith.contains(userId)) {
            sharedWith.add(userId);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a user from share list.
     */
    public void removeSharedUser(String userId) {
        sharedWith.remove(userId);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the layout.
     */
    public void updateLayout(String layout) {
        this.layout = layout;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the refresh interval.
     */
    public void updateRefreshInterval(String refreshInterval) {
        this.refreshInterval = refreshInterval;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the number of widgets.
     */
    public int getWidgetCount() {
        return widgets.size();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public DashboardStatus getStatus() { return status; }
    public List<Widget> getWidgets() { return Collections.unmodifiableList(widgets); }
    public String getCategory() { return category; }
    public String getOwner() { return owner; }
    public List<String> getSharedWith() { return Collections.unmodifiableList(sharedWith); }
    public String getLayout() { return layout; }
    public boolean isActive() { return active; }
    public String getRefreshInterval() { return refreshInterval; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCategory(String category) {
        this.category = category;
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
        return "Dashboard{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", widgets=" + widgets.size() +
                '}';
    }

    /**
     * Widget value object.
     */
    public static final class Widget implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final String title;
        private final ChartType chartType;
        private final String dataSource;
        private final String query;
        private final String dimensions;
        private final String metrics;
        private final String filters;
        private final int width; // in grid columns
        private final int height; // in grid rows
        private final String position;
        private final Map<String, String> configuration;
        private final String notes;

        public Widget(
                String id,
                String title,
                ChartType chartType,
                String dataSource,
                String query,
                String dimensions,
                String metrics,
                String filters,
                int width,
                int height,
                String position,
                Map<String, String> configuration,
                String notes) {
            this.id = id;
            this.title = title;
            this.chartType = chartType;
            this.dataSource = dataSource;
            this.query = query;
            this.dimensions = dimensions;
            this.metrics = metrics;
            this.filters = filters;
            this.width = width;
            this.height = height;
            this.position = position;
            this.configuration = configuration != null ? new HashMap<>(configuration) : new HashMap<>();
            this.notes = notes;
            validate();
        }

        public void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Widget ID cannot be empty");
            }
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Widget title cannot be empty");
            }
            if (chartType == null) {
                throw new IllegalArgumentException("Chart type cannot be null");
            }
            if (width < 1 || width > 12) {
                throw new IllegalArgumentException("Width must be between 1 and 12");
            }
            if (height < 1 || height > 10) {
                throw new IllegalArgumentException("Height must be between 1 and 10");
            }
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public ChartType getChartType() { return chartType; }
        public String getDataSource() { return dataSource; }
        public String getQuery() { return query; }
        public String getDimensions() { return dimensions; }
        public String getMetrics() { return metrics; }
        public String getFilters() { return filters; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public String getPosition() { return position; }
        public Map<String, String> getConfiguration() { return Collections.unmodifiableMap(configuration); }
        public String getNotes() { return notes; }

        public Widget updatePosition(int width, int height, String position) {
            return new Widget(
                id, title, chartType, dataSource, query, dimensions,
                metrics, filters, width, height, position,
                configuration, notes
            );
        }

        @Override
        public String toString() {
            return "Widget{" +
                    "id='" + id + '\'' +
                    ", title='" + title + '\'' +
                    ", chartType=" + chartType +
                    ", dimensions=" + dimensions +
                    ", metrics=" + metrics +
                    '}';
        }
    }
}