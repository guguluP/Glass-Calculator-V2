package Project.model;

import java.time.LocalDateTime;

/**
 * Immutable model for a single history entry (calculation or conversion).
 * Replaces ad-hoc string formatting "expr = res" in UI lists and DBManager.
 * Compatible with current DB schema (added type column previously).
 */
public record CalculationHistoryEntry(
    long id,
    String type,           // "calc" | "conv"
    String expression,
    String result,
    LocalDateTime createdAt
) {
    public CalculationHistoryEntry(String type, String expression, String result) {
        this(0L, type, expression, result, LocalDateTime.now());
    }

    @Override
    public String toString() {
        return expression + " = " + result;
    }
}
