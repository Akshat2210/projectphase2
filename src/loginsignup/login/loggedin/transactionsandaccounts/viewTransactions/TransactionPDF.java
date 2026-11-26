package loginsignup.login.loggedin.transactionsandaccounts.viewTransactions;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import utils.CONSTANTS;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Print-only Pana layout. This class never opens a database connection or writes accounting records. */
public final class TransactionPDF {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yy");

    private TransactionPDF() { }

    public record DateRange(LocalDate from, LocalDate to) {
        public DateRange {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (to.isBefore(from)) throw new IllegalArgumentException("अंतिम तारीख पहली तारीख से पहले नहीं हो सकती।");
        }
    }

    /** Positive amount = Cash debit / aavak; negative amount = Cash credit / jaavak. */
    public record Entry(LocalDate date, BigDecimal amount, String account, String description, String pairedAccount) {
        public Entry {
            Objects.requireNonNull(date);
            amount = Objects.requireNonNull(amount).setScale(2, RoundingMode.UNNECESSARY);
            account = Objects.requireNonNull(account).trim();
            if (account.isEmpty()) throw new IllegalArgumentException("Account name is required.");
            description = description == null ? "" : description.strip();
            pairedAccount = pairedAccount == null ? "" : pairedAccount.trim();
        }
    }

    public static Path save(DateRange range, List<Entry> entries, String database, Path dataFolder)
            throws IOException, DocumentException {
        Objects.requireNonNull(range);
        List<Entry> snapshot = List.copyOf(entries);
        if (database == null || database.isBlank() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*") || database.endsWith(" ") || database.endsWith(".")) {
            throw new IOException("Invalid database folder name.");
        }
        Path directory = dataFolder.toAbsolutePath().normalize().resolve(database).resolve("transactionpana");
        Files.createDirectories(directory);
        String stamp = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve("pana_" + range.from + "_" + range.to + "_" + stamp + ".pdf");
        Path temporary = Files.createTempFile(directory, ".pana-", ".tmp");
        try {
            render(range, snapshot, destination.getFileName().toString(), temporary);
            Files.move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void render(DateRange range, List<Entry> entries, String title, Path target)
            throws IOException, DocumentException {
        Path fontPath = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "mangal.ttf");
        if (!Files.isRegularFile(fontPath)) throw new IOException("Mangal font was not found in Windows Fonts.");
        LayoutProcessor.enable();
        BaseFont base = BaseFont.createFont(fontPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        float fontScale = utils.AppSettings.getTransactionPdfFontScale();
        Font heading = new Font(base, 10 * fontScale, Font.BOLD);
        Document document = new Document(PageSize.A4, 30, 30, 35, 35);
        try (OutputStream output = Files.newOutputStream(target)) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.addTitle(title);
            document.open();
            try {
                boolean first = true;
                for (LocalDate day = range.from; !day.isAfter(range.to); day = day.plusDays(1)) {
                    for (boolean incoming : new boolean[]{true, false}) {
                        if (!first) document.newPage();
                        first = false;
                        header(document, day, incoming, heading);
                        final LocalDate selectedDay = day;
                        List<Entry> pageEntries = entries.stream().filter(e -> e.date.equals(selectedDay)
                                && (incoming ? e.amount.signum() > 0 : e.amount.signum() < 0)).toList();
                        float top = writer.getVerticalPosition(true) - 12;
                        float size = 9 * fontScale;
                        ColumnText probe = body(null, pageEntries, incoming, base, size,
                                document.left(), document.bottom(), document.right(), top);
                        if ((probe.go(true) & ColumnText.NO_MORE_TEXT) == 0) {
                            float low = 0, high = size;
                            for (int attempt = 0; attempt < 24; attempt++) {
                                float candidate = (low + high) / 2;
                                ColumnText test = body(null, pageEntries, incoming, base, candidate,
                                        document.left(), document.bottom(), document.right(), top);
                                if ((test.go(true) & ColumnText.NO_MORE_TEXT) != 0) low = candidate;
                                else high = candidate;
                            }
                            size = low;
                        }
                        ColumnText text = body(writer.getDirectContent(), pageEntries, incoming, base, size,
                                document.left(), document.bottom(), document.right(), top);
                        if ((text.go() & ColumnText.NO_MORE_TEXT) == 0) {
                            throw new DocumentException("Pana entries could not fit on the two daily pages.");
                        }
                    }
                }
            } finally {
                document.close();
            }
        }
    }

    private static void header(Document document, LocalDate day, boolean incoming, Font font)
            throws DocumentException {
        PdfPTable row = new PdfPTable(new float[]{1, 1, 1});
        row.setWidthPercentage(100);
        for (int column = 0; column < 3; column++) {
            PdfPCell cell = new PdfPCell(new Phrase(column == 1 ? "श्री" : column == 2 ? DATE.format(day) : "", font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(column == 2 ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER);
            cell.setPadding(0);
            cell.setLeading(0, 1.3f);
            row.addCell(cell);
        }
        document.add(row);
        Paragraph name = new Paragraph(incoming ? "आवक" : "जावक", font);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setSpacingBefore(8);
        document.add(name);
    }

    private static ColumnText body(PdfContentByte canvas, List<Entry> entries, boolean incoming,
                                   BaseFont base, float size, float left, float bottom, float right, float top) {
        ColumnText column = new ColumnText(canvas);
        column.setSimpleColumn(left, bottom, right, top);
        column.setUseAscender(true);
        Font font = new Font(base, size);
        for (Entry entry : entries) {
            String line = entry.amount.abs().toPlainString() + ":\u00a0\u00a0\u00a0\u00a0" + entry.account
                    + (incoming ? " से जमा हुए हस्ते " : " को दिए हस्ते ") + entry.description;
            if (!entry.pairedAccount.isEmpty()) line += ":" + entry.pairedAccount;
            Paragraph paragraph = new Paragraph(line, font);
            paragraph.setLeading(0, 1.4f);
            paragraph.setSpacingAfter(size * 0.35f);
            column.addElement(paragraph);
        }
        return column;
    }
}
