package Project.model;

/**
 * Snapshot of calculator runtime state for save/restore, testing, or multi-calc scenarios.
 * Captures memory, mode, last result/expression (for 'ans' support etc).
 */
public record CalculatorState(
    double memory,
    boolean radiansMode,
    double lastResult,
    String lastExpression
) {
    public CalculatorState {
        if (lastExpression == null) lastExpression = "";
    }

    public static CalculatorState empty() {
        return new CalculatorState(0.0, true, 0.0, "");
    }
}
