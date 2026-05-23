package Project.engine;

import Project.model.ExpressionParser;
import java.math.BigInteger;

/**
 * Handles core arithmetic evaluation, scientific functions, and result formatting.
 * Supports lastAns for ans/last in expr via parser. Division errors explicit.
 * (Memory and history management moved to UI layer for separation of concerns.)
 */
public class CalculatorEngine {

    // ═══════════════════════════════════════════════════════════════════
    //  CALCULATION METHODS
    // ═══════════════════════════════════════════════════════════════════
    public double eval(String expr, boolean radianMode) throws ArithmeticException {
        return eval(expr, radianMode, Double.NaN);
    }

    /**
     * Evaluates expression, with optional last result for 'ans'/'last' references (e.g. ans+5).
     * Parser creates per-call (acceptable for desktop calc; reuse would need mutable reset()).
     */
    public double eval(String expr, boolean radianMode, double lastAns) throws ArithmeticException {
        return new ExpressionParser(expr, radianMode, lastAns).parse();
    }

    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        if (Math.abs(v) < 1e-12 && v != 0) return "0";
        // Integer check (avoids trailing .0)
        if (v == Math.floor(v) && Math.abs(v) < 1e15)
            return String.valueOf((long) v);
        double abs = Math.abs(v);
        if (abs >= 1e10 || (abs > 0 && abs < 1e-5))
            return String.format("%.8g", v).replaceAll("0+E", "E");
        return String.format("%.10f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    public double percent(double v) {
        return v / 100;
    }

    public double negate(double v) {
        return -v;
    }

    public double reciprocal(double v) {
        if (v == 0) throw new ArithmeticException("Division by zero");
        return 1.0 / v;
    }

    public double square(double v) {
        return v * v;
    }

    public double cube(double v) {
        return v * v * v;
    }

    public BigInteger factorial(double n) {
        if (n < 0 || n > 170 || n != (long) n) {
            throw new IllegalArgumentException("Invalid factorial input");
        }
        BigInteger r = BigInteger.ONE;
        for (long i = 2; i <= (long) n; i++) {
            r = r.multiply(BigInteger.valueOf(i));
        }
        return r;
    }
}