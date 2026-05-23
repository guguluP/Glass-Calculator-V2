package Project.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import Project.config.AppConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Currency exchange service with secure API key management.
 * Fetches real-time exchange rates from exchangerate-api.com.
 * Uses AppConfig for secure API key storage.
 * Features: dynamic error recovery (invalid keys disable at runtime), env-var security bypass,
 * refreshRates API, and stale cache fallback for better UX.
 *
 * @author GlassCalculator Team
 * @version 1.0
 */
public final class CurrencyService {
    private static final long CACHE_TTL_MS = 300_000L; // 5 minutes
    private static final int TIMEOUT_MS = 8_000;

    private record CachedRates(Map<String, Double> rates,
                                String updatedUtc,
                                long fetchedAt) {
        /**
         * Stale entries are still returned as fallback; only controls auto-refresh timing.
         */
        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS;
        }
    }

    private final Map<String, CachedRates> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> previousRates = new ConcurrentHashMap<>();
    private final AtomicBoolean fetching = new AtomicBoolean(false);
    private final ScheduledExecutorService debouncer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Currency-Debounce");
                t.setDaemon(true);
                return t;
            });
    private final AtomicReference<ScheduledFuture<?>> pendingRef =
            new AtomicReference<>();

    private volatile boolean apiConfigured = false;
    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    // Jackson mapper (reused for performance, thread-safe)
    private final ObjectMapper mapper = new ObjectMapper();

    public CurrencyService() {
        this.apiConfigured = AppConfig.isApiKeyConfigured();
        if (apiConfigured && System.getenv("GLASS_CALCULATOR_API_KEY") != null) {
            log.info("Using API key from environment variable (prefs ignored)");
        }
        if (!apiConfigured) {
            log.info("Currency API not configured. Users can add it later.");
        }
    }

    /**
     * Returns cached rate immediately (stale data returned as fallback if expired).
     * Fires non-blocking background fetch if missing or expired; callbacks on FX thread.
     * Re-enables service if config now present (one-way, after runtime disable).
     */
    public double rateOrFetch(String from, String to,
                        Runnable onResult, Runnable onError) {
        if (!apiConfigured && AppConfig.isApiKeyConfigured()) {
            apiConfigured = true;
        }
        // If API not configured, return NaN
        if (!apiConfigured) {
            log.warn("Currency API key not configured");
            Platform.runLater(onError);
            return Double.NaN;
        }

        CachedRates cr = cache.get(from);
        boolean hasEntry = cr != null;
        if (hasEntry) {
            if (cr.isExpired()) {
                // return stale immediately as fallback, refresh in bg
                startBackgroundFetch(from, onResult, onError);
            }
            return cr.rates.getOrDefault(to, Double.NaN);
        }
        // truly empty cache → classic behavior (NaN + start fetch)
        startBackgroundFetch(from, onResult, onError);
        return Double.NaN; // signal: not cached yet
    }

    /**
     * Starts background fetch (if not already fetching) for given base.
     * Used by rateOrFetch and refreshRates to avoid duplication.
     * Updates previousRates for deltas on success.
     */
    private void startBackgroundFetch(String base, Runnable onRes, Runnable onErr) {
        if (fetching.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    CachedRates newRates = fetchRates(base);
                    CachedRates oldRates = cache.get(base);
                    if (oldRates != null) {
                        previousRates.put(base, new HashMap<>(oldRates.rates()));
                    }
                    cache.put(base, newRates);
                    Platform.runLater(onRes);
                } catch (Exception e) {
                    log.error("Failed to fetch rates: {}", e.getMessage(), e);
                    Platform.runLater(onErr);
                } finally {
                    fetching.set(false);
                }
            }).start();
        }
    }

    /**
     * Force a background refresh for the given base currency, ignoring TTL.
     * Callers (e.g. future refresh button) receive onResult / onError on FX thread.
     * No change to GlassCalculator yet (additive API).
     */
    public void refreshRates(String base, Runnable onResult, Runnable onError) {
        if (!apiConfigured) {
            Platform.runLater(onError);
            return;
        }
        // no remove needed; start will snapshot current (for delta) and overwrite on success
        startBackgroundFetch(base, onResult, onError);
    }

    /**
     * Debounce: callback fires 350 ms after last call.
     */
    public void debounce(Runnable callback) {
        ScheduledFuture<?> previous = pendingRef.getAndSet(
                debouncer.schedule(() -> Platform.runLater(callback), 350, TimeUnit.MILLISECONDS)
        );
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public String lastUpdated(String base) {
        CachedRates cr = cache.get(base);
        return cr != null ? cr.updatedUtc() : "—";
    }

    /**
     * Returns the rate change direction: "↑" for increase, "↓" for decrease, "" for no change
     */
    public String getRateChangeDirection(String from, String to) {
        Map<String, Double> prev = previousRates.get(from);
        CachedRates current = cache.get(from);

        if (prev == null || current == null || current.isExpired()) return "";

        Double prevRate = prev.get(to);
        Double currRate = current.rates().get(to);

        if (prevRate == null || currRate == null) return "";

        if (currRate > prevRate) return "↑";
        if (currRate < prevRate) return "↓";
        return "";
    }

    public void shutdown() {
        ScheduledFuture<?> p = pendingRef.getAndSet(null);
        if (p != null) p.cancel(false);
        debouncer.shutdown();
    }

    /**
     * Fetches exchange rates from the API using secure credentials.
     * Throws on network/timeout/IO errors or on API error responses (see parseJson for key/account errors
     * which also flip apiConfigured=false at runtime).
     */
    private CachedRates fetchRates(String base) throws Exception {
        String apiKey = AppConfig.getApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key not configured");
        }

        URL url = new URL("https://v6.exchangerate-api.com/v6/" + apiKey + "/latest/" + base);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();

        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "GlassCalculator/1.0");

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            return parseJson(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    /**
     * Parses JSON response using Jackson (fast, robust, reuses configured dep).
     * Detects API-level errors (e.g. invalid-key) and triggers runtime disable of service
     * for self-protection against revoked/ invalid keys.
     */
    private CachedRates parseJson(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        if ("error".equals(root.path("result").asText())) {
            String err = root.path("error-type").asText("unknown");
            if ("invalid-key".equals(err) || "inactive-account".equals(err)) {
                apiConfigured = false;
                log.warn("Disabling currency service due to API error: {}", err);
                cache.clear();
                previousRates.clear();
            }
            throw new IllegalStateException("API returned error: " + err);
        }
        Map<String, Double> rates = new LinkedHashMap<>();
        JsonNode ratesNode = root.path("conversion_rates");
        if (ratesNode.isObject()) {
            ratesNode.fields().forEachRemaining(entry -> {
                rates.put(entry.getKey(), entry.getValue().asDouble(0.0));
            });
        }
        String utc = root.path("time_last_update_utc").asText("Unknown");
        return new CachedRates(Collections.unmodifiableMap(rates), utc, System.currentTimeMillis());
    }

    /**
     * Checks if currency service is available.
     * @return true if API key is configured, false otherwise
     */
    public boolean isAvailable() {
        return apiConfigured && AppConfig.isApiKeyConfigured();
    }

    /**
     * Configures the API key and enables the service.
     * @param apiKey The API key from exchangerate-api.com
     */
    public void configureApiKey(String apiKey) {
        String env = System.getenv("GLASS_CALCULATOR_API_KEY");
        if (env != null && !env.isEmpty()) {
            log.warn("Cannot configure via UI while env var is set");
            return;
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            AppConfig.setApiKey(apiKey);
            this.apiConfigured = true;
            cache.clear();
            previousRates.clear();
            log.info("Currency API configured");
        }
    }
}