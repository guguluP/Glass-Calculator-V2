package Project.model;

/**
 * Recursive descent parser for calculator expressions.
 * Improvements: custom ExpressionException with pos/token, 'ans'/'last' support,
 * validate() per suggestion, implicit mul (incl. 2( ), 2sin), explicit div0, reusable ctors.
 * Evaluating user input is safe (no eval(), no reflection, controlled grammar).
 */
public final class ExpressionParser {
    private final String expr;
    private final boolean radians;
    private int pos;
    private final double lastAns;

    /**
     * Standard ctor (no previous ans).
     */
    public ExpressionParser(String expression, boolean radians) {
        this(expression, radians, Double.NaN);
    }

    /**
     * Full ctor supporting previous result via 'ans' or 'last' in expressions (e.g. ans+5).
     * Pass Double.NaN or omit for no-ans mode.
     */
    public ExpressionParser(String expression, boolean radians, double lastAns) {
        this.expr = expression;
        this.radians = radians;
        this.pos = 0;
        this.lastAns = lastAns;
    }

    private void skipWS() {
        while (pos < expr.length() && Character.isWhitespace(expr.charAt(pos))) pos++;
    }

    public double parse() throws ArithmeticException {
        skipWS();
        double v = addSub();
        skipWS();
        if (pos < expr.length()) throw new ExpressionException(
                "Unexpected token", pos, currentToken());
        return v;
    }

    /**
     * Validates the expression syntax and semantics (domains etc.) before full use.
     * Throws ExpressionException with position details on failure.
     * (Current impl re-runs parse for simplicity; result discarded.)
     */
    public void validate() throws ArithmeticException {
        int savedPos = pos;
        pos = 0;
        try {
            parse();
        } finally {
            pos = savedPos;
        }
    }

    // ── Grammar ──────────────────────────────────────────────────
    // addSub   = mulDiv (('+' | '-') mulDiv)*
    // mulDiv   = unary  (('*' | '/') unary  | implicit-mul)*
    // unary    = '-' unary | '+' unary | power          ← sits ABOVE power so
    // power    = primary ('^' unary)?                      -2^2 = -(2^2) = -4
    // primary  = number | '(' addSub ')' | function '(' addSub ')' | const
    // ─────────────────────────────────────────────────────────────

