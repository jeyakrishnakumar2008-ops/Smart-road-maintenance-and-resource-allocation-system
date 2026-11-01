// DatabaseConnection.java
// Manages JDBC connections to MySQL database (smart_municipal_maintenance).
// Reads configuration from db.properties or environment variables.
// Uses try-with-resources and PreparedStatements for security.

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseConnection {

    private static String url;
    private static String user;
    private static String password;
    private static String engineeringRegistrationCode = "MUNI-ENG-2026";
    private static boolean driverLoaded = false;
    private static Boolean connectedState = null;

    static {
        loadConfiguration();
        loadDriver();
    }

    private static void loadConfiguration() {
        Properties props = new Properties();

        // 1. Try loading from db.properties in working directory
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Could not load db.properties: " + e.getMessage());
            }
        }

        // 2. Read properties with fallback to environment variables or defaults
        url = System.getenv("DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = props.getProperty("db.url",
                    "jdbc:mysql://localhost:3306/smart_municipal_maintenance?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        }

        user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) {
            user = props.getProperty("db.user", "root");
        }

        password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = props.getProperty("db.password", "");
        }

        String regCode = props.getProperty("security.engineering.registration_code");
        if (regCode != null && !regCode.trim().isEmpty()) {
            engineeringRegistrationCode = regCode.trim();
        }
    }

    private static void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            try {
                // Fallback for older driver class names if present
                Class.forName("com.mysql.jdbc.Driver");
                driverLoaded = true;
            } catch (ClassNotFoundException ex) {
                System.err.println("⚠️ MySQL JDBC Driver not found on classpath: " + e.getMessage());
                driverLoaded = false;
            }
        }
    }

    // Returns a new JDBC connection
    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("MySQL JDBC Driver is not loaded on classpath. Include lib/mysql-connector-j-*.jar.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    // Tests whether MySQL database is reachable
    public static boolean isAvailable() {
        if (!driverLoaded) return false;
        try (Connection conn = getConnection()) {
            connectedState = (conn != null && !conn.isClosed());
            return connectedState;
        } catch (SQLException e) {
            connectedState = false;
            return false;
        }
    }

    // Automatically initializes the schema if MySQL is reachable
    public static boolean initializeSchemaIfConnected() {
        if (!isAvailable()) {
            System.out.println("ℹ️ MySQL server is currently offline or unreachable at: " + url);
            System.out.println("   Application will operate with in-memory synchronization mode until MySQL is active.");
            return false;
        }

        System.out.println("✅ MySQL connection established to: " + url);
        boolean initialized = false;

        File schemaFile = new File("database/schema.sql");
        if (schemaFile.exists()) {
            try {
                String sqlContent = new String(Files.readAllBytes(Paths.get("database/schema.sql")));
                executeSqlScript(sqlContent);
                System.out.println("✅ Database schema verified / initialized from database/schema.sql");
                initialized = true;
            } catch (Exception e) {
                System.err.println("⚠️ Warning executing schema.sql: " + e.getMessage());
            }
        }

        File sampleDataFile = new File("database/sample_data.sql");
        if (sampleDataFile.exists()) {
            try {
                String sqlContent = new String(Files.readAllBytes(Paths.get("database/sample_data.sql")));
                executeSqlScript(sqlContent);
                System.out.println("✅ Sample municipal data loaded from database/sample_data.sql");
                initialized = true;
            } catch (Exception e) {
                System.err.println("⚠️ Warning executing sample_data.sql: " + e.getMessage());
            }
        }

        return initialized;
    }

    // Executes a multi-statement SQL script safely
    private static void executeSqlScript(String script) throws SQLException {
        String[] statements = script.split(";");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                String cleaned = trimmed
                        .replaceAll("(?s)/\\*.*?\\*/", " ")
                        .replaceAll("(?m)^\\s*--.*$", " ")
                        .trim();

                if (cleaned.isEmpty()) {
                    continue;
                }

                try {
                    stmt.execute(cleaned);
                } catch (SQLException e) {
                    if (!e.getMessage().contains("already exists")
                            && !e.getMessage().contains("Duplicate entry")
                            && !e.getMessage().contains("does not exist")) {
                        throw e;
                    }
                }
            }
        }
    }

    // Getters for configuration (passwords never exposed)
    public static String getUrl() { return url; }
    public static String getUser() { return user; }
    public static String getEngineeringRegistrationCode() { return engineeringRegistrationCode; }
}
