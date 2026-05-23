package Project.model;

import javafx.scene.paint.Color;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized UI theme management with system theme detection.
 * Handles dark/light mode switching and color palette management.
 */
public final class UITheme {
    public enum ThemeMode {
        DARK, LIGHT, SYSTEM
    }

    private static ThemeMode themeMode = ThemeMode.LIGHT;
    private static boolean darkMode = false;
    private static boolean systemThemeInitialized = false;

    // Preferences caching for performance
    private static final Preferences PREFS = Preferences.userNodeForPackage(UITheme.class);
    private static final Logger log = LoggerFactory.getLogger(UITheme.class);

    // Color caching for performance
    private static volatile Color cachedBgDeep, cachedBgSurface, cachedBgElevated;
    private static volatile Color cachedDisplayBg, cachedTextPrimary, cachedTextDim;
    private static volatile Color cachedGlassFill, cachedGlassHover, cachedGlassPress, cachedGlassBorder;
    private static volatile boolean colorsCached = false;

    // Stub for removed AWT font lookup (new JavaFX UI does not use these constants)
    private static String getSystemFont(String... fontNames) {
        return fontNames.length > 0 ? fontNames[0] : "System";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FONTS (kept for compatibility with any remaining legacy code)
    // ═══════════════════════════════════════════════════════════════════
    private static final String DISPLAY_FONT_NAME = getSystemFont("San Francisco Display", "Helvetica Neue", "System");
    private static final String TEXT_FONT_NAME = getSystemFont("San Francisco Text", "Helvetica Neue", "System");

    // These are now unused in the minimal JavaFX build but kept to avoid breaking other files
    public static final Object FONT_DISPLAY = null;
    public static final Object FONT_BTN_LG = null;
    public static final Object FONT_BTN_SM = null;
    public static final Object FONT_LABEL = null;
    public static final Object FONT_STATUS = null;
    public static final Object FONT_MONO = null;

    // ═══════════════════════════════════════════════════════════════════
    //  RADIUS & SIZES (iOS-inspired - more rounded)
    // ═══════════════════════════════════════════════════════════════════
    public static final int RADIUS_BTN = 16;      // More rounded like iOS buttons
    public static final int RADIUS_PANEL = 20;    // Rounded panels
    public static final int RADIUS_DIALOG = 24;   // Very rounded dialogs

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR PALETTE - DARK MODE (iOS-inspired)
    // ═══════════════════════════════════════════════════════════════════
    private static final Color DARK_BG_DEEP = Color.rgb(28, 28, 30);        // iOS dark background
    private static final Color DARK_BG_SURFACE = Color.rgb(44, 44, 46);     // iOS secondary background
    private static final Color DARK_BG_ELEVATED = Color.rgb(58, 58, 60);    // iOS elevated surface
    private static final Color DARK_DISPLAY_BG = Color.rgb(0, 0, 0, 80/255.0);   // Semi-transparent black
    private static final Color DARK_TEXT_PRIMARY = Color.rgb(255, 255, 255); // Pure white text
    private static final Color DARK_TEXT_DIM = Color.rgb(174, 174, 178);     // iOS secondary text
    private static final Color DARK_GLASS_FILL = Color.rgb(58, 58, 60, 180/255.0); // Subtle fill
    private static final Color DARK_GLASS_HOVER = Color.rgb(72, 72, 74, 200/255.0); // Lighter on hover
    private static final Color DARK_GLASS_PRESS = Color.rgb(44, 44, 46, 220/255.0); // Darker on press
    private static final Color DARK_GLASS_BORDER = Color.rgb(84, 84, 88, 120/255.0); // Subtle border

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR PALETTE - LIGHT MODE (iOS-inspired)
    // ═══════════════════════════════════════════════════════════════════
    private static final Color LIGHT_BG_DEEP = Color.rgb(248, 248, 248);    // iOS light background
    private static final Color LIGHT_BG_SURFACE = Color.rgb(242, 242, 247);  // iOS secondary background
    private static final Color LIGHT_BG_ELEVATED = Color.rgb(255, 255, 255); // Pure white for elevated
    private static final Color LIGHT_DISPLAY_BG = Color.rgb(255, 255, 255);  // White display background
    private static final Color LIGHT_TEXT_PRIMARY = Color.rgb(0, 0, 0);      // Pure black text
    private static final Color LIGHT_TEXT_DIM = Color.rgb(142, 142, 147);    // iOS secondary text
    private static final Color LIGHT_GLASS_FILL = Color.rgb(242, 242, 247, 200/255.0); // Subtle gray fill
    private static final Color LIGHT_GLASS_HOVER = Color.rgb(230, 230, 235, 220/255.0); // Lighter gray on hover
    private static final Color LIGHT_GLASS_PRESS = Color.rgb(210, 210, 215, 240/255.0); // Darker gray on press
    private static final Color LIGHT_GLASS_BORDER = Color.rgb(198, 198, 200, 160/255.0); // Light border

    // ═══════════════════════════════════════════════════════════════════
    //  ACCENT COLORS (iOS-inspired)
    // ═══════════════════════════════════════════════════════════════════
    public static final Color ACCENT_BLUE = Color.rgb(0, 122, 255);        // iOS blue
    public static final Color ACCENT_GREEN = Color.rgb(52, 199, 89);       // iOS green
    public static final Color ACCENT_ORANGE = Color.rgb(255, 149, 0);      // iOS orange
    public static final Color ACCENT_RED = Color.rgb(255, 59, 48);         // iOS red
    public static final Color ACCENT_PURPLE = Color.rgb(175, 82, 222);     // iOS purple
    public static final Color ACCENT_PINK = Color.rgb(255, 45, 85);        // iOS pink

    // Legacy aliases for backward compatibility
    public static final Color ACCENT_CYAN = ACCENT_BLUE;
    public static final Color ACCENT_AMBER = ACCENT_ORANGE;

    // ═══════════════════════════════════════════════════════════════════
    //  INITIALIZATION & DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initialize system theme detection based on OS settings.
     * Should be called once at application startup.
     */
    public static void initializeSystemTheme() {
        if (systemThemeInitialized) return;

        // Check for saved preference first
        if (PREFS.getBoolean("theme_saved", false)) {
            darkMode = PREFS.getBoolean("theme_dark_mode", true);
            systemThemeInitialized = true;
            log.info("Theme loaded from preferences: {}", darkMode ? "Dark" : "Light");
            return;
        }

        // Detect system theme
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean detected = false;

        if (osName.contains("mac")) {
            darkMode = "darkaqua".equalsIgnoreCase(System.getProperty("apple.awt.application.appearance"));
            detected = true;
        } else if (osName.contains("windows")) {
            darkMode = detectWindowsTheme();
            detected = true;
        } else if (osName.contains("linux")) {
            darkMode = "1".equals(System.getenv("GTK_THEME_PREFER_DARK")) ||
                      (System.getenv("GTK_THEME") != null &&
                       System.getenv("GTK_THEME").toLowerCase().contains("dark"));
            detected = true;
        }

        // Save detected theme
        PREFS.putBoolean("theme_saved", true);
        PREFS.putBoolean("theme_dark_mode", darkMode);
        try {
            PREFS.flush();
        } catch (Exception ignored) {}

        systemThemeInitialized = true;
        log.info("System theme detected: {} {}", darkMode ? "Dark" : "Light", detected ? "(auto)" : "(default)");
    }

    /**
     * Detect Windows system theme (Windows 10+)
     */
    private static boolean detectWindowsTheme() {
        try {
            // Check Windows registry for AppsUseLightTheme
            // 0 = dark, 1 = light
            Process process = Runtime.getRuntime().exec(
                "reg query HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize /v AppsUseLightTheme"
            );
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("0x0")) return true;  // Dark mode
                if (line.contains("0x1")) return false; // Light mode
            }
        } catch (Exception ignored) {}
        return true; // Default to dark
    }

    // ═══════════════════════════════════════════════════════════════════
    //  THEME SWITCHING
    // ═══════════════════════════════════════════════════════════════════

    public static void setDarkMode(boolean dark) {
        darkMode = dark;
        PREFS.putBoolean("theme_dark_mode", dark);
        try {
            PREFS.flush();
        } catch (Exception ignored) {}
        log.info("Theme changed to: {}", dark ? "Dark" : "Light");
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static ThemeMode getThemeMode() {
        return themeMode;
    }

    public static void setThemeMode(ThemeMode mode) {
        themeMode = mode;
        switch (mode) {
            case DARK -> darkMode = true;
            case LIGHT -> darkMode = false;
            case SYSTEM -> {
                // Re-initialize to detect system theme
                systemThemeInitialized = false;
                initializeSystemTheme();
            }
        }
        // Clear color cache when theme changes
        colorsCached = false;
        PREFS.putBoolean("theme_saved", true);
        PREFS.putBoolean("theme_dark_mode", darkMode);
        try {
            PREFS.flush();
        } catch (Exception ignored) {}
        log.info("Theme changed to: {} ({})", mode, darkMode ? "Dark" : "Light");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR ACCESSORS (with caching for performance)
    // ═══════════════════════════════════════════════════════════════════

    public static Color BG_DEEP() {
        if (!colorsCached) updateColorCache();
        return cachedBgDeep;
    }

    public static Color BG_SURFACE() {
        if (!colorsCached) updateColorCache();
        return cachedBgSurface;
    }

    public static Color BG_ELEVATED() {
        if (!colorsCached) updateColorCache();
        return cachedBgElevated;
    }

    public static Color DISPLAY_BG() {
        if (!colorsCached) updateColorCache();
        return cachedDisplayBg;
    }

    public static Color TEXT_PRIMARY() {
        if (!colorsCached) updateColorCache();
        return cachedTextPrimary;
    }

    public static Color TEXT_DIM() {
        if (!colorsCached) updateColorCache();
        return cachedTextDim;
    }

    public static Color GLASS_FILL() {
        if (!colorsCached) updateColorCache();
        return cachedGlassFill;
    }

    public static Color GLASS_HOVER() {
        if (!colorsCached) updateColorCache();
        return cachedGlassHover;
    }

    public static Color GLASS_PRESS() {
        if (!colorsCached) updateColorCache();
        return cachedGlassPress;
    }

    public static Color GLASS_BORDER() {
        if (!colorsCached) updateColorCache();
        return cachedGlassBorder;
    }

    /**
     * Update color cache for current theme
     */
    private static void updateColorCache() {
        if (colorsCached) return;

        cachedBgDeep = darkMode ? DARK_BG_DEEP : LIGHT_BG_DEEP;
        cachedBgSurface = darkMode ? DARK_BG_SURFACE : LIGHT_BG_SURFACE;
        cachedBgElevated = darkMode ? DARK_BG_ELEVATED : LIGHT_BG_ELEVATED;
        cachedDisplayBg = darkMode ? DARK_DISPLAY_BG : LIGHT_DISPLAY_BG;
        cachedTextPrimary = darkMode ? DARK_TEXT_PRIMARY : LIGHT_TEXT_PRIMARY;
        cachedTextDim = darkMode ? DARK_TEXT_DIM : LIGHT_TEXT_DIM;
        cachedGlassFill = darkMode ? DARK_GLASS_FILL : LIGHT_GLASS_FILL;
        cachedGlassHover = darkMode ? DARK_GLASS_HOVER : LIGHT_GLASS_HOVER;
        cachedGlassPress = darkMode ? DARK_GLASS_PRESS : LIGHT_GLASS_PRESS;
        cachedGlassBorder = darkMode ? DARK_GLASS_BORDER : LIGHT_GLASS_BORDER;

        colorsCached = true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a color with adjusted brightness
     */
    public static Color brighten(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed() * 255 * factor));
        int g = Math.min(255, (int) (c.getGreen() * 255 * factor));
        int b = Math.min(255, (int) (c.getBlue() * 255 * factor));
        return Color.rgb(r, g, b, c.getOpacity());
    }

    /**
     * Creates a color with adjusted opacity
     */
    public static Color withAlpha(Color c, int alpha) {
        return Color.rgb((int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255), alpha/255.0);
    }

    /**
     * Get theme name for display
     */
    public static String getThemeName() {
        return darkMode ? "Dark" : "Light";
    }
}