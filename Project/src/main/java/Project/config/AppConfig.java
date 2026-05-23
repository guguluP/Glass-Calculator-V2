package Project.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.prefs.*;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized configuration manager for GlassCalculator.
 * Handles secure storage of database and API credentials.
 *
 * @author GlassCalculator Team
 * @version 1.0
 */
public final class AppConfig {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppConfig.class);
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    // Preference keys
    private static final String KEY_FIRST_RUN = "firstRun";
    private static final String KEY_DB_HOST = "dbHost";
    private static final String KEY_DB_PORT = "dbPort";
    private static final String KEY_DB_USER = "dbUser";
    private static final String KEY_DB_PASSWORD = "dbPassword";
    private static final String KEY_DB_NAME = "dbName";
    private static final String KEY_DB_TYPE = "dbType"; // mysql, sqlite, etc.
    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_SETUP_COMPLETE = "setupComplete";

    /**
     * Checks if this is the first run of the application.
     * @return true if first run, false otherwise
     */
    public static boolean isFirstRun() {
        return PREFS.getBoolean(KEY_FIRST_RUN, true);
    }

    /**
     * Marks the setup wizard as complete.
     */
    public static void markSetupComplete() {
        PREFS.putBoolean(KEY_SETUP_COMPLETE, true);
        PREFS.putBoolean(KEY_FIRST_RUN, false);
        try {
            PREFS.flush();
        } catch (BackingStoreException e) {
            log.error("Failed to save setup completion: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if setup wizard has been completed.
     * @return true if setup is complete, false otherwise
     */
    public static boolean isSetupComplete() {
        return PREFS.getBoolean(KEY_SETUP_COMPLETE, false);
    }

    // ════════════════════════════════════════════════════════════════
    // DATABASE CONFIGURATION
    // ════════════════════════════════════════════════════════════════

    /**
     * Saves database configuration.
     * @param dbType Database type (mysql, sqlite, postgresql, etc.)
     * @param host Database host (localhost, etc.)
     * @param port Database port (3306, 5432, etc.)
     * @param username Database username
     * @param password Database password (will be stored securely)
     * @param dbName Database name
     */
    public static void setDatabaseConfig(String dbType, String host, int port,
                                         String username, String password, String dbName) {
        PREFS.put(KEY_DB_TYPE, dbType);
        PREFS.put(KEY_DB_HOST, host);
        PREFS.putInt(KEY_DB_PORT, port);
        PREFS.put(KEY_DB_USER, username);
        PREFS.put(KEY_DB_PASSWORD, encryptPassword(password));
        PREFS.put(KEY_DB_NAME, dbName);

        try {
            PREFS.flush();
            log.info("Database configuration saved");
        } catch (BackingStoreException e) {
            log.error("Failed to save database config: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the database type.
     * @return Database type (mysql, sqlite, etc.)
     */
    public static String getDatabaseType() {
        return PREFS.get(KEY_DB_TYPE, "sqlite");
    }

    /**
     * Gets the database host.
     * @return Database host address
     */
    public static String getDatabaseHost() {
        return PREFS.get(KEY_DB_HOST, "localhost");
    }

    /**
     * Gets the database port.
     * @return Database port number
     */
    public static int getDatabasePort() {
        return PREFS.getInt(KEY_DB_PORT, 3306);
    }

    /**
     * Gets the database username.
     * @return Database username
     */
    public static String getDatabaseUser() {
        return PREFS.get(KEY_DB_USER, "root");
    }

    /**
     * Gets the database password (decrypted).
     * @return Database password
     */
    public static String getDatabasePassword() {
        String encrypted = PREFS.get(KEY_DB_PASSWORD, "");
        return encrypted.isEmpty() ? "" : decryptPassword(encrypted);
    }

    /**
     * Gets the database name.
     * @return Database name
     */
    public static String getDatabaseName() {
        return PREFS.get(KEY_DB_NAME, "mydb");
    }

    /**
     * Checks if database is configured.
     * @return true if all required DB credentials are set based on database type, false otherwise
     */
    public static boolean isDatabaseConfigured() {
        String dbType = getDatabaseType().toLowerCase();
        if ("sqlite".equals(dbType)) {
            // SQLite doesn't require credentials
            return true;
        } else {
            // MySQL, PostgreSQL require user and password
            return !getDatabasePassword().isEmpty() && !getDatabaseUser().isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // API KEY CONFIGURATION
    // ════════════════════════════════════════════════════════════════

    /**
     * Saves API key for currency exchange service.
     * @param apiKey The API key (will be stored securely)
     */
    public static void setApiKey(String apiKey) {
        PREFS.put(KEY_API_KEY, encryptPassword(apiKey));
        try {
            PREFS.flush();
            log.info("API key configured");
        } catch (BackingStoreException e) {
            log.error("Failed to save API key: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the API key (decrypted).
     * @return The API key, or empty string if not configured
     */
    public static String getApiKey() {
        String encrypted = PREFS.get(KEY_API_KEY, "");
        return encrypted.isEmpty() ? "" : decryptPassword(encrypted);
    }

    /**
     * Checks if API key is configured.
     * @return true if API key is set, false otherwise
     */
    public static boolean isApiKeyConfigured() {
        return !getApiKey().isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // SECURITY / ENCRYPTION  (JCA AES-256-CBC, with legacy XOR fallback)
    // ════════════════════════════════════════════════════════════════

    private static final byte[] SECRET_KEY =
        "GlassCalc2024!AES256SecureKey32B".getBytes(StandardCharsets.UTF_8); // 32 bytes

    /**
     * Encrypts password using AES-256-CBC with random IV (JCA).
     * Stored format: base64(iv):base64(ciphertext)
     * Much stronger than previous XOR. Still uses fixed key (see note).
     * NOTE: For true security on unattended desktop, integrate OS keyring (e.g. java-keyring).
     *
     * @param password Plain text password
     * @return Encrypted string in new format, or empty on failure
     */
    private static String encryptPassword(String password) {
        if (password == null || password.isEmpty()) return "";
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" +
                   Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            log.warn("AES encryption failed", e);
            return "";
        }
    }

    /**
     * Decrypts using new AES format; falls back to legacy XOR for old stored values.
     */
    private static String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        if (encrypted.contains(":")) {
            try {
                String[] p = encrypted.split(":", 2);
                byte[] iv = Base64.getDecoder().decode(p[0]);
                byte[] ct = Base64.getDecoder().decode(p[1]);
                SecretKeySpec key = new SecretKeySpec(SECRET_KEY, "AES");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                byte[] pt = cipher.doFinal(ct);
                return new String(pt, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("AES decrypt failed, trying legacy", e);
            }
        }
        // Legacy XOR fallback for users who had data before upgrade
        return decryptXor(encrypted);
    }

    /**
     * Legacy XOR decrypt (kept only for migration of old prefs data).
     */
    private static String decryptXor(String hexOrOld) {
        if (hexOrOld == null || hexOrOld.isEmpty()) return "";
        try {
            // support both old hex and plain (if any)
            byte[] bytes;
            if (hexOrOld.matches("^[0-9a-fA-F]+$") && hexOrOld.length() % 2 == 0) {
                bytes = hexToBytes(hexOrOld);
            } else {
                bytes = hexOrOld.getBytes(StandardCharsets.UTF_8); // unlikely
            }
            byte[] key = "GlassCalc2024!@#".getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                out[i] = (byte) (bytes[i] ^ key[i % key.length]);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt password: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Converts byte array to hex string (kept for legacy decrypt).
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Converts hex string to byte array (kept for legacy decrypt).
     */
    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * Resets all configuration (useful for testing or reconfiguration).
     */
    public static void resetConfig() {
        try {
            PREFS.clear();
            PREFS.flush();
            log.info("Configuration reset");
        } catch (BackingStoreException e) {
            log.error("Failed to reset configuration: {}", e.getMessage(), e);
        }
    }
}