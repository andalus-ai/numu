package tech.kayys.syirkah.analytics.domain.valueobject;

/**
 * Types of charts for visualization.
 */
public enum ChartType {
    BAR("Bar Chart"),
    LINE("Line Chart"),
    PIE("Pie Chart"),
    DONUT("Donut Chart"),
    AREA("Area Chart"),
    SCATTER("Scatter Plot"),
    BUBBLE("Bubble Chart"),
    HEATMAP("Heatmap"),
    TREEMAP("Treemap"),
    GAUGE("Gauge Chart"),
    FUNNEL("Funnel Chart"),
    TABLE("Table"),
    PIVOT("Pivot Table"),
    METRIC("Metric Card"),
    GEO("Geographic Map"),
    SANKEY("Sankey Diagram"),
    WATERFALL("Waterfall Chart"),
    BOXPLOT("Box Plot"),
    HISTOGRAM("Histogram"),
    RADAR("Radar Chart");

    private final String displayName;

    ChartType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}