package loginsignup.login.loggedin.processingmanagement.recieve;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;
import mainpack.MyClass;
import utils.DBStructure;
import utils.UtilityMethods;
import loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Recieve extends JFrame {
    private final ProcessingManagementScreen processingManagementScreen;
    private JPanel panel;
    private JButton backButton;
    private JComboBox<Long> lotNumber;
    private JComboBox<String> dateComboBox;
    private JButton prevButton;
    private JButton nextButton;
    private JScrollPane scrollPane;
    private JButton submitButton;
    private Long displayedLotId;
    private boolean receiveSaved;
    private boolean transactionUncertain;

    public Recieve(ProcessingManagementScreen processingManagementScreen) {
        this.processingManagementScreen = processingManagementScreen;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        scrollPane.setPreferredSize(new java.awt.Dimension(760, 480));
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        loadDates();
        loadLotNumbers();
        lotNumber.addActionListener(event -> loadSelectedLot());
        loadSelectedLot();
        submitButton.addActionListener(event -> insertData());
        pack();

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                processingManagementScreen.setVisible(true);
            }
        });
    }

    private void loadDates() {
        dateComboBox.setEditable(false);
        UtilityMethods.generateAndAddDates(dateComboBox, false);
    }

    private void loadLotNumbers() {
        lotNumber.setEditable(false);
        DefaultComboBoxModel<Long> model = new DefaultComboBoxModel<>();
        lotNumber.setModel(model);
        lotNumber.setEnabled(false);
        String query = "SELECT " + DBStructure.SEND_LOTS_ID
                + " FROM " + DBStructure.SEND_LOTS_TABLE
                + " ORDER BY " + DBStructure.SEND_LOTS_ID + " DESC";
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                model.addElement(results.getLong(DBStructure.SEND_LOTS_ID));
            }
            lotNumber.setEnabled(model.getSize() > 0);
        } catch (SQLException ex) {
            model.removeAllElements();
            JOptionPane.showMessageDialog(this, "Could not load lot numbers: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSelectedLot() {
        Long selected = (Long) lotNumber.getSelectedItem();
        if (selected == null) return;
        try {
            fetchLot(Math.toIntExact(selected));
        } catch (ArithmeticException ex) {
            JOptionPane.showMessageDialog(this, "Lot number exceeds the supported integer range.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void fetchLot(int lotid) {
        displayedLotId = null;
        receiveSaved = false;
        submitButton.setEnabled(false);
        JPanel packets = new JPanel();
        packets.setLayout(new BoxLayout(packets, BoxLayout.Y_AXIS));
        packets.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        String query = "SELECT p.send_packet_id, p.packet_number, p.customer_name, p.packet_remark, "
                + "i.send_item_id, i.item_name, i.sent_quantity, i.item_remark "
                + "FROM send_lots l LEFT JOIN send_packets p ON p.send_lot_id = l.send_lot_id "
                + "LEFT JOIN send_items i ON i.send_packet_id = p.send_packet_id "
                + "WHERE l.send_lot_id = ? ORDER BY p.packet_number, i.item_number";
        boolean found = false;
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, lotid);
            try (ResultSet results = statement.executeQuery()) {
                long currentPacket = -1;
                javax.swing.table.DefaultTableModel items = null;
                while (results.next()) {
                    found = true;
                    long packetId = results.getLong("send_packet_id");
                    if (results.wasNull()) continue;
                    if (packetId != currentPacket) {
                        currentPacket = packetId;
                        JPanel packet = new JPanel(new java.awt.BorderLayout(8, 8));
                        packet.setAlignmentX(0.0f);
                        packet.setBorder(BorderFactory.createTitledBorder(
                                "Packet " + results.getInt("packet_number")));
                        JPanel details = new JPanel(new java.awt.GridBagLayout());
                        java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
                        constraints.insets = new java.awt.Insets(3, 3, 3, 3);
                        constraints.anchor = java.awt.GridBagConstraints.WEST;
                        constraints.gridx = 0;
                        constraints.gridy = 0;
                        details.add(new JLabel("Customer"), constraints);
                        constraints.gridx = 1;
                        constraints.weightx = 1;
                        constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                        JTextField customer = new JTextField(results.getString("customer_name"));
                        customer.setEditable(false);
                        details.add(customer, constraints);
                        constraints.gridx = 0;
                        constraints.gridy = 1;
                        constraints.weightx = 0;
                        constraints.fill = java.awt.GridBagConstraints.NONE;
                        details.add(new JLabel("Packet remark"), constraints);
                        constraints.gridx = 1;
                        constraints.weightx = 1;
                        constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                        JTextField remark = new JTextField(results.getString("packet_remark"));
                        remark.setEditable(false);
                        details.add(remark, constraints);
                        packet.add(details, java.awt.BorderLayout.NORTH);

                        items = new javax.swing.table.DefaultTableModel(
                                new Object[]{"Item name", "Quantity", "Item remark", "Received"}, 0) {
                            @Override public boolean isCellEditable(int row, int column) {
                                return column == 3;
                            }
                            @Override public Class<?> getColumnClass(int column) {
                                return column == 1 || column == 3 ? Integer.class : String.class;
                            }
                        };
                        JTable table = new JTable(items);
                        table.setRowHeight(28);
                        table.setFillsViewportHeight(true);
                        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                        table.putClientProperty("terminateEditOnFocusLost", true);
                        table.getColumnModel().getColumn(3).setCellRenderer((t, value, selected, focused, row, column) ->
                                new JSpinner(new SpinnerNumberModel(((Number) value).intValue(), null, null, 1)));
                        table.getColumnModel().getColumn(3).setCellEditor(new ReceivedQuantityEditor());
                        // Keep the source ID associated with its row for later receive handling.
                        table.putClientProperty("sendItemIds", new java.util.ArrayList<Long>());
                        JScrollPane itemScroll = new JScrollPane(table);
                        itemScroll.setPreferredSize(new java.awt.Dimension(700, 180));
                        packet.add(itemScroll, java.awt.BorderLayout.CENTER);
                        packet.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 290));
                        packets.add(packet);
                        packets.add(Box.createVerticalStrut(8));
                        packet.putClientProperty("itemTable", table);
                        packet.putClientProperty("sendPacketId", packetId);
                    }
                    long itemId = results.getLong("send_item_id");
                    if (!results.wasNull()) {
                        items.addRow(new Object[]{results.getString("item_name"),
                                results.getInt("sent_quantity"), results.getString("item_remark"), 0});
                        JPanel packet = (JPanel) packets.getComponent(packets.getComponentCount() - 2);
                        JTable table = (JTable) packet.getClientProperty("itemTable");
                        @SuppressWarnings("unchecked")
                        java.util.List<Long> itemIds = (java.util.List<Long>) table.getClientProperty("sendItemIds");
                        itemIds.add(itemId);
                    }
                }
            }
        } catch (SQLException ex) {
            scrollPane.setViewportView(new JLabel("Could not load lot " + lotid));
            JOptionPane.showMessageDialog(this, "Could not load lot: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!found) packets.add(new JLabel("Lot " + lotid + " was not found."));
        else if (packets.getComponentCount() == 0) packets.add(new JLabel("This lot has no packets."));
        displayedLotId = found ? (long) lotid : null;
        submitButton.setEnabled(found && !transactionUncertain);
        scrollPane.setViewportView(packets);
        scrollPane.getVerticalScrollBar().setValue(0);
        packets.revalidate();
        packets.repaint();
    }


    public boolean insertData() {
        if (transactionUncertain) {
            JOptionPane.showMessageDialog(this, "The previous save could not be confirmed. Check the database before receiving again.");
            return false;
        }
        if (receiveSaved) {
            JOptionPane.showMessageDialog(this, "This audit was already saved. Select a lot again to record a new audit.");
            return false;
        }
        if (displayedLotId == null || !displayedLotId.equals(lotNumber.getSelectedItem())) {
            JOptionPane.showMessageDialog(this, "Please load a lot before submitting.");
            return false;
        }
        java.time.LocalDate receiveDate;
        java.util.Map<Long, Integer> counts = new java.util.LinkedHashMap<>();
        try {
            receiveDate = java.time.LocalDate.parse(String.valueOf(dateComboBox.getSelectedItem()),
                    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yy")
                            .withResolverStyle(java.time.format.ResolverStyle.SMART));
            java.awt.Component view = scrollPane.getViewport().getView();
            if (!(view instanceof JPanel)) throw new IllegalArgumentException("No packet data is loaded.");
            for (java.awt.Component component : ((JPanel) view).getComponents()) {
                if (!(component instanceof JPanel)) continue;
                JTable table = (JTable) ((JPanel) component).getClientProperty("itemTable");
                if (table == null) continue;
                if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
                    throw new IllegalArgumentException("Please enter a valid whole-number received quantity.");
                }
                @SuppressWarnings("unchecked")
                java.util.List<Long> itemIds = (java.util.List<Long>) table.getClientProperty("sendItemIds");
                for (int row = 0; row < table.getModel().getRowCount(); row++) {
                    int quantity = new java.math.BigDecimal(String.valueOf(
                            table.getModel().getValueAt(row, 3))).intValueExact();
                    if (counts.put(itemIds.get(row), quantity) != null) {
                        throw new IllegalArgumentException("A sent item appears more than once.");
                    }
                }
            }
            if (counts.isEmpty()) throw new IllegalArgumentException("This lot has no items to receive.");
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Cannot submit: " + ex.getMessage(),
                    "Invalid receive data", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        Connection connection = null;
        java.util.List<Long> savedActionIds = new java.util.ArrayList<>();
        boolean commitStarted = false;
        boolean committed = false;
        submitButton.setEnabled(false);
        try {
            connection = MyClass.createConnection();
            connection.setAutoCommit(false);
            // Recheck source IDs and destinations against the database, not editable display text.
            java.util.Map<Integer, java.util.List<Long>> itemsByGodown = new java.util.LinkedHashMap<>();
            String sourceQuery = "SELECT i.send_item_id, i.destination_godown_id FROM send_items i "
                    + "JOIN send_packets p ON p.send_packet_id = i.send_packet_id "
                    + "WHERE p.send_lot_id = ? ORDER BY i.send_item_id FOR UPDATE";
            int matched = 0;
            try (PreparedStatement source = connection.prepareStatement(sourceQuery)) {
                source.setLong(1, displayedLotId);
                try (ResultSet results = source.executeQuery()) {
                    while (results.next()) {
                        long itemId = results.getLong("send_item_id");
                        if (!counts.containsKey(itemId)) {
                            throw new SQLException("Lot items changed. Reload the lot before submitting.");
                        }
                        itemsByGodown.computeIfAbsent(results.getInt("destination_godown_id"),
                                ignored -> new java.util.ArrayList<>()).add(itemId);
                        matched++;
                    }
                }
            }
            if (matched != counts.size()) {
                throw new SQLException("Lot items changed. Reload the lot before submitting.");
            }
            String actionSql = "INSERT INTO receive_actions "
                    + "(send_lot_id, receive_date, receiving_godown_id, received_by_user, receive_lot_remark) "
                    + "VALUES (?, ?, ?, ?, ?)";
            String itemSql = "INSERT INTO receive_items "
                    + "(receive_action_id, send_item_id, received_quantity, receive_item_remark) VALUES (?, ?, ?, ?)";
            try (PreparedStatement action = connection.prepareStatement(actionSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement item = connection.prepareStatement(itemSql)) {
                for (java.util.Map.Entry<Integer, java.util.List<Long>> group : itemsByGodown.entrySet()) {
                    action.setLong(1, displayedLotId);
                    action.setDate(2, java.sql.Date.valueOf(receiveDate));
                    action.setInt(3, group.getKey());
                    action.setString(4, MyClass.login.getLoginID());
                    action.setNull(5, java.sql.Types.VARCHAR);
                    action.executeUpdate();
                    long actionId;
                    try (ResultSet keys = action.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No receive action ID was returned.");
                        actionId = keys.getLong(1);
                    }
                    savedActionIds.add(actionId);
                    for (long itemId : group.getValue()) {
                        item.setLong(1, actionId);
                        item.setLong(2, itemId);
                        item.setInt(3, counts.get(itemId));
                        // Sent remarks remain in send_items; no receiving remark input exists yet.
                        item.setNull(4, java.sql.Types.VARCHAR);
                        item.addBatch();
                    }
                }
                item.executeBatch();
            }
            commitStarted = true;
            connection.commit();
            committed = true;
            receiveSaved = true;
        } catch (SQLException | RuntimeException ex) {
            if (commitStarted) transactionUncertain = true;
            if (connection != null) {
                try { connection.rollback(); }
                catch (SQLException rollbackError) { transactionUncertain = true; }
            }
            JOptionPane.showMessageDialog(this,
                    transactionUncertain
                            ? "Save status is uncertain. Check the database before trying again.\n" + ex.getMessage()
                            : "Receive audit was not saved. Your entered counts have been kept.\n" + ex.getMessage(),
                    "Receive save error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (connection != null) {
                try { connection.close(); }
                catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Could not close the receive connection: " + ex.getMessage(),
                            "Connection cleanup", JOptionPane.ERROR_MESSAGE);
                }
            }
            submitButton.setEnabled(!receiveSaved && !transactionUncertain && displayedLotId != null);
        }
        if (committed) printFunction(savedActionIds);
        return committed;
    }

    /** Called only after commit. PDF/printing failure must never repeat the database inserts. */
    public void printFunction(java.util.List<Long> receiveActionIds) {
        Path pdf;
        try {
            pdf = savePDF(receiveActionIds);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "The receive audit is saved, but its PDF could not be created.\n" + ex.getMessage()
                            + "\nDo not submit again; the database records already exist.",
                    "Receive PDF", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Receive audit saved successfully.\n\nPDF saved to:\n" + pdf
                + "\n\nAfter OK, the browser print dialog will be requested.");
        try {
            BrowserPdfPrint.openPrintDialog(pdf);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(this, "The receive audit and PDF are saved.\n"
                    + ex.getMessage() + "\n\nPDF: " + pdf,
                    "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Path savePDF(java.util.List<Long> actionIds) throws Exception {
        if (actionIds == null || actionIds.isEmpty()) throw new IllegalArgumentException("No receive audit IDs supplied.");
        String database = MyClass.login.getDatabase();
        if (database == null || database.trim().isEmpty() || database.equals(".") || database.equals("..")
                || database.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")
                || database.endsWith(".") || database.endsWith(" ")
                || database.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new java.io.IOException("The database name cannot be used as a Windows folder name.");
        }
        java.util.List<ReceivePrintRow> rows = new java.util.ArrayList<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(actionIds.size(), "?"));
        String query = "SELECT a.receive_action_id, a.send_lot_id, a.receive_date, a.received_by_user, "
                + "g.godown_name, p.send_packet_id, p.packet_number, p.customer_name, p.packet_remark, "
                + "i.item_name, i.sent_quantity, i.item_remark, r.received_quantity "
                + "FROM receive_actions a JOIN receive_items r ON r.receive_action_id = a.receive_action_id "
                + "JOIN send_items i ON i.send_item_id = r.send_item_id "
                + "JOIN send_packets p ON p.send_packet_id = i.send_packet_id "
                + "JOIN godowns g ON g.godown_id = a.receiving_godown_id "
                + "WHERE a.receive_action_id IN (" + placeholders + ") "
                + "ORDER BY a.receive_action_id, p.packet_number, i.item_number";
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < actionIds.size(); index++) statement.setLong(index + 1, actionIds.get(index));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new ReceivePrintRow(
                        result.getLong("receive_action_id"), result.getLong("send_lot_id"),
                        result.getDate("receive_date").toLocalDate(), result.getString("received_by_user"),
                        result.getString("godown_name"), result.getLong("send_packet_id"),
                        result.getInt("packet_number"), result.getString("customer_name"),
                        result.getString("packet_remark"), result.getString("item_name"),
                        result.getInt("sent_quantity"), result.getString("item_remark"),
                        result.getInt("received_quantity")));
            }
        }
        if (rows.isEmpty() || rows.stream().map(ReceivePrintRow::actionId).distinct().count() != actionIds.size()) {
            throw new java.io.IOException("Could not find all saved receive audit records.");
        }
        Path root = Paths.get("data").toAbsolutePath().normalize();
        Path directory = root.resolve(database).resolve("receive diary").normalize();
        if (!directory.startsWith(root)) throw new java.io.IOException("Invalid database folder.");
        Files.createDirectories(directory);
        String stamp = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path destination = directory.resolve("received_" + stamp + "_" + rows.getFirst().lotId()
                + "_" + actionIds.getFirst() + ".pdf");
        Path temporary = Files.createTempFile(directory, ".receive-", ".tmp");
        try {
            writeReceivePDF(temporary, database, rows, destination.getFileName().toString());
            Files.move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeReceivePDF(Path path, String database, java.util.List<ReceivePrintRow> rows,
                                        String receiptTitle) throws Exception {
        Color navy = new Color(24, 53, 74);
        Color pale = new Color(237, 242, 245);
        Path unicode = Paths.get(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "mangal.ttf");
        if (!Files.isRegularFile(unicode)) throw new java.io.IOException("Mangal font was not found in Windows Fonts.");
        LayoutProcessor.enable();
        BaseFont base = BaseFont.createFont(unicode.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font normal = new Font(base, 10, Font.NORMAL, Color.BLACK);
        Font bold = new Font(base, 10, Font.BOLD, navy);
        Font small = new Font(base, 8, Font.NORMAL, new Color(86, 101, 117));
        Font title = new Font(base, 22, Font.BOLD, navy);
        Font white = new Font(base, 9, Font.BOLD, Color.WHITE);
        ReceivePrintRow first = rows.getFirst();
        Document document = new Document(PageSize.A4, 40, 40, 48, 52);
        try (java.io.OutputStream output = Files.newOutputStream(path)) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    PdfContentByte canvas = w.getDirectContent();
                    canvas.setColorStroke(new Color(213, 221, 227));
                    canvas.moveTo(d.left(), 39);
                    canvas.lineTo(d.right(), 39);
                    canvas.stroke();
                    ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                            new Phrase("Receive Diary | " + database + " | Lot " + first.lotId(), small), d.left(), 25, 0);
                    ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                            new Phrase("Page " + w.getPageNumber(), small), d.right(), 25, 0);
                    if (w.getPageNumber() > 1) ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                            new Phrase("LOT " + first.lotId() + " | continued", small), d.left(), d.top() + 15, 0);
                }
            });
            document.addTitle(receiptTitle);
            document.addAuthor(first.user());
            document.open();
            try {
                Paragraph heading = new Paragraph("RECEIVE DIARY", title);
                heading.setSpacingAfter(4);
                document.add(heading);
                Paragraph business = new Paragraph(database, small);
                business.setSpacingAfter(16);
                document.add(business);
                PdfPTable summary = new PdfPTable(new float[]{1.1f, 1f});
                summary.setWidthPercentage(100);
                summary.addCell(receivePdfCell("LOT " + first.lotId(), bold, pale, 1));
                summary.addCell(receivePdfCell(first.date().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), bold, pale, 1));
                summary.addCell(receivePdfCell("Audit IDs: " + rows.stream().map(ReceivePrintRow::actionId)
                        .distinct().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")), normal, pale, 1));
                summary.addCell(receivePdfCell("Received by: " + first.user(), normal, pale, 1));
                long sent = rows.stream().mapToLong(ReceivePrintRow::sent).sum();
                long received = rows.stream().mapToLong(ReceivePrintRow::received).sum();
                summary.addCell(receivePdfCell(rows.stream().map(ReceivePrintRow::packetId).distinct().count()
                        + " packets / " + rows.size() + " entries", small, pale, 1));
                summary.addCell(receivePdfCell("Sent: " + sent + " / Received: " + received, small, pale, 1));
                summary.setSpacingAfter(16);
                document.add(summary);
                int index = 0;
                while (index < rows.size()) {
                    ReceivePrintRow packet = rows.get(index);
                    PdfPTable table = new PdfPTable(new float[]{.45f, 2.2f, .65f, 2.4f, .85f});
                    table.setWidthPercentage(100);
                    table.setSpacingAfter(14);
                    String customer = packet.customer() == null || packet.customer().isEmpty() ? "No customer" : packet.customer();
                    table.addCell(receivePdfCell("Packet " + packet.packetNumber() + " | " + customer, bold, pale, 5));
                    table.addCell(receivePdfCell("Packet remark: " + (packet.packetRemark() == null ? "" : packet.packetRemark())
                            + "\nReceiving godown: " + packet.godown() + " | Audit " + packet.actionId(), normal, pale, 5));
                    for (String label : new String[]{"No.", "Item name", "Sent", "Item remark", "Received"}) {
                        table.addCell(receivePdfCell(label, white, navy, 1));
                    }
                    table.setHeaderRows(3);
                    table.setSplitRows(true);
                    table.setSplitLate(false);
                    int itemNumber = 1;
                    while (index < rows.size() && rows.get(index).packetId() == packet.packetId()
                            && rows.get(index).actionId() == packet.actionId()) {
                        ReceivePrintRow item = rows.get(index++);
                        Color background = itemNumber % 2 == 1 ? Color.WHITE : new Color(248, 250, 251);
                        table.addCell(receivePdfCell(String.valueOf(itemNumber++), normal, background, 1));
                        table.addCell(receivePdfCell(item.name(), normal, background, 1));
                        PdfPCell sentCell = receivePdfCell(String.valueOf(item.sent()), normal, background, 1);
                        sentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(sentCell);
                        table.addCell(receivePdfCell(item.itemRemark(), normal, background, 1));
                        PdfPCell receivedCell = receivePdfCell(String.valueOf(item.received()), normal, background, 1);
                        receivedCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(receivedCell);
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

    private static PdfPCell receivePdfCell(String text, Font font, Color background, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setColspan(colspan);
        cell.setPadding(7);
        cell.setLeading(0, 1.25f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        cell.setBorderColor(new Color(213, 221, 227));
        cell.setBorderWidth(.4f);
        cell.setBackgroundColor(background);
        return cell;
    }

    private record ReceivePrintRow(long actionId, long lotId, java.time.LocalDate date, String user,
                                   String godown, long packetId, int packetNumber, String customer,
                                   String packetRemark, String name, int sent, String itemRemark, int received) { }
    private static final class ReceivedQuantityEditor extends AbstractCellEditor
            implements javax.swing.table.TableCellEditor {
        private final JSpinner spinner = new JSpinner();
        private javax.swing.event.ChangeListener quantityListener;

        @Override public java.awt.Component getTableCellEditorComponent(JTable table, Object value,
                boolean selected, int row, int column) {
            if (quantityListener != null) spinner.removeChangeListener(quantityListener);
            spinner.setModel(new SpinnerNumberModel(((Number) value).intValue(), null, null, 1));
            quantityListener = event -> table.getModel().setValueAt(spinner.getValue(), row, column);
            spinner.addChangeListener(quantityListener);
            return spinner;
        }

        @Override public Object getCellEditorValue() { return spinner.getValue(); }

        @Override public boolean stopCellEditing() {
            try { spinner.commitEdit(); }
            catch (java.text.ParseException ex) { return false; }
            return super.stopCellEditing();
        }
    }
}
