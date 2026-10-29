package loginsignup.login.loggedin.ordermanagement.generateorder;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.sql.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Sequential export of saved order-slip data in the existing six-column print layout. */
public final class OrderSlipPdf {
    private OrderSlipPdf() { }

    static Path save(Connection connection, int slipId, String database, Path dataFolder)
            throws SQLException, IOException, DocumentException {
        return write(read(connection, slipId), database, dataFolder);
    }

    static SlipData read(Connection connection, int slipId) throws SQLException {
        String sql = "SELECT m.slip_id, m.created_at, m.customer_name, m.slip_type,"
                + " i.item_id, i.design_id, i.item_name, i.quantity, i.plating_grams,"
                + " i.raw_material_price, i.other_details FROM order_slips_main m"
                + " LEFT JOIN order_slips i ON i.slip_id = m.slip_id"
                + " WHERE m.slip_id = ? ORDER BY i.sno, i.item_id";
        SlipData data = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, slipId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (data == null) {
                        data = new SlipData();
                        data.id = result.getInt("slip_id");
                        Timestamp date = result.getTimestamp("created_at");
                        data.date = date == null ? "" : date.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                        data.customer = result.getString("customer_name");
                        data.type = result.getString("slip_type");
                    }
                    result.getLong("item_id");
                    if (!result.wasNull()) {
                        data.rows.add(new String[]{result.getString("design_id"), result.getString("item_name"),
                                result.getString("quantity"), result.getString("plating_grams"),
                                result.getString("raw_material_price"), result.getString("other_details")});
                    }
                }
            }
        }
        if (data == null) throw new SQLException("Saved order slip " + slipId + " was not found.");
        return data;
    }

    public static Path saveView(Connection connection, int slipId, String database, Path dataFolder,
                                javax.swing.table.TableModel displayed) throws SQLException, IOException, DocumentException {
        if (displayed.getColumnCount() != 6 || displayed.getRowCount() == 0) {
            throw new IOException("Select an order slip with items before printing.");
        }
        SlipData slip = read(connection, slipId);
        // View Orders displays billed/total quantities. Preserve the displayed values exactly.
        slip.rows.clear();
        for (int row = 0; row < displayed.getRowCount(); row++) {
            String[] values = new String[6];
            for (int column = 0; column < values.length; column++) {
                Object value = displayed.getValueAt(row, column);
                values[column] = value == null ? "" : value.toString();
            }
            slip.rows.add(values);
        }
        return write(slip, database, dataFolder, "viewslip", "order_", false);
    }

    static Path write(SlipData slip, String database, Path dataFolder) throws IOException, DocumentException {
        return write(slip, database, dataFolder, "orderslips", "orderslip_", true);
    }

    private static Path write(SlipData slip, String database, Path dataFolder,
                              String subfolder, String prefix, boolean includeId) throws IOException, DocumentException {
        if (database == null || database.isBlank() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*") || database.endsWith(".") || database.endsWith(" ")
                || database.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IOException("The database name cannot be used as a Windows folder name.");
        }
        Path directory = dataFolder.toAbsolutePath().normalize().resolve(database).resolve(subfolder);
        Files.createDirectories(directory);
        String stamp = ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve(prefix + stamp + (includeId ? "_" + slip.id : "") + ".pdf");
        Path temporary = Files.createTempFile(directory, ".orderslip-", ".tmp");
        try {
            render(slip, destination.getFileName().toString(), temporary);
            Files.move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void render(SlipData slip, String title, Path target) throws IOException, DocumentException {
        Path font = Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "mangal.ttf");
        if (!Files.isRegularFile(font)) throw new IOException("Mangal font was not found in Windows Fonts.");
        LayoutProcessor.enable();
        BaseFont base = BaseFont.createFont(font.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font body = new Font(base, 9);
        Font heading = new Font(base, 18, Font.BOLD, Color.BLUE);
        Font bold = new Font(base, 9, Font.BOLD);
        Font small = new Font(base, 8, Font.NORMAL, Color.GRAY);
        Document document = new Document(PageSize.A4, 30, 30, 40, 45);
        try (OutputStream output = Files.newOutputStream(target)) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                            new Phrase("Order slip " + slip.id + " | " + value(slip.customer), small), d.left(), 24, 0);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Page " + w.getPageNumber(), small), d.right(), 24, 0);
                }
            });
            document.addTitle(title); // Browser helper identifies this exact receipt by its unique filename.
            document.open();
            try {
                Paragraph name = new Paragraph("Gurukripa Jewellers", heading);
                name.setSpacingAfter(25);
                document.add(name);
                PdfPTable details = new PdfPTable(new float[]{1, 1, 2});
                details.setWidthPercentage(100);
                details.addCell(cell("Order slip: " + slip.id, bold));
                details.addCell(cell(slip.date, body));
                details.addCell(cell(slip.customer, body));
                PdfPCell type = cell("Slip type: " + value(slip.type), body);
                type.setColspan(3);
                details.addCell(type);
                details.setSpacingAfter(12);
                document.add(details);
                PdfPTable table = new PdfPTable(new float[]{1, 1.7f, .85f, .8f, 1, 2.1f});
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                table.setSplitRows(true);
                table.setSplitLate(false);
                for (String column : new String[]{"Design ID", "Item Name", "Quantity", "Plating", "Raw Material Cost", "Other Details"}) {
                    PdfPCell header = cell(column, bold);
                    header.setBackgroundColor(new Color(235, 239, 243));
                    table.addCell(header);
                }
                for (String[] row : slip.rows) for (String text : row) table.addCell(cell(text, body));
                document.add(table);
            } finally {
                document.close();
            }
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text), font));
        cell.setPadding(6);
        cell.setLeading(0, 1.25f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        cell.setBorderColor(new Color(180, 185, 190));
        return cell;
    }

    private static String value(String text) { return text == null ? "" : text; }

    static final class SlipData {
        int id;
        String date, customer, type;
        final List<String[]> rows = new ArrayList<>();
    }
}
