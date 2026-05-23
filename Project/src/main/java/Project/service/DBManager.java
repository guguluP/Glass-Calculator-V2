package Project.service;

import java.sql.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import Project.config.AppConfig;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database manager with improved security, thread safety, and reconnection logic.
 * Now uses AppConfig for secure credential storage.
 *
 * @author GlassCalculator Team
 * @version 1.0
 */
public class DBManager {
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 1000;
    private static final int EXPRESSION_MAX_LENGTH = 500;
    private static final int RESULT_MAX_LENGTH = 100;
    private static final Logger log = LoggerFactory.getLogger(DBManager.class);

    // Core runtime fields
    private ExecutorService dbExec = Executors.newSingleThreadExecutor();
    private HikariDataSource dataSource;
    private volatile boolean ready = false;
    private volatile boolean driverLoaded = false;
    private int reconnectAttempts = 0;

    private String jdbcUrl;
    private String username;
    private String password;
    private String host;
    private String dbName;
    private String dbType;
    private int port;

    public DBManager() {
        // No-argument constructor for pure JavaFX usage
    }

    /**
     * Initializes the database connection using configured credentials.
     * If no credentials are configured, this will fail gracefully.
     */
    public void init() {
        dbExec.execute(() -> {
            try {
                // Check if database is configured
                if (!AppConfig.isDatabaseConfigured()) {
                    log.warn("Database not configured. User should run setup wizard.");
                    driverLoaded = false;
                    ready = false;
                    return;
                }

                // Build JDBC URL based on database type
                String dbType = AppConfig.getDatabaseType().toLowerCase();
                buildJdbcUrl(dbType);

                // Attempt to load driver based on database type
                try {
                    switch (dbType) {
                        case "mysql":
                            Class.forName("com.mysql.cj.jdbc.Driver");
                            break;
                        case "sqlite":
                            Class.forName("org.sqlite.JDBC");
                            break;
                        case "postgresql":
                            Class.forName("org.postgresql.Driver");
                            break;
                        default:
                            Class.forName("com.mysql.cj.jdbc.Driver");
                    }
                    driverLoaded = true;
                    log.info("JDBC driver loaded for {}", dbType);
                } catch (ClassNotFoundException cnfe) {
                    log.error("JDBC driver NOT found for {} - make sure {} is in classpath", dbType, getDriverName(dbType));
                    driverLoaded = false;
                    return;
                }

                // Try to connect and ensure database exists
                ensureDatabase();
                setupDataSource();
                createTable();
                ready = true;
                log.info("Database connected and ready via HikariCP pool");
                // No UI callback needed in pure JavaFX version

            } catch (SQLException e) {
                log.error("Database connection failed: {}", e.getMessage(), e);
                ready = false;
            } catch (Exception e) {
                log.error("Unexpected error during DB init: {}", e.getMessage(), e);
                ready = false;
            }
        });
    }

