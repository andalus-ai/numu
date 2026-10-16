package tech.kayys.syirkah.construction.adapter.postgres.boq;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Explicit mapping from the starter's BOQ persistence contract to the project's
 * physical PostgreSQL tables. Identifiers are validated and quoted; values remain
 * prepared-statement parameters. This is configuration, not a claim about the
 * source dump's actual schema.
 */
public record PgLiveBoqSqlMapping(
        String boqTable, String boqId, String projectId, String tenantId, String boqName,
        String currentRevision, String status, String changedAt,
        String itemTable, String itemId, String itemBoqId, String wbsNodeId,
        String itemCode, String description, String itemType, String quantity,
        String quantityUnit, String rateAmount, String rateUnit, String currencyCode) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public PgLiveBoqSqlMapping {
        table(boqTable, "boqTable");
        table(itemTable, "itemTable");
        identifier(boqId, "boqId"); identifier(projectId, "projectId");
        identifier(tenantId, "tenantId"); identifier(boqName, "boqName");
        identifier(currentRevision, "currentRevision"); identifier(status, "status");
        identifier(changedAt, "changedAt"); identifier(itemId, "itemId");
        identifier(itemBoqId, "itemBoqId"); identifier(wbsNodeId, "wbsNodeId");
        identifier(itemCode, "itemCode"); identifier(description, "description");
        identifier(itemType, "itemType"); identifier(quantity, "quantity");
        identifier(quantityUnit, "quantityUnit"); identifier(rateAmount, "rateAmount");
        identifier(rateUnit, "rateUnit"); identifier(currencyCode, "currencyCode");
    }

    String boqTableSql() { return table(boqTable, "boqTable"); }
    String itemTableSql() { return table(itemTable, "itemTable"); }
    String col(String identifier) { return quote(identifier); }

    private static String table(String value, String name) {
        Objects.requireNonNull(value, name);
        String[] parts = value.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException(name + " must be table or schema.table");
        for (String part : parts) identifier(part, name);
        return java.util.Arrays.stream(parts).map(PgLiveBoqSqlMapping::quote)
                .collect(java.util.stream.Collectors.joining("."));
    }

    private static void identifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException(name + " is not a safe SQL identifier");
    }

    private static String quote(String value) { return "\"" + value + "\""; }
}