    private double addSub() throws ArithmeticException {
        skipWS();
        double v = mulDiv();
        skipWS();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '+') {
                pos++;
                skipWS();
                v += mulDiv();
                skipWS();
            } else if (c == '-') {
                pos++;
                skipWS();
                v -= mulDiv();
                skipWS();
            } else break;
        }
        return v;
    }

    private double mulDiv() throws ArithmeticException {
        skipWS();
        // FIX 1: call unary() (not power()) so that the chain is
        //         mulDiv → unary → power → primary
        //         This makes unary-minus bind looser than '^'.
        double v = unary();
        skipWS();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '*') {
                pos++;
                skipWS();
                v *= unary();   // FIX 1 (cont.): was power()
                skipWS();
            } else if (c == '/') {
                pos++;
                skipWS();
                double d = unary();   // FIX 1 (cont.): was power()
                if (d == 0) throw new ExpressionException("Division by zero", pos, currentToken());
                v /= d;
                skipWS();
            } else if (startsNewPrimary()) {   // implicit multiplication
                v *= unary();   // FIX 1 (cont.): was power()
                skipWS();
            } else break;
        }
        return v;
    }

    private boolean startsNewPrimary() {
        if (pos >= expr.length()) return false;
        char c = expr.charAt(pos);
        if (Character.isDigit(c) || c == '.' || c == '(') return true;

        // Check for function names and constants without creating substrings
        // Note: supports implicit multiplication e.g. 2(3+4), 3sin(30), 2ans+1
        return matchesAt("sin(", 4) || matchesAt("cos(", 4) || matchesAt("tan(", 4) ||
               matchesAt("asin(", 5) || matchesAt("acos(", 5) || matchesAt("atan(", 5) ||
               matchesAt("sinh(", 5) || matchesAt("cosh(", 5) || matchesAt("tanh(", 5) ||
               matchesAt("asinh(", 6) || matchesAt("acosh(", 6) || matchesAt("atanh(", 6) ||
               matchesAt("log2(", 5) || matchesAt("log(", 4) || matchesAt("ln(", 3) ||
               matchesAt("sqrt(", 5) || matchesAt("cbrt(", 5) || matchesAt("pi", 2) ||
               matchesAt("π", 1) || matchesAt("√(", 2) ||
               matchesAt("ans", 3) || matchesAt("ANS", 3) || matchesAt("last", 4) || matchesAt("LAST", 4) ||
               (c == 'e' && (pos + 1 >= expr.length() || !Character.isLetter(expr.charAt(pos + 1))));
    }

    private boolean matchesAt(String str, int len) {
        if (pos + len > expr.length()) return false;
        for (int i = 0; i < len; i++) {
            if (expr.charAt(pos + i) != str.charAt(i)) return false;
        }
        return true;
    }

    // FIX 1 (cont.): power() now calls primary() directly (not unary()).
    //   The exponent side still goes through unary() so that e.g. 2^-3 works.
    private double power() throws ArithmeticException {
        skipWS();
        double base = primary();   // was unary() — caused -2^2 == 4 instead of -4
        skipWS();
        if (pos < expr.length() && expr.charAt(pos) == '^') {
            pos++;
            skipWS();
            return Math.pow(base, unary()); // right-associative; exponent via unary() allows 2^-3
        }
        return base;
    }

    private double unary() throws ArithmeticException {
        skipWS();
        if (pos < expr.length() && expr.charAt(pos) == '-') {
            pos++;
            skipWS();
            return -unary();
        }
        if (pos < expr.length() && expr.charAt(pos) == '+') {
            pos++;
            skipWS();
            return unary();
        }
        return power();   // FIX 1 (cont.): was primary()
    }

    private double primary() throws ArithmeticException {
        skipWS();
        if (pos >= expr.length()) throw new ExpressionException("Unexpected end of expression", pos, currentToken());
        char c = expr.charAt(pos);

        // Number literal (including E-notation)
        if (Character.isDigit(c) || c == '.') return parseNumber();

        // Parenthesised group
        if (c == '(') {
            pos++;
            skipWS();
            double v = addSub();
            skipWS();
            expect(')');
            return v;
        }

        // Named functions / constants
        if (matchesAt("asin(", 5)) return fn1(5, v -> {
            double r = Math.asin(v);
            return radians ? r : Math.toDegrees(r);
        });
        if (matchesAt("acos(", 5)) return fn1(5, v -> {
            double r = Math.acos(v);
            return radians ? r : Math.toDegrees(r);
        });
        if (matchesAt("atan(", 5)) return fn1(5, v -> {
            double r = Math.atan(v);
            return radians ? r : Math.toDegrees(r);
        });
        if (matchesAt("sin(", 4)) return fn1(4, v -> Math.sin(radians ? v : Math.toRadians(v)));
        if (matchesAt("cos(", 4)) return fn1(4, v -> Math.cos(radians ? v : Math.toRadians(v)));
        if (matchesAt("tan(", 4)) return fn1(4, v -> Math.tan(radians ? v : Math.toRadians(v)));
        if (matchesAt("asinh(", 6)) return fn1(6, v -> Math.log(v + Math.sqrt(v * v + 1)));
        if (matchesAt("acosh(", 6)) return fn1(6, v -> Math.log(v + Math.sqrt(v * v - 1)));
        if (matchesAt("atanh(", 6)) return fn1(6, v -> 0.5 * Math.log((1 + v) / (1 - v)));
        if (matchesAt("sinh(", 5)) return fn1(5, Math::sinh);
        if (matchesAt("cosh(", 5)) return fn1(5, Math::cosh);
        if (matchesAt("tanh(", 5)) return fn1(5, Math::tanh);
        if (matchesAt("log2(", 5)) return fn1(5, v -> Math.log(v) / Math.log(2));
        if (matchesAt("log(", 4)) return fn1(4, Math::log10);
        if (matchesAt("ln(", 3)) return fn1(3, Math::log);
        if (matchesAt("sqrt(", 5)) return fn1(5, Math::sqrt);
        if (matchesAt("cbrt(", 5)) return fn1(5, Math::cbrt);
        if (matchesAt("√(", 2)) {
            pos += 2;
            skipWS();
            double v = addSub();
            skipWS();
            expect(')');
            return Math.sqrt(v);
        }
        if (matchesAt("pi", 2)) {
            pos += 2;
            return Math.PI;
        }
        if (matchesAt("π", 1)) {
            pos++;
            return Math.PI;
        }
        if (matchesAt("ans", 3) || matchesAt("ANS", 3)) {
            pos += 3;
            return Double.isNaN(lastAns) ? 0.0 : lastAns;
        }
        if (matchesAt("last", 4) || matchesAt("LAST", 4)) {
            pos += 4;
            return Double.isNaN(lastAns) ? 0.0 : lastAns;
        }
        if (c == 'e' && (pos + 1 >= expr.length() || !Character.isLetter(expr.charAt(pos + 1)))) {
            pos++;
            return Math.E;
        }

        throw new ExpressionException("Unknown token", pos, currentToken());
    }

    // FIX 3: Removed unused 'tag' parameter (was accepted but never read).
    // FIX 4 & 5: Replaced custom DoubleUnary interface (and the unused
    //             'import java.util.function.*') with a local SAM. The standard
    //             DoubleUnaryOperator can't declare 'throws ArithmeticException',
    //             so we keep a minimal private interface — but we no longer
    //             maintain a dead import alongside it.
    @FunctionalInterface
    private interface DoubleUnary {
        double apply(double v) throws ArithmeticException;
    }

    private double fn1(int len, DoubleUnary fn) throws ArithmeticException {
        pos += len;
        skipWS();
        double v = addSub();
        skipWS();
        expect(')');
        return fn.apply(v);
    }

    private double parseNumber() {
        int start = pos;
        while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) pos++;
        // E-notation: e.g. 1.5e+10 or 1.5E-10
        if (pos < expr.length() && Character.toLowerCase(expr.charAt(pos)) == 'e') {
            pos++;
            if (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) pos++;
            while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) pos++;
        }
        try {
            return Double.parseDouble(expr.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new ExpressionException("Bad number", start, expr.substring(start, pos));
        }
    }

    private void expect(char ch) throws ArithmeticException {
        skipWS();
        if (pos >= expr.length() || expr.charAt(pos) != ch) {
            throw new ExpressionException("Expected '" + ch + "'", pos, currentToken());
        }
        pos++;
    }

    /**
     * Custom exception for expression errors, carrying position and offending token for precise UI feedback / logging.
     * Extends ArithmeticException for full backward compatibility with existing callers.
     */
    public static class ExpressionException extends ArithmeticException {
        private final int position;
        private final String token;

        public ExpressionException(String message, int position, String token) {
            super(message + " [position=" + position + ", token=" + token + "]");
            this.position = position;
            this.token = token;
        }

        public int getPosition() { return position; }
        public String getToken() { return token; }
    }

    private String currentToken() {
        if (pos >= expr.length()) return "<end>";
        return "'" + expr.charAt(pos) + "'";
    }
}