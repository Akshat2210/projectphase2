package utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local PDF defaults and sparse overrides keyed by the authenticated MySQL username. */
public final class AppSettings {
    private static final String COMMON = "pdfFontScale";
    private static final String BILLING = "billingPdfFontScale";
    private static final String ORDER = "orderSlipPdfFontScale";
    private static final String TRANSACTION = "transactionPdfFontScale";
    private static final String[] KEYS = {COMMON, BILLING, ORDER, TRANSACTION};
    private static final float DEFAULT_SCALE = 1.6f;
    private static final Path FILE = Path.of("settings.json").toAbsolutePath().normalize();
    private static Map<String, Object> settings = loadSafely();
    private static String username;

    private AppSettings() { }

    public static synchronized float getPdfFontScale() { return scale(COMMON); }
    public static synchronized float getBillingPdfFontScale() { return scale(BILLING); }
    public static synchronized float getOrderSlipPdfFontScale() { return scale(ORDER); }
    public static synchronized float getTransactionPdfFontScale() { return scale(TRANSACTION); }

    /** Select only after successful authentication; no profile is created until a setting changes. */
    public static synchronized void selectUser(String mysqlUsername) {
        String selected = Objects.requireNonNull(mysqlUsername, "MySQL username").trim();
        if (selected.isEmpty()) throw new IllegalArgumentException("MySQL username is required.");
        settings = loadSafely();
        username = selected;
    }

    public static synchronized void clearUser() { username = null; }
    public static synchronized String getSelectedUsername() { return username; }
    public static synchronized void setPdfFontScale(float value) throws IOException { saveScale(COMMON, value); }
    public static synchronized void setBillingPdfFontScale(float value) throws IOException { saveScale(BILLING, value); }
    public static synchronized void setOrderSlipPdfFontScale(float value) throws IOException { saveScale(ORDER, value); }
    public static synchronized void setTransactionPdfFontScale(float value) throws IOException { saveScale(TRANSACTION, value); }

    private static float scale(String key) {
        Map<String, Object> defaults = object(settings.get("defaults"), "defaults");
        float fallback = numeric(defaults.get(COMMON), DEFAULT_SCALE);
        fallback = numeric(defaults.get(key), fallback);
        if (username == null) return fallback;
        Object profile = object(settings.get("users"), "users").get(username);
        return profile == null ? fallback : numeric(object(profile, "user settings").get(key), fallback);
    }

    private static void saveScale(String key, float value) throws IOException {
        saveScales(Map.of(key, value));
    }

    public static synchronized void savePdfFontScales(float common, float billing, float order,
                                                      float transaction) throws IOException {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(COMMON, common);
        values.put(BILLING, billing);
        values.put(ORDER, order);
        values.put(TRANSACTION, transaction);
        saveScales(values);
    }

    private static void saveScales(Map<String, Float> values) throws IOException {
        if (username == null) throw new IllegalStateException("Sign in before saving personal PDF settings.");
        for (float value : values.values()) {
            if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Font scale must be positive and finite.");
        }
        Map<String, Object> updated;
        try {
            // Never overwrite damaged JSON with fallback defaults when saving a personal setting.
            updated = readSettings();
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Cannot save settings: " + invalid.getMessage(), invalid);
        }
        Map<String, Object> users = object(updated.get("users"), "users");
        Map<String, Object> profile = object(users.computeIfAbsent(username, name -> new LinkedHashMap<>()), "user settings");
        for (Map.Entry<String, Float> value : values.entrySet()) {
            profile.put(value.getKey(), new BigDecimal(Float.toString(value.getValue())));
        }
        writeAtomically(updated);
        settings = updated;
    }

    private static Map<String, Object> loadSafely() {
        try {
            return readSettings();
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            System.err.println("Could not load " + FILE + ": " + failure.getMessage() + ". Using PDF defaults.");
            return freshSettings();
        }
    }

