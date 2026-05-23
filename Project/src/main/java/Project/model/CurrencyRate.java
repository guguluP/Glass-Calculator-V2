package Project.model;

/**
 * Immutable value object representing a single currency exchange rate.
 * Can be used by CurrencyService / future UI for typed rate data instead of raw Map.
 */
public record CurrencyRate(
    String from,
    String to,
    double rate,
    String updatedUtc   // from API time_last_update_utc
) {
    public CurrencyRate {
        if (from == null || to == null) throw new IllegalArgumentException("from/to required");
        if (rate < 0) throw new IllegalArgumentException("rate must be non-negative");
    }
}
