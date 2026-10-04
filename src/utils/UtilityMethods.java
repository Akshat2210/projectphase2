package utils;

import mainpack.MyClass;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.Color;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.print.*;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UtilityMethods {
    public static final int VERTI_SPLIT = 0;
    public static final int HORIZONTAL_SPLIT = 1;
    public static final Random RANDOM = new Random();
    public static Thread printingThread;


    public static String parseString(Object object) {
        return Objects.toString(object, "");
    }

    public static int parseInt(Object object) {
        String string = Objects.toString(object, "");
        string = string.trim().isEmpty() ? "0" : string;
        return Integer.parseInt(string);
    }
    public static void saveBillAsPdf(
            javax.swing.table.DefaultTableModel model,
            int billID,
            java.sql.Date date,
            String customerName,
            int type,
            String location
    ) throws java.io.IOException, com.lowagie.text.DocumentException {

        java.io.File pdfFile = new java.io.File(location);
        java.io.File parentFolder = pdfFile.getParentFile();

        if (parentFolder != null && !parentFolder.exists() && !parentFolder.mkdirs()) {
            throw new java.io.IOException("Could not create PDF folder");
        }

        com.lowagie.text.Document document = new com.lowagie.text.Document(
                com.lowagie.text.PageSize.A4,
                20,
                20,
                40,
                40
        );

        com.lowagie.text.pdf.PdfWriter.getInstance(
                document,
                new java.io.FileOutputStream(pdfFile)
        );

        document.open();

        com.lowagie.text.Font titleFont = new com.lowagie.text.Font(
                com.lowagie.text.Font.HELVETICA,
                18,
                com.lowagie.text.Font.BOLD,
                java.awt.Color.BLUE
        );

        com.lowagie.text.Font normalFont = new com.lowagie.text.Font(
                com.lowagie.text.Font.HELVETICA,
                12,
                com.lowagie.text.Font.NORMAL,
                java.awt.Color.BLACK
        );

        com.lowagie.text.Paragraph title =
                new com.lowagie.text.Paragraph("Gurukripa Jewellers", titleFont);

        title.setSpacingAfter(40);
        document.add(title);

        int billTruncatedId = billID % 100 == 0 ? 100 : billID % 100;

        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd-MM-yyyy");

        com.lowagie.text.pdf.PdfPTable billInfo =
                new com.lowagie.text.pdf.PdfPTable(new float[]{1.0f, 1.5f, 2.0f});

        billInfo.setWidthPercentage(100);
        billInfo.getDefaultCell().setBorder(com.lowagie.text.Rectangle.NO_BORDER);

        billInfo.addCell(new com.lowagie.text.Phrase(
                String.valueOf(billTruncatedId),
                normalFont
        ));

        billInfo.addCell(new com.lowagie.text.Phrase(
                sdf.format(date),
                normalFont
        ));

        billInfo.addCell(new com.lowagie.text.Phrase(
                customerName == null ? "" : customerName,
                normalFont
        ));

        document.add(billInfo);

        com.lowagie.text.pdf.PdfPTable divider =
                new com.lowagie.text.pdf.PdfPTable(1);

        divider.setWidthPercentage(100);

        com.lowagie.text.pdf.PdfPCell dividerCell =
                new com.lowagie.text.pdf.PdfPCell();

        dividerCell.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        dividerCell.setBorderWidthBottom(2f);
        dividerCell.setFixedHeight(12f);

        divider.addCell(dividerCell);
        document.add(divider);

        int columnCount = model.getColumnCount();

        if (columnCount == 0) {
            document.close();
            return;
        }

        float[] columnWidths = new float[columnCount];

        com.lowagie.text.pdf.BaseFont baseFont =
                com.lowagie.text.pdf.BaseFont.createFont(
                        com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.WINANSI,
                        com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED
                );

        int firstDynamicColumn = type == CONSTANTS.CUSTOMER_BILL ? 1 : 0;

        if (type == CONSTANTS.CUSTOMER_BILL) {
            columnWidths[0] = 30f;
        }

        float totalWidth = 0f;

        for (int column = firstDynamicColumn; column < columnCount; column++) {
            float widestText = baseFont.getWidthPoint(
                    model.getColumnName(column),
                    12f
            );

            for (int row = 0; row < model.getRowCount(); row++) {
                Object value = model.getValueAt(row, column);

                if (value != null) {
                    widestText = Math.max(
                            widestText,
                            baseFont.getWidthPoint(value.toString(), 12f)
                    );
                }
            }

            columnWidths[column] = widestText + 10f;
        }

        for (float width : columnWidths) {
            totalWidth += width;
        }

        float availableWidth = document.right() - document.left();

        if (totalWidth > availableWidth) {
            float scale = availableWidth / totalWidth;

            for (int column = 0; column < columnCount; column++) {
                columnWidths[column] *= scale;
            }
        }

        com.lowagie.text.pdf.PdfPTable billTable =
                new com.lowagie.text.pdf.PdfPTable(columnWidths);

        billTable.setWidthPercentage(100);
        billTable.setSpacingBefore(10);

        for (int row = 0; row < model.getRowCount(); row++) {
            for (int column = 0; column < columnCount; column++) {
                Object value = model.getValueAt(row, column);

                com.lowagie.text.pdf.PdfPCell cell =
                        new com.lowagie.text.pdf.PdfPCell(
                                new com.lowagie.text.Phrase(
                                        value == null ? "" : value.toString(),
                                        normalFont
                                )
                        );

                cell.setPadding(5f);
                cell.setMinimumHeight(20f);
                cell.setNoWrap(true);

                billTable.addCell(cell);
            }
        }

        document.add(billTable);
        document.close();
    }
    public static double parseDouble(Object object) {
        String string = Objects.toString(object, "");
        string = string.trim().isEmpty() ? "0" : string;
        return Double.parseDouble(string);
    }

    public static Connection createConnection() throws SQLException {

        return DriverManager.getConnection(MyClass.login.getUrl(), MyClass.login.getLoginID(), MyClass.login.getPassword());
    }


    public static final BlockingQueue<Runnable> printQueue = new LinkedBlockingQueue<>();

    public static void storeLogs(Exception e) {
        try {
            // Generate timestamp for filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            // Define logs directory path inside src
            String logsDirPath = "src/logs";
            File logsDir = new File(logsDirPath);
            if (!logsDir.exists()) {
                logsDir.mkdirs(); // Create the folder if it doesn't exist
            }

            // Define log file path
            String filename = timestamp + "_error_logs.txt";
            File logFile = new File(logsDir, filename);

            // Write the exception stack trace to the file
            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile))) {
                pw.println("Exception occurred at: " + timestamp);
                e.printStackTrace(pw);
                System.out.println("Log saved to " + logFile.getAbsolutePath());
            }

        } catch (IOException ioEx) {
            System.err.println("Failed to save logs: " + ioEx.getMessage());
        }
    }

    public static double[] balance(int billID) {
        double lastbillTotal = 0;
        String openingBalanceQuery = "select openingaccount from customers where customer_name=(select customer_name from bills where billid=?)";
        String billQuery = "select billid from bills where customer_name=(select customer_name from bills where billid=?) and date <= (select date from bills where billid=?) and billid<?";
//        String transactionQuery="Select *from transactions where date<=()"
        String transactionQuery = "SELECT sum(amount) FROM transactions " +
                "WHERE customer_name = (SELECT customer_name FROM bills WHERE billid = ?) " +
                "AND (date <=(SELECT date FROM bills WHERE billid = ?) and (billid<?))";
        double grandTotalBill = 0;//contains total till previous bills
        try (Connection con = MyClass.createConnection();
             PreparedStatement billStatement = con.prepareStatement(billQuery); PreparedStatement transacStatement = con.prepareStatement(transactionQuery)) {

            ArrayList<Integer> billIDList = new ArrayList<>();
            billStatement.setInt(1, billID);
            billStatement.setInt(2, billID);
            billStatement.setInt(3, billID);
            StringBuilder placeholders = new StringBuilder();
            try (ResultSet rs = billStatement.executeQuery();
            ) {
                while (rs.next()) {
                    billIDList.add(rs.getInt(1));
                    placeholders.append(rs.getInt(1)).append(",");
                }
            }
            placeholders.append(billID);

            billQuery = "SELECT " +
                    "SUM((" +
                    "(IFNULL(LabourCost, 0) + " +
                    "IFNULL(DullChillaiCost, 0) + " +
                    "IFNULL(MeenaColorMeenaCost, 0) + " +
                    "IFNULL(RhodiumCost, 0) + " +
                    "IFNULL(NagSettingCost, 0) + " +
                    "IFNULL(OtherBaseCosts, 0) + " +
                    "IFNULL(RawCost, 0)) * IFNULL(quantity, 0)" +
                    ") + (IFNULL(GoldRate, 0) * IFNULL(GoldPlatingWeight, 0))) AS total, " +
                    "SUM(TotalFinalCost),billid " +
                    "FROM billdetails " +
                    "WHERE billid in ( " + placeholders + ")" +
                    "GROUP BY billid;";
            System.out.println("placeholders are" + placeholders);

            if (!placeholders.isEmpty()) {
                try (PreparedStatement stmt = con.prepareStatement(billQuery);
                     ResultSet rs = stmt.executeQuery())
//            statement.setInt(1, billID);
                {
                    lastbillTotal = 0;
                    while (rs.next()) {
                        if (rs.getInt(3) == billID) lastbillTotal += rs.getDouble(1);
                        grandTotalBill += rs.getDouble(1);
//                    System.out.println(rs.getDouble(1) + "   from sql" + rs.getDouble(2));
                    }
                }
                System.out.println(grandTotalBill + " value of grand total bill");

            }
            transacStatement.setInt(1, billID);
            transacStatement.setInt(2, billID);
            transacStatement.setInt(3, billID);

            double transactionSum = 0;
            try (ResultSet rs = transacStatement.executeQuery();) {
                if (rs.next()) {
                    transactionSum = rs.getDouble(1);
                    System.out.println("transaction sum is " + rs.getInt(1));
                }
            }
            System.out.println("\n\n\ntotal is " + grandTotalBill);
            double[] result = new double[2];
            try (PreparedStatement openingBalanceStatement = con.prepareStatement(openingBalanceQuery)) {
                openingBalanceStatement.setInt(1, billID);
                double openingBalance = 0;
                try (ResultSet rs = openingBalanceStatement.executeQuery();) {
                    if (rs.next()) openingBalance = rs.getDouble(1);
                }
                result[0] = openingBalance + grandTotalBill - lastbillTotal - transactionSum;
                result[1] = openingBalance + grandTotalBill - transactionSum;

            }
            return result;

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public static void writeTableToExcel(JTable table, String filename) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Table Data");
        TableModel model = table.getModel();

        // Write column headers
        Row headerRow = sheet.createRow(0);
        for (int col = 0; col < model.getColumnCount(); col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(model.getColumnName(col));
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }

        // Write table data
        for (int row = 0; row < model.getRowCount(); row++) {
            Row excelRow = sheet.createRow(row + 1);
            for (int col = 0; col < model.getColumnCount(); col++) {
                Cell cell = excelRow.createCell(col);
                Object value = model.getValueAt(row, col);
                if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        // Auto-size columns for better readability
        for (int col = 0; col < model.getColumnCount(); col++) {
            sheet.autoSizeColumn(col);
        }

        // Write to file
        try (FileOutputStream fileOut = new FileOutputStream(filename)) {
            workbook.write(fileOut);
            System.out.println("Excel file saved as: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close the workbook
        try {
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void csvOut(TableModel tableModel) {
        String fileName = "output.csv";
        try (FileWriter writer = new FileWriter(fileName)) {
            int columnCount = tableModel.getColumnCount();
            int rowCount = tableModel.getRowCount();

            // Writing headers
            for (int col = 0; col < columnCount; col++) {
                writer.append(tableModel.getColumnName(col));
                if (col < columnCount - 1) {
                    writer.append(",");
                }
            }
            writer.append("\n");

            // Writing rows
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    Object value = tableModel.getValueAt(row, col);
                    writer.append(value != null ? value.toString() : "");
                    if (col < columnCount - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }
            writer.flush();
            System.out.println("CSV file generated successfully: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void csvToTableModel(DefaultTableModel tableModel, String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean isHeader = tableModel.getColumnCount() == 0; // Only set headers if model is empty
            TableModelListener[] tl = tableModel.getTableModelListeners();
            for (TableModelListener listener : tl) {
                tableModel.removeTableModelListener(listener);
            }
            tableModel.setRowCount(0);
            int i = 0;
            while ((line = br.readLine()) != null) {
                if (i == 0) {
                    i++;
                    continue;
                }
                String[] values = line.split(",", -1); // Split by comma, preserve empty values

                if (isHeader) {
                    for (String columnName : values) {
                        System.out.println("header");
                        tableModel.addColumn(columnName.trim());
                    }
                    isHeader = false;
                } else {
                    // Add row data

                    Vector<String> row = new Vector<>();
                    for (String value : values) {
                        row.add(value.trim().isEmpty() ? "" : value.trim());
                    }

                    tableModel.addRow(row);

                }
            }
            for (TableModelListener listener : tl) {
                tableModel.addTableModelListener(listener);
            }
            for (Vector row : tableModel.getDataVector()) {
                for (Object data : row) {
                    System.out.print(data + "  ");
                }
                System.out.println();
            }
            System.out.println("success");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Data to be written
        String[][] foodData = {
                {"Item No.", "Item Name", "Quantity", "Unit Price", "Total Price"},
                {"1", "Burger", "2", "5.99", "11.98"},
                {"2", "Fries", "1", "2.99", "2.99"},
                {"3", "Soda", "2", "1.49", "2.98"},
                {"4", "Pizza", "1", "12.99", "12.99"},
                {"5", "Ice Cream", "3", "3.49", "10.47"},
                {"", "Total", "", "", "41.41"}
        };

        // Create a new Excel workbook
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Food Bill");

        // Write data to the sheet
        for (int i = 0; i < foodData.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < foodData[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(foodData[i][j]);
            }
        }

        // Save the Excel file
        try (FileOutputStream fileOut = new FileOutputStream("FoodBill.xlsx")) {
            workbook.write(fileOut);
            System.out.println("Excel file 'FoodBill.xlsx' created successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close the workbook
        try {
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static LocalDateTime parseDate(String dateStr) {
        if (dateStr.isEmpty()) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy");
        LocalDate inputDate = LocalDate.parse(dateStr, formatter);
        LocalDate today = LocalDate.now();

        if (inputDate.equals(today)) {
            return LocalDateTime.now();
        } else {
            return LocalDateTime.of(inputDate, LocalTime.of(13, 0)); // 1 PM
        }
    }

    public static void generateAndAddDates(JComboBox<String> comboBox, boolean headerrow) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy");

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1);

        // Clear any existing items
        if (comboBox != null) {
            comboBox.removeAllItems();

            // Add dates from today to 1 year ago
            if (headerrow) comboBox.addItem("Select date");
            for (LocalDate date = today; !date.isBefore(oneYearAgo); date = date.minusDays(1)) {
                comboBox.addItem(date.format(formatter));
            }
        } else Thread.dumpStack();
    }

    public static void printStartUp() {
        PrinterJob.getPrinterJob(); // triggers internal loading
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        ge.getAllFonts(); // forces font rasterizer to load
    }

    public static void generateAndAddNames(JComboBox<String> comboBox) {
        if (comboBox == null) return;
        comboBox.removeAllItems();
        comboBox.addItem("Select Customer");
        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT customer_name FROM customers");) {
                while (rs.next()) comboBox.addItem(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static DecimalDocumentFilter getDocFilter() {
        return new DecimalDocumentFilter();
    }

    public static TableModelListener[] removeModelListener(DefaultTableModel tableModel) {

        TableModelListener[] listeners = tableModel.getTableModelListeners();
        for (TableModelListener listener : listeners) {


            tableModel.removeTableModelListener(listener);
        }
        return listeners;
    }

    public static void addModelListeners(TableModelListener[] listeners, DefaultTableModel model) {
        for (TableModelListener listener : listeners) {
            model.addTableModelListener(listener);
        }
    }

    public static void printWithDefaultSettings(DefaultTableModel model, int billID, Date date, String customerName, int type) {
        PrinterJob job = PrinterJob.getPrinterJob();

        Printable printable = new Printable() {
            @Override
            public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
                if (pageIndex > 0) return NO_SUCH_PAGE;

                Graphics2D g2d = (Graphics2D) g;
                g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

                int x = 0;
                int y = 0;
                int rowHeight = 20;
                int padding = 5;

                double printableWidth = pageFormat.getImageableWidth();
                int colCount = model.getColumnCount();
                int[] colWidths = new int[colCount];

                // Font setup
                java.awt.Font headerFont = new java.awt.Font("Arial", java.awt.Font.BOLD, 12);
                java.awt.Font normalFont = new java.awt.Font("Arial", java.awt.Font.PLAIN, 12);
                g.setFont(normalFont);
                FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);

                // Fixed width for "SNo"
                int init;
                if (type == CONSTANTS.CUSTOMER_BILL) {
                    colWidths[0] = 30;
                    init = 1;
                } else {
                    init = 0;
                }
                int usedWidth = colWidths[0];

                // Estimate width for other columns
                for (int col = init; col < colCount; col++) {
                    int maxWidth = (int) headerFont.getStringBounds(model.getColumnName(col), frc).getWidth();
                    for (int row = 0; row < model.getRowCount(); row++) {
                        Object val = model.getValueAt(row, col);
                        if (val != null) {
                            int width = (int) normalFont.getStringBounds(val.toString(), frc).getWidth();
                            if (width > maxWidth) maxWidth = width;
                        }
                    }
                    colWidths[col] = maxWidth + padding * 2;
                    usedWidth += colWidths[col];
                }

                // Scale down if total width > printable width
                if (usedWidth > printableWidth) {
                    double scale = printableWidth / usedWidth;
                    for (int i = 0; i < colCount; i++) {
                        colWidths[i] = (int) (colWidths[i] * scale);
                    }
                }

                // Draw title
                g.setColor(java.awt.Color.BLUE);
                g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
                g.drawString("Gurukripa Jewellers", x + 10, y + 20);
                y += 70;
                g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
                g.setColor(java.awt.Color.BLACK);
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                int billtruncatedid = billID % 100 == 0 ? 100 : billID % 100;
                g.drawString("" + billtruncatedid, x + 10, y);
                g.drawString(customerName, x + 300, y);
                g.drawString(sdf.format(date), x + 150, y);
                y += 20;

                g.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(2));
                g.drawLine(x, y, x + (int) printableWidth, y);
                y += 10;

                int colX = x;
                // Column headers
//                g.setFont(headerFont);
//                for (int col = 0; col < colCount; col++) {
//                    g.drawRect(colX, y, colWidths[col], rowHeight);
//                    g.drawString(model.getColumnName(col), colX + padding, y + 15);
//                    colX += colWidths[col];
//                }
//                y += rowHeight;

                // Table rows
                g.setFont(normalFont);
                for (int row = 0; row < model.getRowCount(); row++) {
                    colX = x;
                    for (int col = 0; col < colCount; col++) {
                        g.drawRect(colX, y, colWidths[col], rowHeight);
                        Object val = model.getValueAt(row, col);
                        if (val != null) {
                            g.drawString(val.toString(), colX + padding, y + 15);
                        }
                        colX += colWidths[col];
                    }
                    y += rowHeight;

                    if (y + rowHeight > pageFormat.getImageableHeight()) {
                        return NO_SUCH_PAGE; // Page break not handled yet
                    }
                }

                return PAGE_EXISTS;
            }
        };

        // Define ISO A4 paper
        PageFormat format = job.defaultPage();
        Paper paper = new Paper();
        paper.setSize(595.0, 842.0); // A4 in points
//        paper.setImageableArea(40, 40, 515, 762); // Margins: 40pt
        paper.setImageableArea(20, 40, 555, 762);

        format.setPaper(paper);
        job.setPrintable(printable, format);

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                e.printStackTrace();
            }
        }
    }

    public static void splitFrame(JFrame topLeft, JFrame bottomRight, int splitType) {
        // Screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        // Position and size the frames
        if (splitType == HORIZONTAL_SPLIT) {
            topLeft.setBounds(0, 0, screenWidth, screenHeight / 2);
            bottomRight.setBounds(0, screenHeight / 2, screenWidth, screenHeight / 2);
            return;
        }
        if (splitType == VERTI_SPLIT) {
            topLeft.setBounds(-6, 0, (652), 678);
            bottomRight.setBounds(634, 0,  652 , 678);
        }

    }

    public static String parseDateString(Date date) {
        return date.toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yy"));
    }

    public static LocalDateTime getDate(Object selectedItem) {
        if (selectedItem == null || selectedItem.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Date value is missing or invalid.");
        }

        String dateString = selectedItem.toString().trim();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");

        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateString + " 00:00:00", formatter);

            if (dateTime.toLocalDate().equals(LocalDate.now())) {
                return LocalDateTime.now(); // current system date & time
            } else {
                return dateTime; // parsed date with 00:00:00 time
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date format should be dd-MM-yy", e);
        }
    }

    public static Process pBuilder(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);

        try {
            Process p = processBuilder.start();
            return p;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

class DecimalDocumentFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        String newText = getUpdatedText(fb, offset, 0, string);
        if (isValidInput(newText)) {
            super.insertString(fb, offset, string, attr);
        } else {
            Toolkit.getDefaultToolkit().beep(); // Invalid input feedback
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        String newText = getUpdatedText(fb, offset, length, text);
        if (isValidInput(newText)) {
            super.replace(fb, offset, length, text, attrs);
        } else {
            Toolkit.getDefaultToolkit().beep(); // Invalid input feedback
        }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        super.remove(fb, offset, length); // Allow deletion
    }

    private String getUpdatedText(FilterBypass fb, int offset, int length, String text) throws BadLocationException {
        String existingText = fb.getDocument().getText(0, fb.getDocument().getLength());
        StringBuilder newText = new StringBuilder(existingText);
        newText.replace(offset, offset + length, text);
        return newText.toString();
    }

    private boolean isValidInput(String text) {
        // Regex to allow only numeric values with optional decimal (max 3 decimal places)
        return text.matches("\\d*(\\.\\d{0,3})?");
    }
}