    private static Map<String, Object> readSettings() throws IOException {
        if (!Files.exists(FILE)) {
            Map<String, Object> fresh = freshSettings();
            try {
                Files.writeString(FILE, json(fresh, 0) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                return fresh;
            } catch (FileAlreadyExistsException existing) { /* Read the existing file below. */ }
        }
        String text = Files.readString(FILE, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        Map<String, Object> root = new Json(text).read();
        if (!root.containsKey("defaults") && !root.containsKey("users")) {
            // Legacy flat values become defaults, never an arbitrary user's personal settings.
            float common = numeric(root.get(COMMON), DEFAULT_SCALE);
            Map<String, Object> defaults = new LinkedHashMap<>();
            for (String key : KEYS) defaults.put(key, numeric(root.get(key), common));
            root = freshSettings();
            root.put("defaults", defaults);
            writeAtomically(root);
        }
        object(root.get("defaults"), "defaults");
        for (Object profile : object(root.get("users"), "users").values()) object(profile, "user settings");
        return root;
    }

    private static Map<String, Object> freshSettings() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (String key : KEYS) defaults.put(key, DEFAULT_SCALE);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("defaults", defaults);
        root.put("users", new LinkedHashMap<String, Object>());
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(label + " must be a JSON object.");
        return (Map<String, Object>) value;
    }

    private static float numeric(Object value, float fallback) {
        if (value instanceof Number number) {
            float scale = number.floatValue();
            if (Float.isFinite(scale) && scale > 0) return scale;
        }
        return fallback;
    }

    private static void writeAtomically(Map<String, Object> value) throws IOException {
        Path temporary = Files.createTempFile(FILE.getParent(), ".settings-", ".tmp");
        try {
            Files.writeString(temporary, json(value, 0) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String json(Object value, int depth) {
        if (value instanceof Number) return value.toString();
        if (value instanceof String string) return quote(string);
        Map<String, Object> map = object(value, "settings");
        if (map.isEmpty()) return "{}";
        StringBuilder out = new StringBuilder("{\n");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (out.length() > 2) out.append(",\n");
            out.append("  ".repeat(depth + 1)).append(quote(entry.getKey())).append(": ").append(json(entry.getValue(), depth + 1));
        }
        return out.append('\n').append("  ".repeat(depth)).append('}').toString();
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    /** Object/string/number JSON reader for the settings format; no additional library required. */
    private static final class Json {
        private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
        private final String text;
        private int offset;
        Json(String text) { this.text = text; }
        Map<String, Object> read() {
            Map<String, Object> root = object(value(0), "settings");
            whitespace();
            if (offset != text.length()) throw bad();
            return root;
        }
        private Object value(int depth) {
            if (depth > 8) throw bad();
            whitespace();
            if (take('{')) {
                Map<String, Object> map = new LinkedHashMap<>();
                whitespace();
                if (take('}')) return map;
                do {
                    whitespace();
                    String key = string();
                    whitespace();
                    if (!take(':') || map.containsKey(key)) throw bad();
                    map.put(key, value(depth + 1));
                    whitespace();
                    if (take('}')) return map;
                } while (take(','));
                throw bad();
            }
            if (offset < text.length() && text.charAt(offset) == '"') return string();
            Matcher number = NUMBER.matcher(text);
            number.region(offset, text.length());
            if (!number.lookingAt()) throw bad();
            offset = number.end();
            try { return new BigDecimal(number.group()); }
            catch (NumberFormatException failure) { throw bad(); }
        }
        private String string() {
            if (!take('"')) throw bad();
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char c = text.charAt(offset++);
                if (c == '"') return value.toString();
                if (c < 32) throw bad();
                if (c == '\\') {
                    if (offset == text.length()) throw bad();
                    char escape = text.charAt(offset++);
                    c = switch (escape) {
                        case '"', '\\', '/' -> escape;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        case 'u' -> {
                            if (offset + 4 > text.length()) throw bad();
                            String hex = text.substring(offset, offset + 4);
                            if (!hex.matches("[0-9a-fA-F]{4}")) throw bad();
                            offset += 4;
                            yield (char) Integer.parseInt(hex, 16);
                        }
                        default -> throw bad();
                    };
                }
                value.append(c);
            }
            throw bad();
        }
        private void whitespace() {
            while (offset < text.length() && " \r\n\t".indexOf(text.charAt(offset)) >= 0) offset++;
        }
        private boolean take(char c) {
            if (offset < text.length() && text.charAt(offset) == c) { offset++; return true; }
            return false;
        }
        private IllegalArgumentException bad() { return new IllegalArgumentException("Invalid settings JSON at position " + offset); }
    }
}
