package loginsignup.login.loggedin.processingmanagement.diary;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Paginated, Unicode PDF of the exact history displayed in the window. */
final class PacketHistoryPdf {
    private PacketHistoryPdf() { }

    static Path save(String database, long packetId, List<Object[]> rows) throws Exception {
        if (database == null || database.isBlank() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")
                || database.endsWith(".") || database.endsWith(" ")
                || database.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IOException("The database name cannot be used as a Windows folder name.");
        }
        Path directory = Paths.get("data").toAbsolutePath().normalize().resolve(database).resolve("packet history");
        Files.createDirectories(directory);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve("packet_history_" + packetId + "_" + stamp + "_" + java.util.UUID.randomUUID() + ".pdf");
        Path temporary = Files.createTempFile(directory, ".packet-history-", ".tmp");
        try {
            write(temporary, destination.getFileName().toString(), database, packetId, rows);
            Files.move(temporary, destination);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void write(Path file, String title, String database, long packetId, List<Object[]> rows) throws Exception {
        Path fontFile = Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "mangal.ttf");
        if (!Files.isRegularFile(fontFile)) throw new IOException("Mangal font was not found in Windows Fonts.");
        LayoutProcessor.enable();
        BaseFont base = BaseFont.createFont(fontFile.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Color navy = new Color(24, 53, 74);
        Font normal = new Font(base, 10 * utils.CONSTANTS.PDF_FONT_SCALE);
        Font bold = new Font(base, 10 * utils.CONSTANTS.PDF_FONT_SCALE, Font.BOLD, navy);
        Font small = new Font(base, 8 * utils.CONSTANTS.PDF_FONT_SCALE);
        Document document = new Document(PageSize.A4, 36, 36, 40, 48);
        try (OutputStream stream = Files.newOutputStream(file)) {
            PdfWriter writer = PdfWriter.getInstance(document, stream);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                            new Phrase("Packet history | " + database + " | Send packet ID " + packetId, small), d.left(), 25, 0);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Page " + w.getPageNumber(), small), d.right(), 25, 0);
                }
            });
            document.addTitle(title); // BrowserPdfPrint identifies this exact PDF by its unique title.
            document.open();
            try {
                document.add(new Paragraph("PACKET HISTORY", new Font(base, 20 * utils.CONSTANTS.PDF_FONT_SCALE, Font.BOLD, navy)));
                document.add(new Paragraph(database + " | Send packet ID " + packetId, normal));
                Paragraph note = new Paragraph("Complete forwarding flow. * marks the selected packet. Receive counts are separate audit entries; they are not added to the sent quantities.", small);
                note.setSpacingAfter(12);
                document.add(note);
                for (Object[] row : rows) {
                    PdfPTable event = new PdfPTable(new float[]{1, 3});
                    event.setWidthPercentage(100);
                    event.setSpacingAfter(12);
                    event.setSplitRows(true);
                    event.setSplitLate(false);
                    PdfPCell heading = cell(value(row[0]) + " | " + value(row[3]), bold);
                    heading.setColspan(2);
                    heading.setBackgroundColor(new Color(237, 242, 245));
                    event.addCell(heading);
                    event.setHeaderRows(1);
                    detail(event, "Date", value(row[4]), bold, normal);
                    detail(event, "Send item / parent", value(row[1]) + " / " + (row[2] == null ? "Original send" : value(row[2])), bold, normal);
                    detail(event, "Send lot / packet", value(row[5]), bold, normal);
                    detail(event, "Customer", value(row[6]), bold, normal);
                    detail(event, "From", value(row[7]), bold, normal);
                    detail(event, "To / received at", value(row[8]), bold, normal);
                    detail(event, "Quantity / user", value(row[9]) + " / " + value(row[10]), bold, normal);
                    if (row[11] != null) detail(event, "Order slip", value(row[11]), bold, normal);
                    detail(event, "Remark", value(row[12]), bold, normal);
                    document.add(event);
                }
            } finally { document.close(); }
        }
    }

    private static void detail(PdfPTable table, String label, String value, Font bold, Font normal) {
        table.addCell(cell(label, bold)); table.addCell(cell(value, normal));
    }
    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5); cell.setLeading(0, 1.25f);
        cell.setUseAscender(true); cell.setUseDescender(true);
        cell.setBorderColor(new Color(213, 221, 227)); cell.setBorderWidth(.4f);
        return cell;
    }
    private static String value(Object value) { return value == null ? "" : value.toString(); }
}
