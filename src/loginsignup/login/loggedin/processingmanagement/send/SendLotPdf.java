package loginsignup.login.loggedin.processingmanagement.send;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Text-based PDF export of the exact snapshot committed by Send. */
final class SendLotPdf {
    private static final Color NAVY = new Color(24, 53, 74);
    private static final Color PALE = new Color(237, 242, 245);
    private static final Color LINE = new Color(213, 221, 227);

    private SendLotPdf() { }

    static Path save(Path dataFolder, String database, long lotId, Send.LotDraft draft,
                     String from, String to, ZonedDateTime timestamp) throws IOException, DocumentException {
        validateDatabaseFolder(database);
        Path root = dataFolder.toAbsolutePath().normalize();
        Path directory = root.resolve(database).resolve("send diary").normalize();
        if (!directory.startsWith(root)) throw new IOException("Invalid database folder.");
        Files.createDirectories(directory);
        String stamp = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve("sent_" + stamp + "_" + lotId + ".pdf");
        // Finish a temporary file first; a failed export never leaves a truncated final PDF.
        Path temporary = Files.createTempFile(directory, ".send-" + lotId + "-", ".tmp");
        try {
            write(temporary, database, lotId, draft, from, to, destination.getFileName().toString());
            // No REPLACE_EXISTING: an earlier receipt is never overwritten.
            Files.move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateDatabaseFolder(String database) throws IOException {
        if (database == null || database.trim().isEmpty() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")
                || database.endsWith(".") || database.endsWith(" ")
                || database.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IOException("The database name cannot be used as a Windows folder name.");
        }
    }

    private static void write(Path path, String database, long lotId, Send.LotDraft draft,
                              String from, String to, String receiptTitle) throws IOException, DocumentException {
        // Embed Windows' Mangal font so Hindi names/remarks remain readable in the PDF.
        Path windows = Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"));
        Path unicode = windows.resolve("Fonts").resolve("mangal.ttf");
        if (!Files.isRegularFile(unicode)) throw new IOException("Mangal font was not found in Windows Fonts.");
        LayoutProcessor.enable();
        BaseFont base = BaseFont.createFont(unicode.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font normal = new Font(base, 10, Font.NORMAL, Color.BLACK);
        Font bold = new Font(base, 10, Font.BOLD, NAVY);
        Font small = new Font(base, 8, Font.NORMAL, new Color(86, 101, 117));
        Font title = new Font(base, 22, Font.BOLD, NAVY);
        Font white = new Font(base, 9, Font.BOLD, Color.WHITE);
        Document document = new Document(PageSize.A4, 40, 40, 48, 52);
        try (OutputStream stream = Files.newOutputStream(path)) {
            PdfWriter writer = PdfWriter.getInstance(document, stream);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    PdfContentByte canvas = w.getDirectContent();
                    canvas.setColorStroke(LINE);
                    canvas.moveTo(d.left(), 39);
                    canvas.lineTo(d.right(), 39);
                    canvas.stroke();
                    ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                            new Phrase("Send Diary | " + database + " | Lot " + lotId, small), d.left(), 25, 0);
                    ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                            new Phrase("Page " + w.getPageNumber(), small), d.right(), 25, 0);
                    if (w.getPageNumber() > 1) {
                        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                                new Phrase("LOT " + lotId + " | continued", small), d.left(), d.top() + 15, 0);
                    }
                }
            });
            document.addTitle(receiptTitle); // Unique title lets the print helper identify this exact browser window.
            document.addAuthor(draft.user);
            document.open();
            try {
                Paragraph heading = new Paragraph("SEND DIARY", title);
                heading.setSpacingAfter(4);
                document.add(heading);
                Paragraph business = new Paragraph(database, small);
                business.setSpacingAfter(16);
                document.add(business);
                PdfPTable summary = new PdfPTable(new float[]{1.1f, 1f});
                summary.setWidthPercentage(100);
                summary.addCell(cell("LOT " + lotId, bold, PALE, 1));
                summary.addCell(cell(draft.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), bold, PALE, 1));
                summary.addCell(cell("Send From: " + from, normal, PALE, 1));
                summary.addCell(cell("Send To: " + to, normal, PALE, 1));
                int entries = 0;
                long pieces = 0;
                for (Send.PacketDraft packet : draft.packets) {
                    entries += packet.items.size();
                    for (Send.ItemDraft item : packet.items) pieces += item.quantity;
                }
                summary.addCell(cell(draft.packets.size() + (draft.packets.size() == 1 ? " packet" : " packets")
                        + " / " + entries + " entries / " + pieces + " pieces", small, PALE, 1));
                summary.addCell(cell("Created by: " + draft.user, small, PALE, 1));
                summary.setSpacingAfter(16);
                document.add(summary);
                for (Send.PacketDraft packet : draft.packets) {
                    PdfPTable table = new PdfPTable(new float[]{.45f, 2.2f, .55f, 2.7f});
                    table.setWidthPercentage(100);
                    table.setSpacingAfter(14);
                    table.addCell(cell("Packet " + packet.number + " | "
                            + (packet.customer.isEmpty() ? "No customer" : packet.customer), bold, PALE, 4));
                    table.addCell(cell("Packet remark: " + packet.remark, normal, PALE, 4));
                    for (String label : new String[]{"No.", "Item name", "Qty", "Item remark"}) {
                        table.addCell(cell(label, white, NAVY, 1));
                    }
                    // Repeat packet identity, remark and column names when the table continues.
                    table.setHeaderRows(3);
                    table.setSplitRows(true);
                    table.setSplitLate(false);
                    for (int index = 0; index < packet.items.size(); index++) {
                        Send.ItemDraft item = packet.items.get(index);
                        Color background = index % 2 == 0 ? Color.WHITE : new Color(248, 250, 251);
                        table.addCell(cell(String.valueOf(index + 1), normal, background, 1));
                        table.addCell(cell(item.name, normal, background, 1));
                        PdfPCell quantity = cell(String.valueOf(item.quantity), normal, background, 1);
                        quantity.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(quantity);
                        table.addCell(cell(item.remark, normal, background, 1));
                    }
                    table.setTotalWidth(document.right() - document.left());
                    table.calculateHeights(true);
                    table.setKeepTogether(table.getTotalHeight() < document.top() - document.bottom() - 20);
                    document.add(table);
                }
            } finally {
                document.close();
            }
        }
    }

    private static PdfPCell cell(String text, Font font, Color background, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setColspan(colspan);
        cell.setPadding(7);
        cell.setLeading(0, 1.25f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(.4f);
        cell.setBackgroundColor(background);
        return cell;
    }
}
