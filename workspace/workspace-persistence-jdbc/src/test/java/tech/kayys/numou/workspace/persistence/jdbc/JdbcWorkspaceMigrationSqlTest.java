package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcWorkspaceMigrationSqlTest {

    @Test
    void keepsDollarQuotedFunctionBodiesAsOneStatement() {
        String sql = """
                -- migration comment with a semicolon ;
                CREATE FUNCTION example() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM 'inside; string';
                    -- procedural comment ;
                    RETURN NEW;
                END;
                $$;
                CREATE TRIGGER example_trigger BEFORE INSERT ON example_table
                FOR EACH ROW EXECUTE FUNCTION example();
                """;

        List<String> statements = JdbcWorkspaceStorePostgresTest.splitSqlStatements(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("PERFORM 'inside; string';"));
        assertTrue(statements.get(0).contains("RETURN NEW;"));
        assertTrue(statements.get(0).endsWith("$$"));
        assertTrue(statements.get(1).startsWith("CREATE TRIGGER"));
    }

    @Test
    void keepsQuotedSemicolonsAndIgnoresSemicolonsInComments() {
        String sql = """
                INSERT INTO example_table(value) VALUES ('left;right'); -- comment ;
                /* block ; comment */ SELECT "semi;colon" FROM example_table;
                """;

        List<String> statements = JdbcWorkspaceStorePostgresTest.splitSqlStatements(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("'left;right'"));
        assertTrue(statements.get(1).contains("\"semi;colon\""));
    }
}
