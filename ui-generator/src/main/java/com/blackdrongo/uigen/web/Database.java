package com.blackdrongo.uigen.web;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final Path DATA_DIR = AppPaths.dataDir().toAbsolutePath();
    private static final String DB_PATH = DATA_DIR.resolve("ui-generator.db").toString();
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    static {
        init();
    }

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    private static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found.", e);
        }
        try {
            java.nio.file.Files.createDirectories(DATA_DIR);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to create data directory for SQLite.", e);
        }
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS projects (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        engine TEXT,
                        browser TEXT,
                        headless INTEGER,
                        base_url TEXT,
                        created_at TEXT
                    )
                    """);
            ensureProjectColumns(statement);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS features (
                        id TEXT PRIMARY KEY,
                        project_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        base_url TEXT,
                        jira_stories TEXT,
                        created_at TEXT,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS scenarios (
                        id TEXT PRIMARY KEY,
                        project_id TEXT NOT NULL,
                        feature_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        steps TEXT,
                        tags TEXT,
                        mvn_args TEXT,
                        base_url TEXT,
                        created_at TEXT,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE,
                        FOREIGN KEY(feature_id) REFERENCES features(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS scenario_runs (
                        scenario_id TEXT PRIMARY KEY,
                        state TEXT,
                        message TEXT,
                        updated_at TEXT,
                        FOREIGN KEY(scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite database at " + DB_PATH, e);
        }
    }

    private static void ensureProjectColumns(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "projects", "chrome_start_maximized", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "chrome_incognito", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "chrome_disable_notifications", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "chrome_disable_popup_blocking", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "chrome_accept_insecure_certs", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "chrome_custom_args", "TEXT DEFAULT ''");
        addColumnIfMissing(statement, "projects", "firefox_private_mode", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "firefox_accept_insecure_certs", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "firefox_custom_args", "TEXT DEFAULT ''");
        addColumnIfMissing(statement, "projects", "edge_start_maximized", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "edge_in_private", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "edge_accept_insecure_certs", "INTEGER DEFAULT 1");
        addColumnIfMissing(statement, "projects", "edge_custom_args", "TEXT DEFAULT ''");
    }

    private static void addColumnIfMissing(Statement statement, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(statement, table, column)) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean columnExists(Statement statement, String table, String column) throws SQLException {
        try (var rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String current = rs.getString("name");
                if (column.equalsIgnoreCase(current)) {
                    return true;
                }
            }
        }
        return false;
    }
}
