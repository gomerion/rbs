package ru.ssau.rbs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет скрипты миграций на чистой базе: состав применённых версий,
 * структуру таблицы revoked_tokens из V2 и целостность схемы V1.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static Flyway flyway;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateSchema() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clearRevokedTokens() {
        jdbc.update("delete from revoked_tokens");
    }

    @Test
    void appliesBothMigrationsInOrder() {
        MigrationInfo[] applied = flyway.info().applied();

        assertEquals(2, applied.length);
        assertEquals("1", applied[0].getVersion().getVersion());
        assertEquals("2", applied[1].getVersion().getVersion());
        assertEquals(MigrationState.SUCCESS, applied[1].getState());
    }

    @Test
    void secondMigrationIsRevokedTokens() {
        assertEquals("revoked tokens", flyway.info().applied()[1].getDescription());
    }

    @Test
    void appliedMigrationsPassValidation() {
        // ловит правку уже применённой миграции: именно из-за неё приложение не стартовало
        assertDoesNotThrow(() -> flyway.validate());
    }

    @Test
    void migrateIsIdempotent() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void revokedTokensHasExpectedColumns() {
        Map<String, String> columns = new HashMap<>();
        jdbc.query("select column_name, data_type from information_schema.columns where table_name = 'revoked_tokens'",
                resultSet -> {
                    columns.put(resultSet.getString(1), resultSet.getString(2));
                });

        assertEquals(Map.of("jti", "text", "expires_at", "timestamp with time zone"), columns);
    }

    @Test
    void revokedTokensColumnsAreNotNullable() {
        Integer nullableColumns = jdbc.queryForObject(
                "select count(*) from information_schema.columns"
                        + " where table_name = 'revoked_tokens' and is_nullable = 'YES'",
                Integer.class);

        assertEquals(0, nullableColumns);
    }

    @Test
    void jtiIsThePrimaryKey() {
        List<String> primaryKeyColumns = jdbc.queryForList("""
                select attribute.attname
                from pg_index index
                join pg_attribute attribute
                  on attribute.attrelid = index.indrelid and attribute.attnum = any (index.indkey)
                where index.indrelid = 'revoked_tokens'::regclass and index.indisprimary
                """, String.class);

        assertEquals(List.of("jti"), primaryKeyColumns);
    }

    @Test
    void expiresAtIsIndexed() {
        String definition = jdbc.queryForObject(
                "select indexdef from pg_indexes"
                        + " where tablename = 'revoked_tokens' and indexname = 'revoked_tokens_expires_idx'",
                String.class);

        assertNotNull(definition);
        assertTrue(definition.contains("(expires_at)"), definition);
    }

    @Test
    void duplicateJtiIsRejected() {
        insertToken("repeated", "now() + interval '1 hour'");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertToken("repeated", "now() + interval '2 hours'"));
    }

    @Test
    void expiresAtCannotBeNull() {
        assertThrows(DataIntegrityViolationException.class, () -> insertToken("without-expiry", "null"));
    }

    @Test
    void cleanupByRepositoryQueryRemovesOnlyExpiredTokens() {
        insertToken("expired", "now() - interval '1 minute'");
        insertToken("active", "now() + interval '1 hour'");

        // тот же запрос, что выполняет RevokedTokenRepository.deleteExpired
        int deleted = jdbc.update("delete from revoked_tokens where expires_at < now()");

        assertEquals(1, deleted);
        assertEquals(List.of("active"), jdbc.queryForList("select jti from revoked_tokens", String.class));
    }

    @Test
    void v1SchemaIsStillPresent() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables"
                        + " where table_name in ('users', 'login_attempts', 'bookings', 'audit_log')",
                Integer.class);

        assertEquals(4, tables);
    }

    private static void insertToken(String jti, String expiresAtExpression) {
        jdbc.update("insert into revoked_tokens (jti, expires_at) values (?, " + expiresAtExpression + ")", jti);
    }
}
