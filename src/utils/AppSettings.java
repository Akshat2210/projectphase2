package utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, editable settings loaded once per application run. */
public final class AppSettings {
    private static final float DEFAULT_PDF_FONT_SCALE = 1.6f;
    private static final String DEFAULT_JSON = "{\n  \"pdfFontScale\": 1.6\n}\n";
    private static final Pattern SETTINGS = Pattern.compile(
            "\\s*\\{\\s*\"pdfFontScale\"\\s*:\\s*"
                    + "(-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)\\s*}\\s*");
    private static final float PDF_FONT_SCALE = loadPdfFontScale();

    private AppSettings() { }

    public static float getPdfFontScale() {
        return PDF_FONT_SCALE;
    }

    private static float loadPdfFontScale() {
        Path path = Path.of("settings.json").toAbsolutePath().normalize();
        try {
            try {
                // CREATE_NEW never overwrites an existing user's settings.
                Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException existing) {
                // Read the existing local settings below.
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) json = json.substring(1);
            Matcher matcher = SETTINGS.matcher(json);
            if (!matcher.matches()) throw new IllegalArgumentException("Expected {\"pdfFontScale\": number}.");
            float scale = Float.parseFloat(matcher.group(1));
            if (!Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("pdfFontScale must be a finite number greater than zero.");
            }
            return scale;
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            // A damaged/unwritable settings file must not prevent login or erase user preferences.
            System.err.println("Could not load " + path + ": " + failure.getMessage()
                    + ". Using PDF font scale " + DEFAULT_PDF_FONT_SCALE + ".");
            return DEFAULT_PDF_FONT_SCALE;
        }
    }
}
