package utils;

/** Normalizes text at database boundaries without changing passwords or numeric values. */
public final class TextNormalization {
    private TextNormalization() { }

    public static String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public static String stripTrailingToEmpty(Object value) {
        return value == null ? "" : value.toString().stripTrailing();
    }

    public static String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    public static String stripTrailingNullable(String value) {
        return value == null ? null : value.stripTrailing();
    }
}