    /**
     * Builds and caches JDBC URL based on database type.
     */
    private void buildJdbcUrl(String dbType) {
        // Cache configuration values to avoid repeated AppConfig lookups
        this.dbType = dbType;
        this.username = AppConfig.getDatabaseUser();
        this.password = AppConfig.getDatabasePassword();
        this.host = AppConfig.getDatabaseHost();
        this.port = AppConfig.getDatabasePort();
        this.dbName = AppConfig.getDatabaseName();

        switch (dbType) {
            case "mysql":
                this.jdbcUrl = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true",
                    host, port, dbName);
                break;
            case "sqlite":
                this.jdbcUrl = "jdbc:sqlite:" + dbName + ".db";
                break;
            case "postgresql":
                this.jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    host, port, dbName);
                break;
            default:
                // Default to MySQL
                this.jdbcUrl = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true",
                    host, port, dbName);
        }
    }

    /**
     * Gets the JDBC driver name for a database type.
     */
    private String getDriverName(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "mysql-connector-java";
            case "sqlite" -> "sqlite-jdbc";
            case "postgresql" -> "postgresql";
            default -> "MySQL JDBC Driver";
        };
    }

    /**
     * Sets up HikariCP connection pool for high-performance DB access.
     * Replaces raw DriverManager connections with pooled, auto-validated ones.
     */
    private void setupDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            return; // already initialized
        }
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            if (username != null && !username.isEmpty()) {
                config.setUsername(username);
                config.setPassword(password);
            }
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("GlassCalc-Hikari-" + (dbType != null ? dbType : "db"));

            // DB-specific optimizations
            if ("mysql".equalsIgnoreCase(dbType)) {
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
            } else if ("sqlite".equalsIgnoreCase(dbType)) {
                config.setMaximumPoolSize(1); // SQLite single writer best
                config.setConnectionTestQuery(null);
            }

            dataSource = new HikariDataSource(config);
            log.info("HikariCP pool initialized for {}", dbType);
        } catch (Exception e) {
            log.error("Failed to setup HikariCP: {}", e.getMessage(), e);
            ready = false;
            throw new RuntimeException("Pool init failed", e);
        }
    }

    public void saveAsync(String expression, String result, String type) {
        // Basic sync check (may be stale, full check inside task)
        if (result.equals("Error")) {
            return;
        }
        if (expression.length() > EXPRESSION_MAX_LENGTH) {
            log.warn("Expression too long, not saving");
            return;
        }
        if (result.length() > RESULT_MAX_LENGTH) {
            log.warn("Result too long, not saving");
            return;
        }

        dbExec.execute(() -> {
            if (!driverLoaded || !AppConfig.isDatabaseConfigured()) {
                return;
            }
            try {
                reconnectIfNeeded();
                if (!ready || dataSource == null) {
                    return;
                }
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO calculation_history (type, expression, result) VALUES (?, ?, ?)")) {
                    ps.setString(1, type);
                    ps.setString(2, expression);
                    ps.setString(3, result);
                    ps.executeUpdate();
                    log.info("{} saved", type);
                }
            } catch (SQLException e) {
                log.error("Failed to save history: {}", e.getMessage(), e);
            }
        });
    }

    public void shutdown() {
        ready = false;
        dbExec.execute(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    log.info("HikariCP pool closed");
                }
            } catch (Exception ignored) {
            }
        });

        // Properly shutdown executor
        try {
            dbExec.shutdown();
            if (!dbExec.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isDriverLoaded() {
        return driverLoaded;
    }

    /**
     * Returns true only if fully ready (connected + driver ok).
     */
    public boolean isAvailable() {
        return ready && driverLoaded;
    }

    /**
     * Ensures pool is ready (HikariCP handles reconnection, validation, eviction internally).
     * Kept for API compatibility and initial config reloads.
     */
    private void reconnectIfNeeded() throws SQLException {
        if (jdbcUrl == null || username == null) {
            if (!AppConfig.isDatabaseConfigured()) {
                ready = false;
                driverLoaded = false;
                throw new SQLException("Database not configured");
            }
            String dbType = AppConfig.getDatabaseType().toLowerCase();
            buildJdbcUrl(dbType);
        }
        if (dataSource == null || dataSource.isClosed()) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                throw new SQLException("Failed to initialize pool after " + MAX_RECONNECT_ATTEMPTS + " attempts");
            }
            try {
                reconnectAttempts++;
                log.info("Initializing HikariCP pool (attempt {})...", reconnectAttempts);
                setupDataSource();
                reconnectAttempts = 0;
                ready = true;
                log.info("HikariCP pool ready");
            } catch (Exception e) {
                ready = false;
                if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    throw new SQLException("Pool initialization failed", e);
                }
                try {
                    long delay = RECONNECT_DELAY_MS * (1L << (reconnectAttempts - 1));
                    Thread.sleep(Math.min(delay, 30000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Ensures the database exists before connecting.
     */
    private void ensureDatabase() throws Exception {
        String dbType = AppConfig.getDatabaseType().toLowerCase();

        if ("sqlite".equals(dbType)) {
            // SQLite auto-creates the database file
            return;
        }

        if ("mysql".equals(dbType)) {
            String host = AppConfig.getDatabaseHost();
            int port = AppConfig.getDatabasePort();
            String dbName = AppConfig.getDatabaseName();
            String rootUrl = String.format(
                "jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port);

            try (Connection c = DriverManager.getConnection(rootUrl, username, password);
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
                log.info("Database '{}' ensured", dbName);
            }
        } else if ("postgresql".equals(dbType)) {
            // PostgreSQL typically requires manual database creation
            log.info("PostgreSQL database must be created manually");
        }
    }

    /**
     * Creates the calculation history table if it doesn't exist, or upgrades schema.
     */
    private void createTable() throws SQLException {
        reconnectIfNeeded();
        if (dataSource == null) {
            throw new SQLException("No datasource available");
        }
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            String dbType = AppConfig.getDatabaseType().toLowerCase();
            boolean isSQLite = "sqlite".equals(dbType);

            // Check if table exists
            boolean tableExists = false;
            try (ResultSet rs = s.executeQuery(
                    isSQLite ? "SELECT name FROM sqlite_master WHERE type='table' AND name='calculation_history'"
                             : "SELECT table_name FROM information_schema.tables WHERE table_name='calculation_history'")) {
                tableExists = rs.next();
            } catch (SQLException ignored) {
                // Fallback: try to query the table
                try (ResultSet rs = s.executeQuery("SELECT 1 FROM calculation_history LIMIT 1")) {
                    tableExists = true;
                } catch (SQLException ignored2) {
                }
            }

            if (!tableExists) {
                // Create table
                String createTableSql = isSQLite ?
                    """
                    CREATE TABLE calculation_history (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        type        TEXT NOT NULL,
                        expression  TEXT NOT NULL,
                        result      TEXT NOT NULL,
                        created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """ :
                    """
                    CREATE TABLE calculation_history (
                        id          INTEGER PRIMARY KEY AUTO_INCREMENT,
                        type        VARCHAR(10) NOT NULL,
                        expression  VARCHAR(500) NOT NULL,
                        result      VARCHAR(100) NOT NULL,
                        created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
                s.executeUpdate(createTableSql);
            } else {
                // Check if 'type' column exists, add it if missing
                boolean hasTypeColumn = false;
                try (ResultSet rs = s.executeQuery(
                        isSQLite ? "PRAGMA table_info(calculation_history)"
                                 : "DESCRIBE calculation_history")) {
                    while (rs.next()) {
                        String columnName = isSQLite ? rs.getString("name") : rs.getString("Field");
                        if ("type".equals(columnName)) {
                            hasTypeColumn = true;
                            break;
                        }
                    }
                } catch (SQLException ignored) {
                }

                if (!hasTypeColumn) {
                    // Add type column with default value
                    String alterSql = isSQLite ?
                        "ALTER TABLE calculation_history ADD COLUMN type TEXT NOT NULL DEFAULT 'calc'" :
                        "ALTER TABLE calculation_history ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'calc'";
                    s.executeUpdate(alterSql);
                    log.info("Added 'type' column to existing table");
                }
            }

            log.info("History table ready");
        }
    }

    /**
     * Loads calculation history from database.
     */
    public void loadCalcHistoryAsync(java.util.function.Consumer<java.util.List<String>> callback) {
        dbExec.execute(() -> {
            java.util.List<String> history = new java.util.ArrayList<>();
            if (!driverLoaded || !AppConfig.isDatabaseConfigured()) {
                Platform.runLater(() -> callback.accept(history));
                return;
            }
            try {
                reconnectIfNeeded();
                if (dataSource == null) {
                    Platform.runLater(() -> callback.accept(history));
                    return;
                }
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "SELECT expression, result FROM calculation_history WHERE type = 'calc' ORDER BY created_at DESC LIMIT 100")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String expr = rs.getString("expression");
                            String res = rs.getString("result");
                            history.add(expr + " = " + res);
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to load calc history: {}", e.getMessage(), e);
            }
            Platform.runLater(() -> callback.accept(history));
        });
    }

    /**
     * Loads conversion history from database.
     */
    public void loadConvHistoryAsync(java.util.function.Consumer<java.util.List<String>> callback) {
        dbExec.execute(() -> {
            java.util.List<String> history = new java.util.ArrayList<>();
            if (!driverLoaded || !AppConfig.isDatabaseConfigured()) {
                Platform.runLater(() -> callback.accept(history));
                return;
            }
            try {
                reconnectIfNeeded();
                if (dataSource == null) {
                    Platform.runLater(() -> callback.accept(history));
                    return;
                }
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "SELECT expression, result FROM calculation_history WHERE type = 'conv' ORDER BY created_at DESC LIMIT 100")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String expr = rs.getString("expression");
                            String res = rs.getString("result");
                            history.add(expr + " = " + res);
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to load conv history: {}", e.getMessage(), e);
            }
            Platform.runLater(() -> callback.accept(history));
        });
    }

    /**
     * Clears all calculation history.
     */
    public void clearHistory() {
        dbExec.execute(() -> {
            if (!driverLoaded || !AppConfig.isDatabaseConfigured()) {
                return;
            }
            try {
                reconnectIfNeeded();
                if (!ready || dataSource == null) {
                    return;
                }
                try (Connection c = dataSource.getConnection();
                     Statement s = c.createStatement()) {
                    s.executeUpdate("DELETE FROM calculation_history");
                    log.info("History cleared");
                }
            } catch (SQLException e) {
                log.error("Failed to clear history: {}", e.getMessage(), e);
            }
        });
    }
}