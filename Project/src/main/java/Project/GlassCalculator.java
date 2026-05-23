package Project;

import Project.config.AppConfig;
import Project.engine.CalculatorEngine;
import Project.service.CurrencyService;
import Project.service.DBManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlassCalculator extends Application {

    private static final Logger log = LoggerFactory.getLogger(GlassCalculator.class);
    private static Stage primaryStageRef;  // for dialog owner + macOS error dialog stability
    private final CalculatorEngine engine = new CalculatorEngine();
    private final CurrencyService fx = new CurrencyService();
    private DBManager db;  // for database-backed history (used together with Preferences)

    // Memory register for M+, M-, MC, MR
    private double memory = 0.0;
    private Label memoryStatus;   // 💾 M: value status bar

    // Scientific mode: true = radians, false = degrees
    private boolean radiansMode = true;

    // ========== BASIC + SCIENTIFIC STATE ==========
    private ScalingDisplay calcDisplay;   // Custom glass scaling display
    private StringBuilder expression = new StringBuilder();
    private boolean resultShown = false;
    
    // Font caching for performance
    private Font cachedFont12;
    private Font cachedFont14;
    private Font cachedFont16;
    private Font cachedFont18;
    private Font cachedFont20;

    // ========== CURRENCY STATE ==========
    private TextField amountField;
    private ComboBox<String> fromBox, toBox;
    private Label currencyResult;
    private Label currencyStatus;

    private final String[] CURRENCIES = {
            "USD", "EUR", "INR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "RUB",
            "BRL", "ZAR", "MXN", "SGD", "HKD", "SEK", "NOK", "DKK", "KRW", "TRY"
    };

    // Shared history
    private ListView<String> historyList;
    // Fast membership set for history to avoid O(n) contains checks
    private final Set<String> historySet = Collections.synchronizedSet(new LinkedHashSet<>());

    // Single-threaded IO executor for prefs/db persistence to avoid blocking the FX thread
    private final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IO-Worker"); t.setDaemon(true); return t;
    });

    // Root pane (used for advanced overlays like Toast). Use StackPane to host overlays/drawers
    private StackPane root;
    private TabPane tabPane;
    private double winX = -1, winY = -1, winW = -1, winH = -1;

    // Main border pane and rail/drawer state
    private BorderPane mainBorderPane;
    private VBox rail;

    // Drawer state and nodes
    private boolean drawerOpen = false;
    private VBox drawer;
    private Region overlay;
    private int lastTabIndex = 0;

    // Bottom status bar
    private Label dbStatusLabel;
    private Label prefStatusLabel;

    // Application icon image (loaded from resources)
    private javafx.scene.image.Image appIconImage;
    
    // Glass effect style cache (for common opacity values)
    private final java.util.Map<Double, String> glassEffectCache = new java.util.HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStageRef = primaryStage;
        
        // Initialize font cache for faster font access throughout the app
        initFontCache();
        
        // Load application icon asynchronously on background thread to avoid blocking UI
        new Thread(() -> {
            try {
                var is = getClass().getResourceAsStream("/icons/AppIcon.png");
                if (is != null) {
                    appIconImage = new javafx.scene.image.Image(is);
                    if (!appIconImage.isError()) {
                        Platform.runLater(() -> {
                            if (primaryStage != null && !primaryStage.getIcons().contains(appIconImage)) {
                                primaryStage.getIcons().add(appIconImage);
                            }
                        });
                    }
                } else {
                    log.debug("App icon resource not found at /icons/AppIcon.png");
                }

                // On macOS, try to set the Dock icon for non-bundled runs using com.apple.eawt.Application
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    var is2 = getClass().getResourceAsStream("/icons/AppIcon.png");
                    if (is2 != null) {
                        try {
                            java.awt.image.BufferedImage awtImg = javax.imageio.ImageIO.read(is2);
                            if (awtImg != null) {
                                try {
                                    Class<?> appClass = Class.forName("com.apple.eawt.Application");
                                    java.lang.reflect.Method getApp = appClass.getMethod("getApplication");
                                    Object app = getApp.invoke(null);
                                    java.lang.reflect.Method setDock = appClass.getMethod("setDockIconImage", java.awt.Image.class);
                                    setDock.invoke(app, awtImg);
                                } catch (ClassNotFoundException cnf) {
                                    // Not on Apple runtime - ignore
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("Failed creating AWT image for dock icon", ex);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to load application icon", ex);
            }
        }, "IconLoader").start();
        // Top display used by calculator modes - Custom ScalingDisplay with advanced glass
        calcDisplay = new ScalingDisplay();
        calcDisplay.setPrefHeight(72);
        calcDisplay.setMaxWidth(Double.MAX_VALUE);
        calcDisplay.setMaxHeight(90);  // allow slight growth on tall windows

        // History (shared)
        historyList = new ListView<>();
        historyList.setPrefHeight(110);
        historyList.setMaxHeight(120);
        historyList.setStyle(
            "-fx-background-color: rgba(20,20,25,0.6);" +
            "-fx-text-fill: #ccc;" +
            "-fx-background-radius: 10;"
        );
        // Memory status bar (💾 M: value)
        memoryStatus = new Label("💾 M: 0");
        memoryStatus.setFont(getFont(12));
        memoryStatus.setStyle("-fx-text-fill: #ffeb3b; -fx-padding: 2 8;");
        updateMemoryStatus();   // initial state

        // Initialize DB (non-blocking) + load from BOTH Preferences and Database
        db = new DBManager();
        db.init();
        loadHistoryFromBoth();
        updateMemoryStatus();
        restorePreferencesState();
        if (AppConfig.isFirstRun()) {
            Platform.runLater(this::showSettingsDialog);
        }

        // ========== TAB 1: BASIC ==========
        Tab basicTab = new Tab("Basic", createBasicPane());
        basicTab.setClosable(false);

        // ========== TAB 2: SCIENTIFIC ==========
        Tab sciTab = new Tab("Scientific", createScientificPane());
        sciTab.setClosable(false);

        // ========== TAB 3: CURRENCY ==========
        Tab currencyTab = new Tab("Currency", createCurrencyPane());
        currencyTab.setClosable(false);

        tabPane = new TabPane(basicTab, sciTab, currencyTab);
        tabPane.setTabMinWidth(90);

        tabPane.setStyle(
            "-fx-background-color: rgba(30,32,42,0.4);" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: rgba(255,255,255,0.12);" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 16;" +
            "-fx-padding: 4;"
        );

        restoreLastTab();
        tabPane.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            lastTabIndex = nv.intValue();
            saveLastTab();
        });

        mainBorderPane = new BorderPane();

        // Left persistent rail with a hamburger menu
        rail = new VBox();
        rail.setPadding(new Insets(8, 4, 8, 4));
        rail.setPrefWidth(48);
        applyGlassEffect(rail, 0.06);

        Button menuBtn = new Button("☰");
        menuBtn.setFont(getFont(14));
        menuBtn.setPrefSize(36, 36);
        menuBtn.getStyleClass().addAll("glass-button", "sidebar-btn", "rail-btn");
        // If app icon loaded, show it on the rail menu button
        if (appIconImage != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(appIconImage);
            iv.setFitWidth(18);
            iv.setFitHeight(18);
            menuBtn.setGraphic(iv);
        }
        menuBtn.setOnAction(e -> toggleDrawer());
        rail.getChildren().add(menuBtn);

        // Drawer (hidden by default) containing History and Settings
        drawer = new VBox(10);
        drawer.setPadding(new Insets(10));
        drawer.setPrefWidth(240);
        applyGlassEffect(drawer, 0.12);

        Button histBtn = new Button("History");
        histBtn.setFont(getFont(11));
        histBtn.setMaxWidth(Double.MAX_VALUE);
        histBtn.getStyleClass().addAll("glass-button", "sidebar-btn");
        histBtn.setOnAction(e -> { showHistoryDialog(); toggleDrawer(); });

        Button setBtn = new Button("Settings");
        setBtn.setFont(getFont(11));
        setBtn.setMaxWidth(Double.MAX_VALUE);
        setBtn.getStyleClass().addAll("glass-button", "sidebar-btn");
        setBtn.setOnAction(e -> { showSettingsDialog(); toggleDrawer(); });

        drawer.getChildren().addAll(histBtn, setBtn);

        // No overlay push mode: when drawer is toggled we replace left rail with drawer (push content)
        overlay = new Region();
        overlay.setVisible(false);

        VBox centerContent = new VBox(6, calcDisplay, tabPane, memoryStatus);
        mainBorderPane.setCenter(centerContent);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        VBox.setVgrow(calcDisplay, Priority.SOMETIMES);
        centerContent.setPadding(new Insets(6));

        mainBorderPane.setLeft(rail);

        // Bottom status bar (DB connection + Preferences save state)
        dbStatusLabel = new Label();
        Region dbDot = new Region();
        dbDot.setPrefSize(10, 10);
        dbDot.getStyleClass().addAll("status-dot", "unknown");
        dbStatusLabel.setGraphic(dbDot);
        dbStatusLabel.setText("DB: Unknown");
        dbStatusLabel.getStyleClass().add("status-label");

        prefStatusLabel = new Label();
        Region prefDot = new Region();
        prefDot.setPrefSize(10, 10);
        prefDot.getStyleClass().addAll("status-dot", "off");
        prefStatusLabel.setGraphic(prefDot);
        prefStatusLabel.setText("Prefs: Ready");
        prefStatusLabel.getStyleClass().add("status-label");

        HBox statusBar = new HBox(12, dbStatusLabel, prefStatusLabel);
        statusBar.setPadding(new Insets(6, 10, 6, 10));
        statusBar.setStyle("-fx-alignment: CENTER_LEFT;");
        // Darken status bar slightly for better contrast
        applyGlassEffect(statusBar, 0.18);
        mainBorderPane.setBottom(statusBar);

        // Stack mainBorderPane and drawer so we can still place toast overlays over the entire app
        StackPane stack = new StackPane(mainBorderPane, overlay, drawer);
        drawer.setVisible(false);
        drawer.setTranslateX(0);

        root = stack;

        root.setCache(true);
        root.setCacheHint(javafx.scene.CacheHint.SPEED);

        Scene scene = new Scene(root, 450, 620);
        scene.setFill(Color.TRANSPARENT);

        var cssUrl = getClass().getResource("/styles/glass-calculator.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            log.warn("glass-calculator.css not found");
        }

        applyGlassEffect(root, 0.06);

        // Global keyboard support (Basic + Scientific). Currency tab uses its own fields.
        scene.setOnKeyPressed(event -> {
            if (drawerOpen && event.getCode() == KeyCode.ESCAPE) {
                toggleDrawer();
                return;
            }
            int selected = tabPane.getSelectionModel().getSelectedIndex();
            if (selected < 2) {
                handleCalculatorKey(event.getCode());
            } else if (selected == 2 && event.getCode() == KeyCode.ESCAPE) {
                amountField.clear();
            }
        });
        scene.setOnKeyTyped(event -> {
            if (tabPane.getSelectionModel().getSelectedIndex() < 2) {
                String ch = event.getCharacter();
                if (ch == null || ch.isEmpty()) return;
                switch (ch) {
                    case "%" -> handleInput("%");
                    case "." -> handleInput(".");
                    case "/", "÷" -> handleInput("/");
                    case "*", "x", "X" -> handleInput("×");
                    case "+" -> handleInput("+");
                    case "-" -> handleInput("−");
                    case "=" -> handleInput("=");
                    default -> {
                        // Ignore digits here - they are handled in keyPressed to avoid duplicate input
                    }
                }
            }
        });

        primaryStage.setTitle("Glass Calculator - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(420);
        primaryStage.setMinHeight(520);
        if (winW > 0 && winH > 0) {
            primaryStage.setX(winX);
            primaryStage.setY(winY);
            primaryStage.setWidth(winW);
            primaryStage.setHeight(winH);
        }
        primaryStage.setOnHiding(e -> saveWindowState(primaryStage));
        primaryStage.show();
    }

    /**
     * Cleanup on application stop (called by JavaFX on window close / exit).
     * Ensures DB connection and executor are properly shut down to prevent leaks.
     */
    @Override
    public void stop() {
        if (db != null) {
            db.shutdown();
        }
        fx.shutdown();
        saveHistoryToPrefs();
        saveLastTab();
        // Shutdown IO executor (allow brief time for async prefs flush on exit)
        try {
            ioExecutor.shutdown();
            if (!ioExecutor.awaitTermination(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ===================== BASIC PANE =====================
    private Pane createBasicPane() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        VBox.setVgrow(box, Priority.ALWAYS);

        // Memory operations row
        HBox memBar = createMemoryBar();

        // Keypad layout (4 columns) — backspace at left, operators in rightmost column
        GridPane grid = createCalcGrid(new String[][]{
                {"⌫", "AC", "%", "/"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"+/-", "0", ".", "="}
        }, true);

        // Allow the keypad to grow and buttons to scale nicely
        VBox.setVgrow(grid, Priority.ALWAYS);

        box.getChildren().addAll(memBar, grid);
        return box;
    }

    // ===================== SCIENTIFIC PANE =====================
    private Pane createScientificPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(6));
        VBox.setVgrow(box, Priority.ALWAYS);

        // Rad/Deg Toggle with visual indicator (replaces old mode handling)
        HBox modeToggle = new HBox(6);
        modeToggle.setAlignment(javafx.geometry.Pos.CENTER);

        ToggleButton radBtn = new ToggleButton("Rad");
        ToggleButton degBtn = new ToggleButton("Deg");
        radBtn.setSelected(radiansMode);
        degBtn.setSelected(!radiansMode);
        radBtn.setStyle("-fx-background-radius: 12; -fx-padding: 3 10;");
        degBtn.setStyle("-fx-background-radius: 12; -fx-padding: 3 10;");

        ToggleGroup modeGroup = new ToggleGroup();
        radBtn.setToggleGroup(modeGroup);
        degBtn.setToggleGroup(modeGroup);

        radBtn.setOnAction(e -> { radiansMode = true; radBtn.setSelected(true); saveLastTab(); });
        degBtn.setOnAction(e -> { radiansMode = false; degBtn.setSelected(true); saveLastTab(); });

        ToggleButton advToggle = new ToggleButton("Adv");
        advToggle.setStyle("-fx-background-radius: 12; -fx-padding: 3 10;");
        modeToggle.getChildren().addAll(new Label("Mode: "), radBtn, degBtn, advToggle);

        // Advanced mathematical functions (Trig, Hyperbolic, Logs, Roots, Powers)
        GridPane funcGrid = new GridPane();
        funcGrid.setHgap(4);
        funcGrid.setVgap(4);
        funcGrid.setPadding(new Insets(4));

        // Make all 6 columns equal width for perfect alignment
        for (int i = 0; i < 6; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 6);
            cc.setHgrow(Priority.ALWAYS);
            funcGrid.getColumnConstraints().add(cc);
        }

        // Give rows equal height
        for (int i = 0; i < 4; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            funcGrid.getRowConstraints().add(rc);
        }

        String[][] functions = {
            {"sin", "cos", "tan", "asin", "acos", "atan"},
            {"sinh", "cosh", "tanh", "asinh", "acosh", "atanh"},
            {"log", "ln", "log2", "√", "∛", "y√x"},
            {"π", "e", "x²", "x³", "2^x", "x!"}
        };

        for (int r = 0; r < functions.length; r++) {
            for (int c = 0; c < functions[r].length; c++) {
                String text = functions[r][c];
                Button btn = new Button(text);
                btn.setFont(getFont(16));
                btn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                btn.setMinHeight(46);
                btn.getStyleClass().addAll("glass-button", "sci-button");
                final String val = text;
                btn.setOnAction(e -> handleScientificFunction(val));

                btn.setCache(true);
                btn.setCacheHint(javafx.scene.CacheHint.SPEED);

                funcGrid.add(btn, c, r);
            }
        }

        // Expanded advanced set (revealed by Adv toggle)
        GridPane advGrid = new GridPane();
        advGrid.setHgap(4);
        advGrid.setVgap(4);
        advGrid.setPadding(new Insets(4));
        for (int i = 0; i < 6; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 6);
            cc.setHgrow(Priority.ALWAYS);
            advGrid.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < 1; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            advGrid.getRowConstraints().add(rc);
        }
        String[][] advFuncs = {{"asin", "acos", "atan", "logy", "y^x", "2^x"}};
        for (int r = 0; r < advFuncs.length; r++) {
            for (int c = 0; c < advFuncs[r].length; c++) {
                String text = advFuncs[r][c];
                Button btn = new Button(text);
                btn.setFont(getFont(14));
                btn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                btn.setMinHeight(36);
                btn.getStyleClass().addAll("glass-button", "sci-button");
                final String val = text;
                btn.setOnAction(e -> handleScientificFunction(val));
                btn.setCache(true);
                btn.setCacheHint(javafx.scene.CacheHint.SPEED);
                advGrid.add(btn, c, r);
            }
        }
        advGrid.visibleProperty().bind(advToggle.selectedProperty());
        advGrid.managedProperty().bind(advToggle.selectedProperty());
        applyGlassEffect(advGrid, 0.08);
        VBox.setVgrow(advGrid, Priority.SOMETIMES);

        // ⌫ integrated into top row of 5-col numGrid below (see createCalcGrid + generalized cols)
        // Standard numeric keypad (reused) - 5 cols only for top row, others 4
        GridPane numGrid = createCalcGrid(new String[][]{
                {"⌫", "AC", "%", "/"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"+/-", "0", ".", "="}
        }, false);

        HBox memBar = createMemoryBar();

        // Apply glass effect to the advanced function area
        applyGlassEffect(funcGrid, 0.08);

        // Make both function area and keypad grow well in full screen
        VBox.setVgrow(funcGrid, Priority.ALWAYS);
        VBox.setVgrow(numGrid, Priority.ALWAYS);

        Label funcLabel = new Label("Advanced Functions");
        funcLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        Label keypadLabel = new Label("Keypad");
        keypadLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");

        box.getChildren().addAll(
            modeToggle,
            funcLabel,
            funcGrid,
            advGrid,
            memBar,
            keypadLabel,
            numGrid
        );
        return box;
    }

    private GridPane createCalcGrid(String[][] layout, boolean basic) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(4));

        // Compute dynamic column count from widest row (supports 4 or 5 cols for ⌫ integration)
        int cols = 4;
        for (String[] row : layout) {
            if (row.length > cols) cols = row.length;
        }

        // Make columns and rows grow equally for responsive resizing (5 cols ~80-90px each still comfortable)
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        for (int r = 0; r < layout.length; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(rc);

            for (int c = 0; c < layout[r].length; c++) {
                String text = layout[r][c];
                if (text.isEmpty()) continue;

                Button btn = new Button(text);
                btn.setFont(getFont(18));
                btn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                btn.setMinHeight(48);
                btn.getStyleClass().addAll("glass-button", "calc-button");
                if ("AC".equals(text) || "+/-".equals(text) || "%".equals(text) || "⌫".equals(text)) {
                    btn.getStyleClass().add("ac");
                } else if ("=".equals(text)) {
                    btn.getStyleClass().add("equals");
                } else if ("/×−+^".contains(text)) {
                    btn.getStyleClass().add("operator");
                }
                final String val = text;
                if ("⌫".equals(text)) {
                    btn.setOnAction(e -> handleBackspace());
                } else {
                    btn.setOnAction(e -> handleInput(val));
                }

                btn.setCache(true);
                btn.setCacheHint(javafx.scene.CacheHint.SPEED);

                grid.add(btn, c, r);
            }
        }

        // Apply subtle glass effect to numeric keypads
        applyGlassEffect(grid, 0.06);
        return grid;
    }

    private Label makeCurrencyLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12px;");
        return l;
    }

    // ===================== CURRENCY PANE =====================
    private Pane createCurrencyPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        VBox.setVgrow(box, Priority.ALWAYS);
        applyGlassEffect(box, 0.09);   // Glass effect on currency converter panel

        amountField = new TextField("100");
        amountField.setFont(getFont(20));
        amountField.setStyle("-fx-background-color: #222; -fx-text-fill: white;");
        HBox.setHgrow(amountField, Priority.ALWAYS);

        fromBox = new ComboBox<>(FXCollections.observableArrayList(CURRENCIES));
        toBox = new ComboBox<>(FXCollections.observableArrayList(CURRENCIES));
        fromBox.setValue("USD");
        toBox.setValue("INR");
        fromBox.setPrefWidth(110);
        toBox.setPrefWidth(110);

        Button convertBtn = new Button("Convert");
        convertBtn.getStyleClass().addAll("glass-button", "convert-button");
        convertBtn.setOnAction(e -> performConversion());

        currencyResult = new Label("—");
        currencyResult.setFont(getFont(28));
        currencyResult.setStyle("-fx-text-fill: #0f0;");
        HBox.setHgrow(currencyResult, Priority.ALWAYS);

        currencyStatus = new Label("Rates via exchangerate-api.com");
        currencyStatus.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        HBox amountRow = new HBox(8, makeCurrencyLabel("Amount"), amountField);
        HBox.setHgrow(amountField, Priority.ALWAYS);
        amountRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox fromTo = new HBox(10, makeCurrencyLabel("From"), fromBox, makeCurrencyLabel("To"), toBox);
        HBox.setHgrow(fromBox, Priority.ALWAYS);
        HBox.setHgrow(toBox, Priority.ALWAYS);
        fromTo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox favs = new HBox(6);
        for (String cur : new String[]{"USD", "EUR", "INR", "GBP", "JPY"}) {
            Button b = new Button(cur);
            b.getStyleClass().addAll("glass-button", "fav-button");
            b.setOnAction(ev -> {
                fromBox.setValue(cur);
                performConversion();
            });
            favs.getChildren().add(b);
        }

        Label quickLabel = makeCurrencyLabel("Quick:");
        HBox favRow = new HBox(6, quickLabel, favs);
        favRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox resultRow = new HBox(8, makeCurrencyLabel("Result"), currencyResult);
        HBox.setHgrow(currencyResult, Priority.ALWAYS);
        resultRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        box.getChildren().addAll(
                amountRow,
                fromTo,
                convertBtn,
                resultRow,
                favRow,
                currencyStatus
        );
        return box;
    }

    private void performConversion() {
        try {
            double amount = Double.parseDouble(amountField.getText().trim());
            String from = fromBox.getValue();
            String to = toBox.getValue();

            double rate = fx.rateOrFetch(from, to,
                    () -> Platform.runLater(() -> showConversionResult(amount, from, to)),
                    () -> Platform.runLater(() -> {
                        currencyResult.setText("Rate unavailable");
                        currencyStatus.setText("Check internet / API key");
                    }));

            if (!Double.isNaN(rate) && rate > 0) {
                showConversionResult(amount, from, to);
            } else {
                currencyResult.setText("Fetching rate...");
                currencyStatus.setText("Please wait...");
            }

        } catch (NumberFormatException ex) {
            currencyResult.setText("Invalid amount");
        }
    }

    private void showConversionResult(double amount, String from, String to) {
        double rate = fx.rateOrFetch(from, to, null, null); // get from cache
        if (Double.isNaN(rate) || rate <= 0) {
            currencyResult.setText("Rate unavailable");
            return;
        }
        double result = amount * rate;
        currencyResult.setText(String.format("%.4f %s", result, to));
        currencyStatus.setText("1 " + from + " = " + String.format("%.6f", rate) + " " + to);
        addToHistory(amount + " " + from + " → " + String.format("%.4f", result) + " " + to);
    }

    // ===================== SHARED INPUT HANDLER =====================
    private void handleInput(String value) {
        switch (value) {
            case "AC":
                expression.setLength(0);
                calcDisplay.setText("0");
                resultShown = false;
                break;

            case "⌫":
                handleBackspace();
                break;

            case "=":
                if (expression.length() > 0) {
                    try {
                        String expr = expression.toString()
                                .replace("×", "*")
                                .replace("−", "-")
                                .replace("÷", "/");
                        double result = engine.eval(expr, radiansMode);
                        String formatted = CalculatorEngine.fmt(result);
                        updateDisplay(formatted);
                        addToHistory(expression + " = " + formatted);
                        expression = new StringBuilder(formatted);
                        resultShown = true;
                     } catch (Exception ex) {
                        log.debug("Eval error", ex);
                        updateDisplay("Error");
                        expression.setLength(0);
                        resultShown = true;
                    }
                }
                break;

            case "+/-":
                if (expression.length() > 0) {
                    if (expression.charAt(0) == '-') {
                        expression.deleteCharAt(0);
                    } else {
                        expression.insert(0, '-');
                    }
                    calcDisplay.setText(expression.toString());
                }
                break;

            case "%":
                if (expression.length() > 0) {
                     try {
                        double v = Double.parseDouble(expression.toString());
                        String res = CalculatorEngine.fmt(v / 100);
                        calcDisplay.setText(res);
                        expression = new StringBuilder(res);
                    } catch (Exception ex) {
                        log.debug("Percent parse error", ex);
                    }
                }
                break;

            case "sin": case "cos": case "tan":
            case "asin": case "acos": case "atan":
            case "sinh": case "cosh": case "tanh":
            case "log": case "ln": case "log2":
            case "sqrt": case "cbrt":
                expression.append(value).append("(");
                calcDisplay.setText(expression.toString());
                break;

            case "pi":
                expression.append("pi");
                calcDisplay.setText(expression.toString());
                break;

            case "e":
                expression.append("e");
                calcDisplay.setText(expression.toString());
                break;

            case "^":
                expression.append("^");
                calcDisplay.setText(expression.toString());
                break;

            default:
                if (resultShown) {
                    expression.setLength(0);
                    resultShown = false;
                }
                expression.append(value);
                calcDisplay.setText(expression.toString());
                break;
        }
    }

    private void handleCalculatorKey(KeyCode code) {
        // Handle non-character keys here. Operators (like + - * /) are handled in keyTyped to avoid duplicate events
        if (code.isDigitKey()) handleInput(code.getName());
        else if (code == KeyCode.ENTER || code == KeyCode.EQUALS) handleInput("=");
        else if (code == KeyCode.PERIOD || code == KeyCode.DECIMAL) handleInput(".");
        else if (code == KeyCode.ESCAPE) handleInput("AC");
        else if (code == KeyCode.BACK_SPACE) handleBackspace();
    }

    // Handles advanced scientific function buttons (trig, hyperbolic, logs, roots, etc.)
    private void handleScientificFunction(String val) {
        switch (val) {
            case "π":
                expression.append("pi");
                calcDisplay.setText(expression.toString());
                break;
            case "x²":
                expression.append("^2");
                calcDisplay.setText(expression.toString());
                break;
            case "x³":
                expression.append("^3");
                calcDisplay.setText(expression.toString());
                break;
            case "2^x":
                expression.append("2^");
                calcDisplay.setText(expression.toString());
                break;
            case "y^x":
                expression.append("^(");
                calcDisplay.setText(expression.toString());
                break;
            case "logy":
                expression.append("log(");
                calcDisplay.setText(expression.toString());
                break;
            case "1/x":
                expression.append("1/(");
                calcDisplay.setText(expression.toString());
                break;
            case "√":
                expression.append("sqrt(");
                calcDisplay.setText(expression.toString());
                break;
            case "∛":
                expression.append("cbrt(");
                calcDisplay.setText(expression.toString());
                break;
            case "y√x":
                // Better UX: insert x^(1/y) template
                if (resultShown || expression.length() == 0) {
                    expression.append("^(1/");
                } else {
                    expression.append("^(1/");
                }
                calcDisplay.setText(expression.toString());
                break;
            case "x!":
                // Compute factorial of current value (integers only)
                try {
                    double v = Double.parseDouble(calcDisplay.getText());
                    if (v < 0 || v != Math.floor(v)) {
                        updateDisplay("Error");
                        expression.setLength(0);
                        resultShown = true;
                        return;
                    }
                    long result = factorial((long) v);
                    String fmt = CalculatorEngine.fmt(result);
                    calcDisplay.setText(fmt);
                    expression = new StringBuilder(fmt);
                    resultShown = true;
                } catch (Exception ex) {
                    log.debug("Factorial error", ex);
                    calcDisplay.setText("Error");
                    expression.setLength(0);
                    resultShown = true;
                }
                break;
            default:
                // trig, hyperbolic, log, ln, log2, etc.
                expression.append(val);
                if (!val.equals("(") && !val.equals(")") && !val.equals("^")) {
                    expression.append("(");
                }
                calcDisplay.setText(expression.toString());
                break;
        }
    }

    // Simple factorial (supports up to 20 safely)
    private long factorial(long n) {
        if (n < 0) throw new IllegalArgumentException("Negative factorial");
        if (n == 0 || n == 1) return 1;
        long res = 1;
        for (long i = 2; i <= n; i++) {
            res *= i;
            if (res < 0) throw new ArithmeticException("Factorial overflow");
        }
        return res;
    }

    private HBox createMemoryBar() {
        HBox bar = new HBox(6);
        bar.setPadding(new Insets(6, 10, 6, 10));

        String[] ops = {"MC", "MR", "M-", "M+"};
        for (String op : ops) {
            Button btn = new Button(op);
            btn.setFont(getFont(11));
            btn.setPrefSize(48, 26);
            btn.getStyleClass().addAll("glass-button", "mem-button");
            btn.setOnAction(e -> handleMemory(op));

            btn.setCache(true);
            btn.setCacheHint(javafx.scene.CacheHint.SPEED);

            bar.getChildren().add(btn);
        }

        // Apply advanced glass effect to the memory bar container
        applyGlassEffect(bar, 0.12);
        return bar;
    }

    private void handleMemory(String op) {
        String displayText = calcDisplay.getText();
        if ("Error".equals(displayText)) {
            if ("MC".equals(op)) {
                memory = 0;
                updateMemoryStatus();
            }
            return; // ignore M+/M-/MR on error
        }

        double current = 0;
        try {
            current = Double.parseDouble(displayText);
        } catch (Exception ex) {
            log.debug("Memory parse error on display", ex);
        }

        switch (op) {
            case "MC":
                memory = 0;
                break;
            case "MR":
                expression = new StringBuilder(CalculatorEngine.fmt(memory));
                calcDisplay.setText(CalculatorEngine.fmt(memory));
                resultShown = true;
                showToast("Memory recalled");
                break;
            case "M+":
                memory += current;
                break;
            case "M-":
                memory -= current;
                break;
        }
        updateMemoryStatus();
    }

    private void updateMemoryStatus() {
        if (memoryStatus != null) {
            String text = (memory == 0) ? "💾 M: 0" : "💾 M: " + CalculatorEngine.fmt(memory);
            memoryStatus.setText(text);

            // Subtle pop animation when memory changes
            animateScale(memoryStatus, 0.85, 1.0, 200);
        }
        // Update DB status indicator in bottom bar
        updateDbStatus();
    }

    private void updateDbStatus() {
        if (dbStatusLabel == null) return;
        // Ensure graphic dot exists
        javafx.scene.Node g = dbStatusLabel.getGraphic();
        Region dot;
        if (g instanceof Region) {
            dot = (Region) g;
        } else {
            dot = new Region();
            dot.setPrefSize(10, 10);
            dot.getStyleClass().add("status-dot");
            dbStatusLabel.setGraphic(dot);
        }

        if (db != null) {
            if (db.isAvailable()) {
                dbStatusLabel.setText("DB: Connected");
                dot.getStyleClass().removeAll("off", "warn", "unknown");
                if (!dot.getStyleClass().contains("ok")) dot.getStyleClass().add("ok");
            } else if (db.isDriverLoaded()) {
                dbStatusLabel.setText("DB: Driver");
                dot.getStyleClass().removeAll("ok", "off", "unknown");
                if (!dot.getStyleClass().contains("warn")) dot.getStyleClass().add("warn");
            } else {
                dbStatusLabel.setText("DB: Unavailable");
                dot.getStyleClass().removeAll("ok", "warn", "unknown");
                if (!dot.getStyleClass().contains("off")) dot.getStyleClass().add("off");
            }
        } else {
            dbStatusLabel.setText("DB: Unknown");
            dot.getStyleClass().removeAll("ok", "warn", "off");
            if (!dot.getStyleClass().contains("unknown")) dot.getStyleClass().add("unknown");
        }
    }

    private void showPrefSaving() {
        if (prefStatusLabel == null) return;
        javafx.scene.Node g = prefStatusLabel.getGraphic();
        Region dot;
        if (g instanceof Region) {
            dot = (Region) g;
        } else {
            dot = new Region();
            dot.setPrefSize(10, 10);
            dot.getStyleClass().add("status-dot");
            prefStatusLabel.setGraphic(dot);
        }
        prefStatusLabel.setText("Prefs: Saving...");
        dot.getStyleClass().removeAll("ok", "off", "unknown");
        if (!dot.getStyleClass().contains("warn")) dot.getStyleClass().add("warn");
    }

    private void indicatePrefSaved(boolean success) {
        if (prefStatusLabel == null) return;
        javafx.scene.Node g = prefStatusLabel.getGraphic();
        Region dot;
        if (g instanceof Region) {
            dot = (Region) g;
        } else {
            dot = new Region();
            dot.setPrefSize(10, 10);
            dot.getStyleClass().add("status-dot");
            prefStatusLabel.setGraphic(dot);
        }
        if (success) {
            prefStatusLabel.setText("Prefs: Saved");
            dot.getStyleClass().removeAll("warn", "off", "unknown");
            if (!dot.getStyleClass().contains("ok")) dot.getStyleClass().add("ok");
            // Clear to Ready after 1.6s
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1600));
            pt.setOnFinished(e -> prefStatusLabel.setText("Prefs: Ready"));
            pt.play();
        } else {
            prefStatusLabel.setText("Prefs: Failed");
            dot.getStyleClass().removeAll("ok", "warn", "unknown");
            if (!dot.getStyleClass().contains("off")) dot.getStyleClass().add("off");
        }
    }

    private void handleBackspace() {
        if (expression.length() > 0) {
            expression.setLength(expression.length() - 1);
            calcDisplay.setText(expression.length() == 0 ? "0" : expression.toString());
        }
    }

    private void addToHistory(String entry) {
        if (entry == null || entry.isEmpty()) return;

        // Fast membership check using historySet to avoid O(n) list scans
        boolean added = historySet.add(entry);
        if (added) {
            // Update UI on FX thread
            Platform.runLater(() -> {
                historyList.getItems().add(0, entry);
                // Cap at 25 items; remove tail entries and keep set in sync
                while (historyList.getItems().size() > 25) {
                    String removed = historyList.getItems().remove(historyList.getItems().size() - 1);
                    historySet.remove(removed);
                }
            });
            // Persist prefs asynchronously
            ioExecutor.submit(() -> saveHistoryToPrefsInternal());
        }

        // Also persist to Database (if configured) asynchronously
        if (db != null) {
            String type = entry.contains("→") ? "conv" : "calc";
            String expr = entry;
            String res = "";
            if (entry.contains(" = ")) {
                String[] p = entry.split(" = ", 2);
                expr = p[0];
                res = p.length > 1 ? p[1] : "";
            } else if (entry.contains(" → ")) {
                String[] p = entry.split(" → ", 2);
                expr = p[0];
                res = p.length > 1 ? p[1] : "";
            }
            db.saveAsync(expr, res, type);
        }
    }

    // No longer needed — DBManager is fully decoupled from UI

    // ========== PERSISTENT HISTORY - both Database + Preferences (like original design) ==========
    private void loadHistoryFromBoth() {
        // Clear set for fresh load (list cleared implicitly on start)
        historySet.clear();
        historyList.getItems().clear();

        // 1. Load from Preferences first (fast, local)
        try {
            java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(GlassCalculator.class);
            int n = p.getInt("fxHistN", 0);
            for (int i = 0; i < n; i++) {
                String e = p.get("fxHist_" + i, null);
                if (e != null && historySet.add(e)) {
                    historyList.getItems().add(e);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load history from preferences", ex);
        }

        // 2. Also load from Database asynchronously (if configured)
        if (db != null) {
            // Load calculations
            db.loadCalcHistoryAsync(dbList -> Platform.runLater(() -> {
                for (String item : dbList) {
                    if (historySet.add(item)) {
                        historyList.getItems().add(0, item);   // put older DB items at top if new
                    }
                }
                while (historyList.getItems().size() > 25) {
                    String removed = historyList.getItems().remove(25);
                    historySet.remove(removed);
                }
                updateMemoryStatus(); // refresh DB indicator
            }));

            // Load conversions
            db.loadConvHistoryAsync(dbList -> Platform.runLater(() -> {
                for (String item : dbList) {
                    if (historySet.add(item)) {
                        historyList.getItems().add(0, item);
                    }
                }
                while (historyList.getItems().size() > 25) {
                    String removed = historyList.getItems().remove(25);
                    historySet.remove(removed);
                }
                updateMemoryStatus(); // refresh DB indicator
            }));
        }
    }

    private void saveHistoryToPrefs() {
        // Backwards compatible: schedule async write
        ioExecutor.submit(() -> saveHistoryToPrefsInternal());
    }

    // Internal method to perform prefs write; runs on IO thread
    private void saveHistoryToPrefsInternal() {
        if (historyList == null) return;
        Platform.runLater(this::showPrefSaving);
        boolean success = false;
        try {
            java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(GlassCalculator.class);
            // Snapshot items to avoid concurrent modification
            java.util.List<String> snapshot = new ArrayList<>();
            snapshot.addAll(historyList.getItems());
            int n = Math.min(snapshot.size(), 25);
            p.putInt("fxHistN", n);
            for (int i = 0; i < n; i++) {
                p.put("fxHist_" + i, snapshot.get(i));
            }
            p.flush();
            success = true;
        } catch (Exception ex) {
            log.warn("Failed to save history to preferences", ex);
            success = false;
        } finally {
            boolean s = success;
            Platform.runLater(() -> indicatePrefSaved(s));
        }
    }

    private void clearAllHistory() {
        historyList.getItems().clear();
        historySet.clear();
        saveHistoryToPrefs();
        if (db != null) {
            db.clearHistory();
        }
    }

    private void restorePreferencesState() {
        try {
            java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(GlassCalculator.class);
            radiansMode = p.getBoolean("fxRadians", true);
            lastTabIndex = p.getInt("fxLastTab", 0);
            winX = p.getDouble("fxWinX", -1);
            winY = p.getDouble("fxWinY", -1);
            winW = p.getDouble("fxWinW", -1);
            winH = p.getDouble("fxWinH", -1);
        } catch (Exception ex) {
            log.debug("Prefs restore failed", ex);
        }
    }

    private void saveWindowState(Stage stage) {
        Platform.runLater(this::showPrefSaving);
        boolean success = false;
        try {
            java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(GlassCalculator.class);
            p.putDouble("fxWinX", stage.getX());
            p.putDouble("fxWinY", stage.getY());
            p.putDouble("fxWinW", stage.getWidth());
            p.putDouble("fxWinH", stage.getHeight());
            p.flush();
            success = true;
        } catch (Exception ex) {
            log.debug("Window save failed", ex);
        } finally {
            boolean s = success;
            Platform.runLater(() -> indicatePrefSaved(s));
        }
    }

    private void saveLastTab() {
        Platform.runLater(this::showPrefSaving);
        boolean success = false;
        try {
            java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(GlassCalculator.class);
            p.putInt("fxLastTab", lastTabIndex);
            p.putBoolean("fxRadians", radiansMode);
            p.flush();
            success = true;
        } catch (Exception ex) {
            log.debug("Tab/rad save failed", ex);
        } finally {
            boolean s = success;
            Platform.runLater(() -> indicatePrefSaved(s));
        }
    }

    private void restoreLastTab() {
        if (tabPane != null && lastTabIndex >= 0 && lastTabIndex < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(lastTabIndex);
        }
    }

    private void showSettingsDialog() {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Glass Calculator Settings");
        d.setHeaderText("Database & Currency API Configuration");
        d.setOnShown(ev -> {
            try {
                Stage s = (Stage) d.getDialogPane().getScene().getWindow();
                if (appIconImage != null) s.getIcons().add(appIconImage);
            } catch (Exception ex) {
                // ignore
            }
        });

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("sqlite", "mysql", "postgresql"));
        typeBox.setValue(AppConfig.getDatabaseType());
        TextField hostF = new TextField(AppConfig.getDatabaseHost());
        TextField portF = new TextField(String.valueOf(AppConfig.getDatabasePort()));
        TextField userF = new TextField(AppConfig.getDatabaseUser());
        PasswordField passF = new PasswordField();
        passF.setText(AppConfig.getDatabasePassword());
        TextField nameF = new TextField(AppConfig.getDatabaseName());
        TextField apiF = new TextField(AppConfig.getApiKey());
        apiF.setPromptText("exchangerate-api.com key (optional for some)");

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        int r = 0;
        g.add(new Label("DB Type"), 0, r); g.add(typeBox, 1, r++);
        g.add(new Label("Host"), 0, r); g.add(hostF, 1, r++);
        g.add(new Label("Port"), 0, r); g.add(portF, 1, r++);
        g.add(new Label("User"), 0, r); g.add(userF, 1, r++);
        g.add(new Label("Password"), 0, r); g.add(passF, 1, r++);
        g.add(new Label("DB Name"), 0, r); g.add(nameF, 1, r++);
        g.add(new Label("API Key"), 0, r); g.add(apiF, 1, r++);

        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        d.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    int p = Integer.parseInt(portF.getText().trim());
                    AppConfig.setDatabaseConfig(typeBox.getValue(), hostF.getText().trim(), p,
                            userF.getText().trim(), passF.getText(), nameF.getText().trim());
                    AppConfig.setApiKey(apiF.getText().trim());
                    AppConfig.markSetupComplete();
                } catch (Exception ex) {
                    log.warn("Settings save error", ex);
                }
            }
            return null;
        });

        d.showAndWait();
    }

    public static void main(String[] args) {
        // Force hardware GPU acceleration for maximum animation performance
        System.setProperty("prism.order", "es2,sw"); // Prefer OpenGL/ES2 (GPU) over software
        System.setProperty("prism.vsync", "false"); // Disable vsync for lower latency animations (may cause tearing)

        // Global uncaught exception handler with nice error dialog (Phase 1.4)
        // Also dump to stderr so cause is visible even if dialog fails (e.g. macOS NPE in show)
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception in thread: {}", thread.getName(), throwable);
            System.err.println("=== Uncaught Exception ===");
            throwable.printStackTrace(System.err);
            Platform.runLater(() -> showGlobalErrorDialog(throwable));
        });

        launch(args);
    }

    /**
     * Global error dialog for uncaught exceptions. Provides user-friendly message + expandable stack trace.
     */
    private static void showGlobalErrorDialog(Throwable t) {
        try {
            if (primaryStageRef == null || !primaryStageRef.isShowing()) {
                // Fallback for early errors or before stage ready (avoids macOS Glass NPE in nested showAndWait)
                System.err.println("Uncaught (dialog skipped, no primary stage): " + t);
                t.printStackTrace(System.err);
                return;
            }

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Glass Calculator - Error");
            alert.setHeaderText("An unexpected error occurred");
            String msg = (t.getMessage() != null && !t.getMessage().isEmpty())
                    ? t.getMessage() : t.getClass().getSimpleName();
            alert.setContentText("The application encountered a problem and may be unstable.\n\n" + msg);

            if (primaryStageRef != null) {
                alert.initOwner(primaryStageRef);
            }

            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            TextArea textArea = new TextArea(sw.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(300);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(new Label("Technical Details (for support):"), 0, 0);
            expContent.add(textArea, 0, 1);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            alert.getDialogPane().setExpandableContent(expContent);
            alert.getDialogPane().setExpanded(true); // show details by default for devs

            // Use show() not showAndWait() to avoid macOS nested EventLoop + platformWindow NPE
            alert.show();
        } catch (Throwable fallback) {
            log.error("Failed to display error dialog", fallback);
            System.err.println("Uncaught (dialog also failed): " + t);
            t.printStackTrace(System.err);
        }
    }

    /**
     * Updates the calculator display (text updates are instant; scale animation disabled per UX request).
     */
    private void updateDisplay(String newText) {
        calcDisplay.setDisplayText(newText);
    }

    /**
     * Initialize font cache to avoid repeated Font.font() calls
     * This is a performance optimization for JavaFX font instantiation
     */
    private void initFontCache() {
        cachedFont12 = Font.font(12);
        cachedFont14 = Font.font(14);
        cachedFont16 = Font.font(16);
        cachedFont18 = Font.font(18);
        cachedFont20 = Font.font(20);
    }

    /**
     * Get cached font, avoids expensive Font.font() instantiation
     */
    private Font getFont(double size) {
        if (size == 12) return cachedFont12;
        if (size == 14) return cachedFont14;
        if (size == 16) return cachedFont16;
        if (size == 18) return cachedFont18;
        if (size == 20) return cachedFont20;
        return Font.font(size);  // Fallback for other sizes
    }

    /**
     * Applies pronounced layered glassmorphism with multiple stacked planes,
     * inner highlights, and specular effects for deep dimensionality.
     * Uses caching to avoid repeated string allocations for common opacity values.
     */
    private void applyGlassEffect(javafx.scene.Node node, double baseOpacity) {
        // Check cache first to avoid recomputing style string
        String cachedStyle = glassEffectCache.get(baseOpacity);
        
        if (cachedStyle == null) {
            // iOS 26 'liquid glass' style: softer gradients, larger radii, subtle glow
            cachedStyle = 
                "-fx-background-color: linear-gradient(to bottom, rgba(255,255,255," + (baseOpacity + 0.10) + ") 0%, rgba(255,255,255," + (baseOpacity * 0.5) + ") 30%, rgba(25,28,35," + (baseOpacity * 0.85) + ") 100%);" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: rgba(255,255,255,0.16);" +
                "-fx-border-width: 0.6;" +
                "-fx-border-radius: 20;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.32), 22, 0.36, 0, 6), " +
                            "innershadow(gaussian, rgba(255,255,255,0.10), 14, 0.7, 0, 3);";
            
            // Cache for future use (limit cache size to avoid memory bloat)
            if (glassEffectCache.size() < 20) {
                glassEffectCache.put(baseOpacity, cachedStyle);
            }
        }

        node.setStyle(cachedStyle);

        // Keep GPU caching for smooth rendering
        node.setCache(true);
        node.setCacheHint(javafx.scene.CacheHint.SPEED);
    }

    // ==================== Animation Helpers (Improved Overall Feel) ====================

    private static void animateScale(javafx.scene.Node node, double from, double to, int durationMs) {
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(durationMs), node);
        st.setFromX(from);
        st.setFromY(from);
        st.setToX(to);
        st.setToY(to);
        st.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        st.play();
    }

    private static void animateFade(javafx.scene.Node node, double from, double to, int durationMs) {
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(durationMs), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        ft.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        ft.play();
    }

    // Toggle the left drawer (slide in/out) with overlay fade
    private void toggleDrawer() {
        if (mainBorderPane == null || rail == null || drawer == null) return;
        if (!drawerOpen) {
            // Open drawer by replacing the left rail with the drawer (push content)
            mainBorderPane.setLeft(drawer);
            drawer.setVisible(true);
            drawerOpen = true;
        } else {
            // Close drawer and restore the rail
            mainBorderPane.setLeft(rail);
            drawer.setVisible(false);
            drawerOpen = false;
        }
    }

    // ==================== Advanced JavaFX UI Helpers (replacing old Swing components) ====================

    // Toast queue for better UX (replaces simple old Toast)
    private final java.util.Queue<javafx.scene.Node> toastQueue = new java.util.LinkedList<>();

    /**
     * Advanced Toast with types (SUCCESS, ERROR, INFO) and queue support
     */
    private void showToast(String message, ToastType type) {
        String bgColor;
        switch (type) {
            case SUCCESS -> bgColor = "#2e7d32";
            case ERROR   -> bgColor = "#c62828";
            default      -> bgColor = "#424242";
        }

        Label toastLabel = new Label(message);
        toastLabel.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-text-fill: white;" +
            "-fx-padding: 10 18;" +
            "-fx-background-radius: 999;" +
            "-fx-font-size: 13px;"
        );

        StackPane toastContainer = new StackPane(toastLabel);
        toastContainer.setPadding(new Insets(8, 0, 8, 0));

        root.getChildren().add(toastContainer);
        StackPane.setAlignment(toastContainer, javafx.geometry.Pos.BOTTOM_CENTER);

        // Lightweight entrance (less heavy for full screen)
        toastContainer.setScaleX(0.75);
        toastContainer.setScaleY(0.75);
        toastContainer.setOpacity(0.0);

        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), toastContainer);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        javafx.animation.ScaleTransition scaleIn = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(140), toastContainer);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        scaleIn.play();

        // Auto dismiss with fade out
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.seconds(2.6), toastContainer);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setDelay(javafx.util.Duration.seconds(1.8));
        fade.setOnFinished(e -> {
            root.getChildren().remove(toastContainer);
            toastQueue.poll();
        });
        fade.play();
    }

    // Overload for simple info toasts
    private void showToast(String message) {
        showToast(message, ToastType.INFO);
    }

    enum ToastType { SUCCESS, ERROR, INFO }

    /**
     * Custom ScalingDisplay Region implementing advanced glass look for the main calculator output.
     * (Scale pop animation on text change has been disabled for cleaner/snappier UX.)
     */
    private static class ScalingDisplay extends StackPane {
        private final Label valueLabel = new Label("0");

        public ScalingDisplay() {
            valueLabel.setFont(Font.font(34));
            valueLabel.setTextFill(Color.WHITE);
            valueLabel.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            // Layered glass background with inner highlight
            this.setStyle(
                "-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0.03) 30%, rgba(18,20,30,0.72) 100%);" +
                "-fx-background-radius: 18;" +
                "-fx-border-color: rgba(255,255,255,0.25);" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 18;" +
                "-fx-padding: 12 20;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 20, 0.4, 0, 8), " +
                            "innershadow(gaussian, rgba(255,255,255,0.18), 10, 0.7, 0, 2);"
            );

            this.getChildren().add(valueLabel);
            this.setMinHeight(72);
            StackPane.setAlignment(valueLabel, javafx.geometry.Pos.CENTER_RIGHT);

            // Maximize GPU usage for smooth display animations
            this.setCache(true);
            this.setCacheHint(javafx.scene.CacheHint.SPEED);
        }

        public void setDisplayText(String text) {
            valueLabel.setText(text);
        }

        // Backward compatible setText for existing code
        public void setText(String text) {
            setDisplayText(text);
        }

        public String getText() {
            return valueLabel.getText();
        }
    }

    /**
     * Opens history in a separate dialog (moved from main layout to menu)
     */
    private void showHistoryDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Calculation History");
        dialog.setHeaderText("Recent Calculations & Conversions");
        dialog.setResizable(true);
        dialog.setOnShown(ev -> {
            try {
                Stage s = (Stage) dialog.getDialogPane().getScene().getWindow();
                if (appIconImage != null) s.getIcons().add(appIconImage);
            } catch (Exception ex) {
                // ignore
            }
        });

        TextField filterField = new TextField();
        filterField.setPromptText("Filter...");

        FilteredList<String> filtered = new FilteredList<>(historyList.getItems(), s -> true);
        filterField.textProperty().addListener((o, oldV, newV) -> {
            String term = newV == null ? "" : newV.toLowerCase();
            filtered.setPredicate(s -> term.isEmpty() || s.toLowerCase().contains(term));
        });

        ListView<String> historyView = new ListView<>(filtered);
        historyView.setPrefWidth(480);
        historyView.setPrefHeight(420);

        Button clearBtn = new Button("Clear All");
        clearBtn.getStyleClass().addAll("glass-button", "dialog-button");
        clearBtn.setOnAction(e -> {
            clearAllHistory();
            dialog.setResult(null);
            dialog.close();
        });

        HBox bar = new HBox(8, filterField, clearBtn);
        HBox.setHgrow(filterField, Priority.ALWAYS);
        VBox content = new VBox(8, bar, historyView);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

}
