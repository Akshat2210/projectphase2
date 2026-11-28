package loginsignup.login.loggedin.billing.newBill;

import com.lowagie.text.Document;
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import mainpack.MyClass;
import utils.DBStructure;

import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Saves customer and detailed office copies together, without changing saved billing data. */
public final class SaleBillPdf {
    private SaleBillPdf() { }

    public static Path save(DefaultTableModel model, int billId, java.sql.Date date, String customerName,
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
        PrintDetails details = fetchPrintDetails(billId);
        Document document = new Document(PageSize.A4, 20, 20, 40, 40);
        try (OutputStream output = Files.newOutputStream(target)) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            PageNumbers pageNumbers = new PageNumbers();
            writer.setPageEvent(pageNumbers);
            document.open();
            try {
                float fontScale = utils.AppSettings.getBillingPdfFontScale();
                Font titleFont = new Font(Font.HELVETICA, 18 * fontScale, Font.BOLD, Color.BLUE);
                Path fontPath = java.nio.file.Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"),
                        "Fonts", "mangal.ttf");
                if (!Files.isRegularFile(fontPath)) {
                    throw new IOException("Mangal font was not found in Windows Fonts.");
                }
                LayoutProcessor.enable();
                BaseFont baseFont = BaseFont.createFont(fontPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                Font normalFont = new Font(baseFont, 9 * fontScale, Font.NORMAL, Color.BLACK);
                Font detailFont = new Font(baseFont, 6 * fontScale, Font.NORMAL, Color.DARK_GRAY);
                renderCopy(document, model, details, date, customerName, titleFont, normalFont, detailFont, false);
                document.newPage();
                pageNumbers.startOfficeCopy(writer);
                renderCopy(document, model, details, date, customerName, titleFont, normalFont, detailFont, true);
            } finally {
                document.close();
            }
        }
    }

    private static void renderCopy(Document document, DefaultTableModel model, PrintDetails details,
                                   java.sql.Date date, String customerName, Font titleFont,
                                   Font normalFont, Font detailFont, boolean officeCopy) throws Exception {
        BaseFont baseFont = normalFont.getBaseFont();
        Paragraph copyLabel = new Paragraph(officeCopy ? "OFFICE COPY - COST BREAKDOWN" : "CUSTOMER COPY", detailFont);
        copyLabel.setSpacingAfter(8);
        document.add(copyLabel);

        PdfPTable billInfo = new PdfPTable(new float[]{1.0f, 1.5f, 2.0f});
        billInfo.setWidthPercentage(100);
        billInfo.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        billInfo.addCell(new Phrase(String.valueOf(details.billNumber), normalFont));
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
        float totalWidth = 0f;
        for (int column = 0; column < columnCount; column++) {
            float widestText = baseFont.getWidthPoint(model.getColumnName(column), normalFont.getSize());
            for (int row = 0; row < model.getRowCount(); row++) {
                // Long receipt/balance labels must not squeeze the monetary columns.
                if (itemSerial(model.getValueAt(row, 0)) == null && column < columnCount - 1) continue;
                Object value = model.getValueAt(row, column);
                if (value != null) widestText = Math.max(widestText, baseFont.getWidthPoint(printedCellValue(model, row, column), normalFont.getSize()));
            }
            columnWidths[column] = widestText + 10f;
        }
        for (float width : columnWidths) totalWidth += width;
        float availableWidth = document.right() - document.left();
        // Wrap the item name first, preserving room for serials, quantities and money.
        if (totalWidth > availableWidth && columnCount > 1) {
            float itemWidth = Math.max(60f, columnWidths[1] - (totalWidth - availableWidth));
            totalWidth += itemWidth - columnWidths[1];
            columnWidths[1] = itemWidth;
        }
        if (totalWidth > availableWidth) {
            float scale = availableWidth / totalWidth;
            for (int column = 0; column < columnCount; column++) columnWidths[column] *= scale;
        }

        PdfPTable billTable = new PdfPTable(columnWidths);
        billTable.setWidthPercentage(100);
        billTable.setSpacingBefore(10);
        billTable.setSplitRows(true);
        billTable.setSplitLate(false);
        boolean transactionsPrinted = false;
        for (int row = 0; row < model.getRowCount(); row++) {
            if (columnCount > 1 && itemSerial(model.getValueAt(row, 0)) == null) {
                if (!transactionsPrinted) {
                    PdfPCell block = new PdfPCell(transactionBlock(model, normalFont, columnWidths));
                    block.setColspan(columnCount);
                    block.setPadding(0);
                    block.setBorder(Rectangle.BOX);
                    billTable.addCell(block);
                    transactionsPrinted = true;
                }
                continue;
            }
            for (int column = 0; column < columnCount; column++) {
                Object value = model.getValueAt(row, column);
                PdfPCell cell = new PdfPCell(new Phrase(printedCellValue(model, row, column), normalFont));
                cell.setPadding(5f);
                cell.setMinimumHeight(20f);
                cell.setLeading(0, 1.25f);
                cell.setUseAscender(true);
                cell.setUseDescender(true);
                billTable.addCell(cell);
            }
        }
        if (officeCopy && columnCount > 1) {
            // Retain the customer's layout text so row heights AND page splits stay identical.
            for (int row = 0; row < model.getRowCount(); row++) {
                Integer serial = itemSerial(model.getValueAt(row, 0));
                if (serial == null) continue;
                ItemCosts costs = details.items.get(serial);
                if (costs == null) throw new IOException("Saved cost details not found for item " + serial + ".");
                Object value = model.getValueAt(row, 1);
                String name = value == null ? "" : value.toString();
                billTable.getRow(row).getCells()[1].setCellEvent((cell, area, canvases) -> {
                    drawOfficeItem(name, costs, normalFont, detailFont, area, canvases);
                });
            }
        }
        document.add(billTable);
    }

    private static String summaryLabel(DefaultTableModel model, int row) {
        StringJoiner label = new StringJoiner(" ");
        for (int column = 0; column < model.getColumnCount() - 1; column++) {
            Object value = model.getValueAt(row, column);
            if (value != null && !value.toString().isBlank()) label.add(value.toString());
        }
        return label.toString();
    }

    private static boolean isTransactionSummary(String label) {
        return label.equalsIgnoreCase("totalRecieved") || label.equalsIgnoreCase("Direct journal total")
                || label.startsWith("Direct journal/") || label.matches("\\d+/\\d{2}-\\d{2}-\\d{2}.*");
    }

    private static PdfPTable transactionBlock(DefaultTableModel model, Font font, float[] billWidths) {
        PdfPTable entries = new PdfPTable(new float[]{75, 25});
        entries.setWidthPercentage(100);
        BigDecimal total = BigDecimal.ZERO;
        int entryCount = 0;
        String singleEntryLabel = null;
        BigDecimal singleEntryAmount = null;
        for (int row = 0; row < model.getRowCount(); row++) {
            if (itemSerial(model.getValueAt(row, 0)) != null) continue;
            String label = summaryLabel(model, row);
            if (!isTransactionSummary(label) || label.equalsIgnoreCase("totalRecieved")
                    || label.equalsIgnoreCase("Direct journal total")) continue;
            Object value = model.getValueAt(row, model.getColumnCount() - 1);
            if (isZeroAmount(value)) continue;
            BigDecimal amount = new BigDecimal(value.toString().trim());
            // Journals store debit-minus-credit; receipts use the opposite sign.
            // Express both as received amounts so their displayed sum agrees with the balance.
            if (label.startsWith("Direct journal/")) amount = amount.negate();
            label = label.replaceFirst("^Direct journal/", "")
                    .replaceFirst("^\\d+/(?=\\d{2}-\\d{2}-\\d{2})", "");
            addSummaryTotal(entries, label, amount.setScale(2).toPlainString(), font);
            entryCount++;
            singleEntryLabel = label;
            singleEntryAmount = amount;
            total = total.add(amount);
        }
        float totalWidth = 0;
        for (float width : billWidths) totalWidth += width;
        float amountWidth = billWidths[billWidths.length - 1];
        PdfPTable totals = new PdfPTable(new float[]{Math.max(1f, totalWidth * 0.4f - amountWidth), amountWidth});
        totals.setWidthPercentage(100);
        for (int row = 0; row < model.getRowCount(); row++) {
            if (itemSerial(model.getValueAt(row, 0)) != null) continue;
            String label = summaryLabel(model, row);
            if (label.equalsIgnoreCase("totalRecieved")) {
                if (entryCount == 1) {
                    addSummaryTotal(totals, singleEntryLabel, singleEntryAmount.setScale(2).toPlainString(), font);
                } else if (entryCount > 1) {
                    addSummaryTotal(totals, "Total received:", total.setScale(2).toPlainString(), font);
                }
            } else if (!isTransactionSummary(label)) {
                Object value = model.getValueAt(row, model.getColumnCount() - 1);
                if (!label.isBlank() || (value != null && !value.toString().isBlank())) {
                    addSummaryTotal(totals, label, value, font);
                }
            }
        }
        PdfPTable block = new PdfPTable(new float[]{60, 40});
        block.setWidthPercentage(100);
        PdfPCell left = entryCount > 1 ? new PdfPCell(entries) : summaryCell("", font);
        if (entryCount > 1) left.setPadding(0);
        left.setBorder(Rectangle.RIGHT);
        block.addCell(left);
        PdfPCell right = new PdfPCell(totals);
        right.setPadding(0);
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_TOP);
        block.addCell(right);
        return block;
    }
    private static void addSummaryTotal(PdfPTable totals, String label, Object amount, Font font) {
        PdfPCell text = summaryCell(label, font);
        text.setBorder(Rectangle.NO_BORDER);
        text.setHorizontalAlignment(Element.ALIGN_LEFT);
        totals.addCell(text);
        PdfPCell value = summaryCell(printedNumber(amount), font);
        value.setBorder(Rectangle.NO_BORDER);
        // Same left inset as the bill's final amount column above.
        value.setHorizontalAlignment(Element.ALIGN_LEFT);
        totals.addCell(value);
    }
    private static boolean isZeroAmount(Object value) {
        if (value == null || value.toString().isBlank()) return true;
        try {
            return new BigDecimal(value.toString().trim()).signum() == 0;
        } catch (NumberFormatException invalid) {
            return false; // Never hide a value that could not be interpreted.
        }
    }
    private static String printedCellValue(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        // Bill columns: serial, item, quantity, plating grams, base amount, gold cost, total.
        // Summary text, item names and plating retain their original punctuation.
        boolean amount = itemSerial(model.getValueAt(row, 0)) == null
                ? column == model.getColumnCount() - 1 : column >= 2 && column != 3;
        return amount ? printedNumber(value) : value == null ? "" : value.toString();
    }

    private static String printedNumber(Object value) {
        String text = value == null ? "" : value.toString();
        return text.matches("[+-]?[0-9,]+\\.[0-9]+") ? text.replace('.', '=') : text;
    }
    private static PdfPCell summaryCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        cell.setPadding(5f);
        cell.setMinimumHeight(20f);
        cell.setLeading(0, 1.25f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        return cell;
    }

    private static final class PageNumbers extends PdfPageEventHelper {
        private final Font font = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        private PdfTemplate total;
        private int copyStartPage = 1;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(24, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + (writer.getPageNumber() - copyStartPage + 1) + " of ", font),
                    document.right() - 24, 20, 0);
            writer.getDirectContent().addTemplate(total, document.right() - 22, 20);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            writeTotal(writer.getPageNumber() - copyStartPage);
        }

        void startOfficeCopy(PdfWriter writer) {
            // newPage has finished the customer copy before this method is called.
            writeTotal(writer.getPageNumber() - copyStartPage);
            copyStartPage = writer.getPageNumber();
            total = writer.getDirectContent().createTemplate(24, 12);
        }

        private void writeTotal(int pages) {
            total.beginText();
            total.setFontAndSize(font.getCalculatedBaseFont(false), font.getSize());
            total.setColorFill(Color.GRAY);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(pages));
            total.endText();
        }
    }

    private static Integer itemSerial(Object value) {
        if (value == null || value.toString().isBlank()) return null; // Balance/receipt rows are not items.
        try {
            int serial = Integer.parseInt(value.toString().trim());
            return serial > 0 ? serial : null;
        } catch (NumberFormatException summaryRow) {
            return null;
        }
    }

    private static void drawOfficeItem(String name, ItemCosts costs, Font normalFont, Font detailFont,
                                       Rectangle area, com.lowagie.text.pdf.PdfContentByte[] canvases) {
        float left = area.getLeft() + 5, right = area.getRight() - 5;
        float bottom = area.getBottom() + 5, top = area.getTop() - 5;
        float low = 0f, high = 1f;
        Phrase fitted = null;
        // Find the largest readable size that fits inside the customer's existing row.
        for (int attempt = 0; attempt < 24; attempt++) {
            float scale = (low + high) / 2;
            Font nameFont = new Font(normalFont);
            nameFont.setSize(normalFont.getSize() * scale);
            Font costFont = new Font(detailFont);
            costFont.setSize(detailFont.getSize() * scale);
            Phrase text = new Phrase();
            text.add(new Chunk(name, nameFont));
            if (!costs.breakdown.isEmpty()) text.add(new Chunk("\n" + costs.breakdown, costFont));
            if (!costs.notes.isBlank()) text.add(new Chunk("\n" + costs.notes, costFont));
            ColumnText measure = officeTextColumn(null, text, left, bottom, right, top);
            try {
                if ((measure.go(true) & ColumnText.NO_MORE_TEXT) != 0) {
                    fitted = text;
                    low = scale;
                } else high = scale;
            } catch (com.lowagie.text.DocumentException failure) {
                throw new IllegalStateException("Could not measure office item text.", failure);
            }
        }
        if (fitted == null) throw new IllegalStateException("Office item cannot fit the customer row.");
        com.lowagie.text.pdf.PdfContentByte canvas = canvases[PdfPTable.TEXTCANVAS];
        // Cover only the original item text, leaving the existing grid and other columns intact.
        canvas.saveState();
        canvas.setColorFill(Color.WHITE);
        canvas.rectangle(area.getLeft() + 0.5f, area.getBottom() + 0.5f,
                area.getWidth() - 1, area.getHeight() - 1);
        canvas.fill();
        canvas.restoreState();
        try {
            officeTextColumn(canvas, fitted, left, bottom, right, top).go();
        } catch (com.lowagie.text.DocumentException failure) {
            throw new IllegalStateException("Could not draw office item text.", failure);
        }
    }

    private static ColumnText officeTextColumn(com.lowagie.text.pdf.PdfContentByte canvas, Phrase text,
                                               float left, float bottom, float right, float top) {
        ColumnText column = new ColumnText(canvas);
        column.setSimpleColumn(left, bottom, right, top);
        column.setLeading(0, 1.15f);
        column.setUseAscender(true);
        column.setText(text);
        return column;
    }

    private static PrintDetails fetchPrintDetails(int billId) throws SQLException {
        String sql = "SELECT " + DBStructure.BILLS_BILL_NUMBER + " FROM " + DBStructure.BILLS_TABLE
                + " WHERE " + DBStructure.BILLS_BILL_ID + " = ?";
        String itemSql = "SELECT SNo, LabourCost, RawCost, DullChillaiCost, MeenaColorMeenaCost, "
                + "RhodiumCost, NagSettingCost, OtherBaseCosts, OtherBaseCostNotes FROM billdetails WHERE BillID = ?";
        PrintDetails details = new PrintDetails();
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
            statement.setInt(1, billId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    int billNumber = result.getInt(DBStructure.BILLS_BILL_NUMBER);
                    if (result.wasNull()) throw new SQLException("No bill number found for BillID: " + billId);
                    details.billNumber = billNumber;
                } else {
                    throw new SQLException("No bill number found for BillID: " + billId);
                }
            }
            itemStatement.setInt(1, billId);
            try (ResultSet result = itemStatement.executeQuery()) {
                String[] columns = {"LabourCost", "RawCost", "DullChillaiCost", "MeenaColorMeenaCost",
                        "RhodiumCost", "NagSettingCost", "OtherBaseCosts"};
                String[] labels = {"L", "R", "DC", "M/CM", "Rh", "Nag", "O"};
                while (result.next()) {
                    StringJoiner breakdown = new StringJoiner("+");
                    for (int i = 0; i < columns.length; i++) {
                        BigDecimal cost = result.getBigDecimal(columns[i]);
                        if (cost != null && cost.signum() != 0) {
                            breakdown.add(labels[i] + printedNumber(cost.stripTrailingZeros().toPlainString()));
                        }
                    }
                    String notes = result.getString("OtherBaseCostNotes");
                    int serial = result.getInt("SNo");
                    if (details.items.put(serial, new ItemCosts(breakdown.toString(), notes == null ? "" : notes)) != null) {
                        throw new SQLException("Duplicate saved serial number " + serial + " in bill " + billId + ".");
                    }
                }
            }
        }
        return details;
    }

    private static final class PrintDetails {
        int billNumber;
        final Map<Integer, ItemCosts> items = new HashMap<>();
    }

    private record ItemCosts(String breakdown, String notes) { }
}
