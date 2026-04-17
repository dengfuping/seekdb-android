package com.oceanbase.seekdb.android.compat;

/**
 * SQL used by App Inspection / Database Inspector to load the schema tree. The
 * stock Inspector
 * query targets SQLite ({@code sqlite_master} / {@code pragma_table_info});
 * SeekDB exposes
 * MySQL/OceanBase catalogs, so we use {@code information_schema} with the row
 * shape expected by
 * {@code androidx.sqlite.inspection.SqliteInspector#querySchema}:
 * {@code type}, {@code tableName}, {@code columnName}, {@code columnType},
 * {@code notnull},
 * {@code pk}, {@code unique}.
 */
public final class SeekdbInspectorSchemaQuery {
        private SeekdbInspectorSchemaQuery() {
        }

        /**
         * One row per column, ordered by table then ordinal position (matches the
         * former SQLite query
         * ordering). {@code unique} is currently always 0 (single-column unique
         * detection can be added
         * via {@code information_schema.statistics} when needed).
         */
        public static final String FOR_APP_INSPECTION_GET_SCHEMA = "SELECT "
                        + "CASE WHEN t.TABLE_TYPE = 'VIEW' THEN 'view' ELSE 'table' END AS type, "
                        + "t.TABLE_NAME AS tableName, "
                        + "c.COLUMN_NAME AS columnName, "
                        + "c.COLUMN_TYPE AS columnType, "
                        + "IF(c.IS_NULLABLE = 'NO', 1, 0) AS notnull, "
                        + "IF(c.COLUMN_KEY = 'PRI', 1, 0) AS pk, "
                        + "0 AS `unique` "
                        + "FROM information_schema.tables t "
                        + "INNER JOIN information_schema.columns c "
                        + "ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME "
                        + "WHERE t.TABLE_SCHEMA = DATABASE() "
                        + "AND (t.TABLE_TYPE = 'BASE TABLE' OR t.TABLE_TYPE = 'VIEW') "
                        + "ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION";
}
