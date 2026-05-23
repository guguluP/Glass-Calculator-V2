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
    private ScheduledFuture<?> pending;

    private volatile boolean apiConfigured = false;
    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    // Jackson mapper (reused for performance, thread-safe)
    private final ObjectMapper mapper = new ObjectMapper();

    public CurrencyService() {
        this.apiConfigured = AppConfig.isApiKeyConfigured();
        if (!apiConfigured) {
            log.info("Currency API not configured. Users can add it later.");
        }
    }

    /**
     * Returns cached rate immediately if fresh; otherwise returns NaN
     * and fires a background fetch that calls onResult when done.
     */
    public double rateOrFetch(String from, String to,
                       Runnable onResult, Runnable onError) {
        // If API not configured, return NaN
        if (!apiConfigured) {
            log.warn("Currency API key not configured");
            Platform.runLater(onError);
            return Double.NaN;
        }

        CachedRates cr = cache.get(from);
        if (cr != null && !cr.isExpired()) {
            return cr.rates.getOrDefault(to, Double.NaN);
        }

        if (fetching.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    CachedRates newRates = fetchRates(from);
                    CachedRates oldRates = cache.get(from);
                    if (oldRates != null) {
                        previousRates.put(from, new HashMap<>(oldRates.rates()));
                    }
                    cache.put(from, newRates);
                    Platform.runLater(onResult);
                } catch (Exception e) {
                    log.error("Failed to fetch rates: {}", e.getMessage(), e);
                    Platform.runLater(onError);
                } finally {
                    fetching.set(false);
                }
            }).start();
        }
        return Double.NaN; // signal: not cached yet
    }

    /**
     * Debounce: callback fires 350 ms after last call.
     */
    public void debounce(Runnable callback) {
        if (pending != null) pending.cancel(false);
        pending = debouncer.schedule(
                () -> Platform.runLater(callback), 350, TimeUnit.MILLISECONDS);
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

        if (prev == null || current == null) return "";

        Double prevRate = prev.get(to);
        Double currRate = current.rates().get(to);

        if (prevRate == null || currRate == null) return "";

        if (currRate > prevRate) return "↑";
        if (currRate < prevRate) return "↓";
        return "";
    }

    public void shutdown() {
        debouncer.shutdown();
    }

    /**
     * Fetches exchange rates from the API using secure credentials.
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
     */
    private CachedRates parseJson(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
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
        if (apiKey != null && !apiKey.isEmpty()) {
            AppConfig.setApiKey(apiKey);
            this.apiConfigured = true;
            log.info("Currency API configured");
        }
    }
}