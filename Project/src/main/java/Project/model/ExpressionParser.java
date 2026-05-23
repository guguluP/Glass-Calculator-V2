package Project.model;

public final class ExpressionParser {
    private final String expr;
    private final boolean radians;
    private int pos;

    public ExpressionParser(String expression, boolean radians) {
        this.expr = expression;
        this.radians = radians;
        this.pos = 0;
    }

    private void skipWS() {
        while (pos < expr.length() && Character.isWhitespace(expr.charAt(pos))) pos++;
    }

    public double parse() throws ArithmeticException {
        skipWS();
        double v = addSub();
        skipWS();
        if (pos < expr.length()) throw new ArithmeticException(
                "Unexpected token at " + pos + ": '" + expr.charAt(pos) + "'");
        return v;
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
                if (d == 0) throw new ArithmeticException("Division by zero");
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
        return matchesAt("sin(", 4) || matchesAt("cos(", 4) || matchesAt("tan(", 4) ||
               matchesAt("asin(", 5) || matchesAt("acos(", 5) || matchesAt("atan(", 5) ||
               matchesAt("sinh(", 5) || matchesAt("cosh(", 5) || matchesAt("tanh(", 5) ||
               matchesAt("asinh(", 6) || matchesAt("acosh(", 6) || matchesAt("atanh(", 6) ||
               matchesAt("log2(", 5) || matchesAt("log(", 4) || matchesAt("ln(", 3) ||
               matchesAt("sqrt(", 5) || matchesAt("cbrt(", 5) || matchesAt("pi", 2) ||
               matchesAt("π", 1) || matchesAt("√(", 2) ||
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
        if (pos >= expr.length()) throw new ArithmeticException("Unexpected end of expression");
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
        if (c == 'e' && (pos + 1 >= expr.length() || !Character.isLetter(expr.charAt(pos + 1)))) {
            pos++;
            return Math.E;
        }

        throw new ArithmeticException("Unknown token: '" + expr.charAt(pos) + "' at pos " + pos);
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
            throw new ArithmeticException("Bad number at " + start);
        }
    }

    private void expect(char ch) throws ArithmeticException {
        skipWS();
        if (pos >= expr.length() || expr.charAt(pos) != ch) {
            throw new ArithmeticException("Expected '" + ch + "' at position " + pos);
        }
        pos++;
    }
}