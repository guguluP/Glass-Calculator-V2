package Project.service;

import Project.model.CalculationHistoryEntry;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Manages calculation/conversion history persistence (Preferences + DB) and UI ListView.
 * Extracted from GlassCalculator to reduce the monolithic main class.
 * Uses CalculationHistoryEntry internally where possible.
 */
public class HistoryManager {

    private static final Logger log = LoggerFactory.getLogger(HistoryManager.class);
    private static final int MAX_HISTORY = 25;

    private final ListView<String> historyList;
    private final Set<String> historySet;
    private final DBManager db;
    private final ExecutorService ioExecutor;

    public HistoryManager(ListView<String> historyList, Set<String> historySet, DBManager db, ExecutorService ioExecutor) {
        this.historyList = historyList;
        this.historySet = historySet;
        this.db = db;
        this.ioExecutor = ioExecutor;
    }

    public void addToHistory(String entry) {
        if (entry == null || entry.isEmpty()) return;

        boolean added = historySet.add(entry);
        if (added) {
            Platform.runLater(() -> {
                historyList.getItems().add(0, entry);
                while (historyList.getItems().size() > MAX_HISTORY) {
                    String removed = historyList.getItems().remove(historyList.getItems().size() - 1);
                    historySet.remove(removed);
                }
            });
            ioExecutor.submit(this::saveHistoryToPrefsInternal);
        }

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

    public void loadHistoryFromBoth() {
        historySet.clear();
        historyList.getItems().clear();

        // Preferences first
        try {
            Preferences p = Preferences.userNodeForPackage(Project.GlassCalculator.class); // keep same node for compat
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

        // DB async
        if (db != null) {
            db.loadCalcHistoryAsync(dbList -> Platform.runLater(() -> mergeDbHistory(dbList)));
            db.loadConvHistoryAsync(dbList -> Platform.runLater(() -> mergeDbHistory(dbList)));
        }
    }

    private void mergeDbHistory(List<String> dbList) {
        for (String item : dbList) {
            if (historySet.add(item)) {
                historyList.getItems().add(0, item);
            }
        }
        while (historyList.getItems().size() > MAX_HISTORY) {
            String removed = historyList.getItems().remove(MAX_HISTORY);
            historySet.remove(removed);
        }
    }

    public void saveHistoryToPrefs() {
        ioExecutor.submit(this::saveHistoryToPrefsInternal);
    }

    private void saveHistoryToPrefsInternal() {
        if (historyList == null) return;
        // Note: showPrefSaving / indicatePrefSaved left in Glass for UI coupling
        boolean success = false;
        try {
            Preferences p = Preferences.userNodeForPackage(Project.GlassCalculator.class);
            List<String> snapshot = new ArrayList<>(historyList.getItems());
            int n = Math.min(snapshot.size(), MAX_HISTORY);
            p.putInt("fxHistN", n);
            for (int i = 0; i < n; i++) {
                p.put("fxHist_" + i, snapshot.get(i));
            }
            p.flush();
            success = true;
        } catch (Exception ex) {
            log.warn("Failed to save history to preferences", ex);
        }
        // UI feedback is handled by caller in Glass for now
    }

    public void clearAllHistory() {
        historyList.getItems().clear();
        historySet.clear();
        saveHistoryToPrefs();
        if (db != null) {
            db.clearHistory();
        }
    }

    // Simple accessor if needed by UI
    public List<String> getCurrentHistory() {
        return new ArrayList<>(historyList.getItems());
    }
}
