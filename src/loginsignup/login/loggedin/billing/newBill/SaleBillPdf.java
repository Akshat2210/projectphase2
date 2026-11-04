package loginsignup.login.loggedin.billing.newBill;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import mainpack.MyClass;
import utils.DBStructure;

import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Saves a sale bill using the existing bill layout, with order-slip-style file handling. */
final class SaleBillPdf {
    private SaleBillPdf() { }

    static Path save(DefaultTableModel model, int billId, java.sql.Date date, String customerName,
                     String database, Path dataFolder) throws Exception {
        validateFolderName(database);
        Path directory = dataFolder.toAbsolutePath().normalize().resolve(database).resolve("salebills");
        Files.createDirectories(directory);
        String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve(billId + "_" + timestamp + ".pdf");
        Path temporary = Files.createTempFile(directory, ".salebill-", ".tmp");
        try {
            render(model, billId, date, customerName, temporary);
            Files.move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateFolderName(String database) throws IOException {
        if (database == null || database.isBlank() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*") || database.endsWith(" ") || database.endsWith(".")) {
            throw new IOException("The database name cannot be used as a Windows folder name.");
        }
    }

    private static void render(DefaultTableModel model, int billId, java.sql.Date date, String customerName,
                               Path target) throws Exception {
        Document document = new Document(PageSize.A4, 20, 20, 40, 40);
        try (OutputStream output = Files.newOutputStream(target)) {
            PdfWriter.getInstance(document, output);
            document.open();
            try {
                Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.BLUE);
                Path fontPath = java.nio.file.Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"),
                        "Fonts", "mangal.ttf");
                if (!Files.isRegularFile(fontPath)) {
                    throw new IOException("Mangal font was not found in Windows Fonts.");
                }
                BaseFont baseFont = BaseFont.createFont(fontPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                Font normalFont = new Font(baseFont, 12, Font.NORMAL, Color.BLACK);
                Paragraph title = new Paragraph("Gurukripa Jewellers", titleFont);
                title.setSpacingAfter(40);
                document.add(title);

                PdfPTable billInfo = new PdfPTable(new float[]{1.0f, 1.5f, 2.0f});
                billInfo.setWidthPercentage(100);
                billInfo.getDefaultCell().setBorder(Rectangle.NO_BORDER);
                billInfo.addCell(new Phrase(String.valueOf(fetchBillNumber(billId)), normalFont));
                billInfo.addCell(new Phrase(new java.text.SimpleDateFormat("dd-MM-yyyy").format(date), normalFont));
                billInfo.addCell(new Phrase(customerName == null ? "" : customerName, normalFont));
                document.add(billInfo);

                PdfPTable divider = new PdfPTable(1);
                divider.setWidthPercentage(100);
                PdfPCell dividerCell = new PdfPCell();
                dividerCell.setBorder(Rectangle.BOTTOM);
                dividerCell.setBorderWidthBottom(2f);
                dividerCell.setFixedHeight(12f);
                divider.addCell(dividerCell);
                document.add(divider);

                int columnCount = model.getColumnCount();
                if (columnCount == 0) return;
                float[] columnWidths = new float[columnCount];
                columnWidths[0] = 30f;
                float totalWidth = 0f;
                for (int column = 1; column < columnCount; column++) {
                    float widestText = baseFont.getWidthPoint(model.getColumnName(column), 12f);
                    for (int row = 0; row < model.getRowCount(); row++) {
                        Object value = model.getValueAt(row, column);
                        if (value != null) widestText = Math.max(widestText, baseFont.getWidthPoint(value.toString(), 12f));
                    }
                    columnWidths[column] = widestText + 10f;
                }
                for (float width : columnWidths) totalWidth += width;
                float availableWidth = document.right() - document.left();
                if (totalWidth > availableWidth) {
                    float scale = availableWidth / totalWidth;
                    for (int column = 0; column < columnCount; column++) columnWidths[column] *= scale;
                }

                PdfPTable billTable = new PdfPTable(columnWidths);
                billTable.setWidthPercentage(100);
                billTable.setSpacingBefore(10);
                for (int row = 0; row < model.getRowCount(); row++) {
                    for (int column = 0; column < columnCount; column++) {
                        Object value = model.getValueAt(row, column);
                        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value.toString(), normalFont));
                        cell.setPadding(5f);
                        cell.setMinimumHeight(20f);
                        cell.setNoWrap(true);
                        billTable.addCell(cell);
                    }
                }
                document.add(billTable);
            } finally {
                document.close();
            }
        }
    }

    private static int fetchBillNumber(int billId) throws SQLException {
        String sql = "SELECT " + DBStructure.BILLS_BILL_NUMBER + " FROM " + DBStructure.BILLS_TABLE
                + " WHERE " + DBStructure.BILLS_BILL_ID + " = ?";
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, billId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    int billNumber = result.getInt(DBStructure.BILLS_BILL_NUMBER);
                    if (!result.wasNull()) return billNumber;
                }
            }
        }
        throw new SQLException("No bill number found for BillID: " + billId);
    }
}
