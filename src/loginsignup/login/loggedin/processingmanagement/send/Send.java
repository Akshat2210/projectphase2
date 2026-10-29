package loginsignup.login.loggedin.processingmanagement.send;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;
import mainpack.MyClass;
import utils.UtilityMethods;
import utils.DBStructure;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class Send extends JFrame {
    private final ProcessingManagementScreen processingManagementScreen;
    private JPanel panel;
    private JButton backButton;
    private JComboBox<GodownOption> sendFrom;
    private JComboBox<GodownOption> sendTo;
    private JComboBox<String> dateComboBox;
    private JPanel packetWorkArea;
    private JSplitPane sendSplitPane;
    private JComboBox<String> selectionComboBox;
    private JScrollPane packetScrollPane;
    private JButton submitButton;
    private JTable itemTable;
    private JButton addPacketButton;
    private DefaultTableModel sourceItemTableModel;
    private JPanel packetListPanel;
    private final List<PacketPanel> packetPanels = new ArrayList<>();
    private final List<JScrollPane> packetItemScrollPanes = new ArrayList<>();
    private PacketPanel selectedPacket;
    private final List<RemarkComboBox> remarkComboBoxes = new ArrayList<>();
    private final List<String> presetRemarks = new ArrayList<>();
    private static final Object ADD_PRESET_REMARK = new Object() {
        @Override
        public String toString() {
            return "Add preset remark...";
        }
    };
    private long selectionModelRequestNumber;
    private long sourceLoadRequestNumber;
    private boolean sourceItemsReady;
    private String selectedPacketCustomer = "";
    private boolean saving;
    private int packetPanelHeight = 200;
    private final Map<java.awt.Component, Boolean> enabledBeforeSave = new IdentityHashMap<>();

    public Send(ProcessingManagementScreen processingManagementScreen) {
        this.processingManagementScreen = processingManagementScreen;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saving) return;
                dispose();
                processingManagementScreen.setVisible(true);
            }
        });

        UtilityMethods.generateAndAddDates(dateComboBox,false);
        submitButton.addActionListener(event -> submitSend());
        addPacketButton.addActionListener(event -> addPacket());
        configureSourceItemTable();
        selectionComboBox.addActionListener(event -> loadSelectedSourceItems());
        setupInitialPacketPanel();
        loadPresetRemarks();
        setPanelHeight(200);
        setLotScrollSpeed(32);
        loadGodowns();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setSplitRatio(75, 25);
    }

    private java.nio.file.Path savePDF(long lotId, LotDraft draft, String database,
                                      String from, String to) throws Exception {
        // data/<current database>/send diary/sent_<timestamp>_<lot number>.pdf
        return SendLotPdf.save(java.nio.file.Paths.get("data"), database, lotId, draft, from, to,
                java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
    }

    private void openPdfForPrinting(java.nio.file.Path pdf) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                BrowserPdfPrint.openPrintDialog(pdf);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // A dialog request is not a claim that paper has been printed.
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(processingManagementScreen,
                            "The send and PDF are saved.\n" + cause.getMessage() + "\n\nPDF: " + pdf,
                            "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void configureSourceItemTable() {
        sourceItemTableModel = new DefaultTableModel(
                new String[]{"Design ID", "Item name", "Quantity", "Details"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        itemTable.setModel(sourceItemTableModel);
        itemTable.setFillsViewportHeight(true);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setAutoCreateRowSorter(true);
        itemTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "pushData");
        itemTable.getActionMap().put("pushData", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pushData();
            }
        });
        itemTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)
                        && itemTable.rowAtPoint(event.getPoint()) >= 0) pushData();
            }
        });
    }

    private void setSplitRatio(int r1, int r2) {
        if (r1 <= 0 || r2 <= 0) {
            throw new IllegalArgumentException("Split ratios must be greater than zero.");
        }

        double leftRatio = (double) r1 / (r1 + r2);
        sendSplitPane.setResizeWeight(leftRatio);
        SwingUtilities.invokeLater(() -> sendSplitPane.setDividerLocation(leftRatio));
    }

    private void setPanelHeight(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("Panel height must be greater than zero.");
        }

        packetPanelHeight = height;
        for (PacketPanel packet : packetPanels) {
            Dimension size = new Dimension(0, height);
            packet.panel.setPreferredSize(size);
            packet.panel.setMinimumSize(size);
            packet.panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }
        packetListPanel.revalidate();
        packetListPanel.repaint();
    }

    private void setLotScrollSpeed(int speed) {
        if (speed <= 0) {
            throw new IllegalArgumentException("Scroll speed must be greater than zero.");
        }
        packetScrollPane.getVerticalScrollBar().setUnitIncrement(speed);
    }

    private void setPacketScrollSpeed(int speed) {
        if (speed <= 0) {
            throw new IllegalArgumentException("Scroll speed must be greater than zero.");
        }
        for (JScrollPane itemScrollPane : packetItemScrollPanes) {
            itemScrollPane.getVerticalScrollBar().setUnitIncrement(speed);
        }
    }

    /** Start the editable packet list with one blank packet. */
    private void setupInitialPacketPanel() {
        packetListPanel = new JPanel();
        packetListPanel.setLayout(new BoxLayout(packetListPanel, BoxLayout.Y_AXIS));
        packetListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        packetScrollPane.setViewportView(packetListPanel);

        addPacket();
    }

    private void addPacket() {
        if (saving) return;
        PacketPanel packet = buildPacketPanel(packetPanels.size() + 1);
        packetListPanel.add(packet.panel);
        setPanelHeight(packetPanelHeight);
        selectPacket(packet);
        SwingUtilities.invokeLater(() -> {
            if (selectedPacket == packet) {
                packetListPanel.revalidate();
                packetListPanel.doLayout();
                packetListPanel.scrollRectToVisible(packet.panel.getBounds());
            }
        });
    }

    private PacketPanel buildPacketPanel(int packetNumber) {
        JPanel packetPanel = new JPanel(new BorderLayout(8, 8));
        packetPanel.setAlignmentX(0.0f);
        packetPanel.setBorder(BorderFactory.createTitledBorder("Packet " + packetNumber));

        JPanel packetDetailsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.anchor = GridBagConstraints.WEST;

        JComboBox<String> customerComboBox = new JComboBox<>();
        customerComboBox.setEditable(false);
        UtilityMethods.generateAndAddNames(customerComboBox);
        RemarkComboBox packetRemarkComboBox = createRemarkComboBox();
        PacketPanel packet = new PacketPanel(packetPanel, customerComboBox);
        packet.remarkComboBox = packetRemarkComboBox;
        packetPanels.add(packet);

        packetPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                selectPacket(packet);
            }
        });
        customerComboBox.addActionListener(event -> selectPacket(packet));

        constraints.gridx = 0;
        constraints.gridy = 0;
        packetDetailsPanel.add(new JLabel("Customer"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        packetDetailsPanel.add(customerComboBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        packetDetailsPanel.add(new JLabel("Packet remark"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        packetDetailsPanel.add(packetRemarkComboBox, constraints);

        DefaultTableModel itemTableModel = new DefaultTableModel(
                new Object[0][3],
                new String[]{"Item name", "Quantity", "Item remark"}
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : String.class;
            }
        };

        JTable itemTable = new JTable(itemTableModel);
        itemTable.setRowHeight(26);
        itemTable.setFillsViewportHeight(true);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane itemScrollPane = new JScrollPane(itemTable);
        itemScrollPane.setPreferredSize(new Dimension(420, 180));
        packetItemScrollPanes.add(itemScrollPane);

        packetPanel.add(packetDetailsPanel, BorderLayout.NORTH);
        packet.itemTable = itemTable;
        packet.itemTableModel = itemTableModel;
        installPacketSelection(packetPanel, packet);
        packetPanel.add(itemScrollPane, BorderLayout.CENTER);
        installPacketSelection(itemScrollPane, packet);
        return packet;
    }

    private void selectPacket(PacketPanel packet) {
        String customer = getCustomerName(packet);
        if (selectedPacket == packet && customer.equals(selectedPacketCustomer)) return;
        selectedPacket = packet;
        selectedPacketCustomer = customer;
        for (PacketPanel current : packetPanels) {
            current.panel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(current == packet
                            ? java.awt.Color.BLUE : java.awt.Color.GRAY, current == packet ? 2 : 1),
                    "Packet " + (packetPanels.indexOf(current) + 1)
                            + (current == packet ? " (selected)" : "")));
        }
        updateSelectionComboBoxModel();
    }

    /**
     * The source selector always begins with Inventory.  If the selected packet
     * has a customer, the remaining choices are that customer's order-slip IDs.
     */
    private void updateSelectionComboBoxModel() {
        final PacketPanel packet = selectedPacket;
        final String customerName = getCustomerName(packet);
        final long requestNumber = ++selectionModelRequestNumber;
        sourceItemsReady = false;
        ++sourceLoadRequestNumber;
        sourceItemTableModel.setRowCount(0);

        new SwingWorker<DefaultComboBoxModel<String>, Void>() {
            @Override
            protected DefaultComboBoxModel<String> doInBackground() throws SQLException {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                model.addElement("Inventory");

                if (customerName.isEmpty()) {
                    return model;
                }

                String sql = "SELECT slip_id FROM order_slips_main "
                        + "WHERE customer_name = ? ORDER BY slip_id DESC";
                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) {
                        throw new SQLException("Could not connect to the database.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, customerName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                model.addElement(String.valueOf(resultSet.getInt("slip_id")));
                            }
                        }
                    }
                }
                return model;
            }

            @Override
            protected void done() {
                try {
                    if (requestNumber != selectionModelRequestNumber
                            || selectedPacket != packet
                            || !customerName.equals(getCustomerName(packet))) {
                        return;
                    }
                    selectionComboBox.setModel(get());
                    loadSelectedSourceItems();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Could not load order slips: " + exception.getMessage(),
                            "Order slips unavailable",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Loads source data without deducting any packet quantities. */
    private void loadSelectedSourceItems() {
        sourceItemsReady = false;
        sourceItemTableModel.setRowCount(0);
        ++sourceLoadRequestNumber;
        Object selectedSource = selectionComboBox.getSelectedItem();
        if (selectedSource == null) {
            sourceItemTableModel.setRowCount(0);
            return;
        }

        final String source = selectedSource.toString().trim();
        final PacketPanel packet = selectedPacket;
        final String customerName = getCustomerName(packet);
        final long requestNumber = ++sourceLoadRequestNumber;

        new SwingWorker<SourceTableData, Void>() {
            @Override
            protected SourceTableData doInBackground() throws SQLException {
                if ("Inventory".equals(source)) {
                    return loadInventorySourceData();
                }
                return loadOrderSlipSourceData(source, customerName);
            }

            @Override
            protected void done() {
                try {
                    Object currentSelection = selectionComboBox.getSelectedItem();
                    String currentSource = currentSelection == null ? "" : currentSelection.toString().trim();
                    if (requestNumber != sourceLoadRequestNumber
                            || selectedPacket != packet
                            || !source.equals(currentSource)
                            || !customerName.equals(getCustomerName(packet))) {
                        return;
                    }

                    SourceTableData data = get();
                    sourceItemTableModel.setDataVector(data.rows, data.columnNames);
                    // Keep the database item ID in the model, but out of the browser view.
                    if (!"Inventory".equals(source)) {
                        itemTable.removeColumn(itemTable.getColumnModel().getColumn(5));
                    }
                    sourceItemsReady = true;
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Could not load source items: " + exception.getMessage(),
                            "Source items unavailable",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private SourceTableData loadInventorySourceData() throws SQLException {
        String sql = "SELECT DESIGNID, itemname, TotalQuantity FROM inventory ORDER BY DESIGNID";
        List<Object[]> rows = new ArrayList<>();

        try (Connection connection = MyClass.createConnection()) {
            if (connection == null) {
                throw new SQLException("Could not connect to the database.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Object[]{
                            resultSet.getString("DESIGNID"),
                            resultSet.getString("itemname"),
                            resultSet.getInt("TotalQuantity"),
                            "Inventory"
                    });
                }
            }
        }

        return new SourceTableData(
                new String[]{"Design ID", "Item name", "Available quantity", "Source"},
                rows.toArray(new Object[0][])
        );
    }

    private SourceTableData loadOrderSlipSourceData(String source, String customerName) throws SQLException {
        final int orderSlipId;
        try {
            orderSlipId = Integer.parseInt(source);
        } catch (NumberFormatException exception) {
            return new SourceTableData(
                    new String[]{"S.No", "Design ID", "Item name", "Pending quantity", "Details", "Item ID"},
                    new Object[0][6]
            );
        }

        String sql = "SELECT item_id, sno, design_id, item_name, quantity, billed_quantity, other_details "
                + "FROM order_slips WHERE slip_id = ? AND customer_name = ? ORDER BY item_id";
        List<Object[]> rows = new ArrayList<>();

        try (Connection connection = MyClass.createConnection()) {
            if (connection == null) {
                throw new SQLException("Could not connect to the database.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, orderSlipId);
                statement.setString(2, customerName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        int pendingQuantity = resultSet.getInt("quantity")
                                - resultSet.getInt("billed_quantity");
                        rows.add(new Object[]{
                                resultSet.getInt("sno"),
                                resultSet.getString("design_id"),
                                resultSet.getString("item_name"),
                                pendingQuantity,
                                resultSet.getString("other_details"),
                                resultSet.getLong("item_id")
                        });
                    }
                }
            }
        }

        return new SourceTableData(
                new String[]{"S.No", "Design ID", "Item name", "Pending quantity", "Details", "Item ID"},
                rows.toArray(new Object[0][])
        );
    }

    private void installPacketSelection(java.awt.Component component, PacketPanel packet) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                selectPacket(packet);
            }
        });
        component.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                selectPacket(packet);
            }
        });
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                installPacketSelection(child, packet);
            }
        }
    }

    /** Copies a browser item to the active packet; never changes source quantities. */
    public void pushData() {
        if (saving) return;
        final PacketPanel packet = selectedPacket;
        if (packet == null || !sourceItemsReady) {
            JOptionPane.showMessageDialog(this, "Select a packet and wait for its source items to load.");
            return;
        }
        int viewRow = itemTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select an item to add to the packet.");
            return;
        }
        if (packet.itemTable.isEditing() && !packet.itemTable.getCellEditor().stopCellEditing()) return;
        int row = itemTable.convertRowIndexToModel(viewRow);
        String source = String.valueOf(selectionComboBox.getSelectedItem());
        boolean inventory = "Inventory".equals(source);
        String customer = getCustomerName(packet);
        String designId = (String) sourceItemTableModel.getValueAt(row, inventory ? 0 : 1);
        String itemName = (String) sourceItemTableModel.getValueAt(row, inventory ? 1 : 2);
        Object displayedQuantity = sourceItemTableModel.getValueAt(row, inventory ? 2 : 3);
        String remark = inventory ? "" : (String) sourceItemTableModel.getValueAt(row, 4);
        Integer slipId = inventory ? null : Integer.valueOf(source);
        Long slipItemId = inventory ? null : ((Number) sourceItemTableModel.getValueAt(row, 5)).longValue();
        long request = sourceLoadRequestNumber;
        String input = JOptionPane.showInputDialog(this,
                "Quantity to add for " + itemName + "\n"
                        + (inventory ? "Available quantity: " : "Pending quantity: ") + displayedQuantity,
                "Add to packet", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        final int quantity;
        try {
            quantity = Integer.parseInt(input.trim());
            if (quantity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Enter a whole number greater than zero.",
                    "Invalid quantity", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!sourceItemsReady || request != sourceLoadRequestNumber || packet != selectedPacket
                || !customer.equals(getCustomerName(packet))) {
            JOptionPane.showMessageDialog(this, "The packet or source changed. Select the item again.");
            return;
        }
        packet.itemSources.add(new PacketItemSource(designId, slipId, slipItemId));
        packet.itemTableModel.addRow(new Object[]{itemName, quantity, remark});
        if (!inventory) packet.customerComboBox.setEnabled(false);
        int addedRow = packet.itemTableModel.getRowCount() - 1;
        packet.itemTable.setRowSelectionInterval(addedRow, addedRow);
        packet.itemTable.scrollRectToVisible(packet.itemTable.getCellRect(addedRow, 0, true));
    }

    private static final class PacketItemSource {
        private final String designId;
        private final Integer orderSlipId;
        private final Long orderSlipItemId;

        private PacketItemSource(String designId, Integer orderSlipId, Long orderSlipItemId) {
            this.designId = designId;
            this.orderSlipId = orderSlipId;
            this.orderSlipItemId = orderSlipItemId;
        }
    }

    private String getCustomerName(PacketPanel packet) {
        if (packet == null) {
            return "";
        }
        Object customer = packet.customerComboBox.isEditable()
                ? packet.customerComboBox.getEditor().getItem()
                : packet.customerComboBox.getSelectedItem();
        String customerName = customer == null ? "" : customer.toString().trim();
        return "Select Customer".equals(customerName) ? "" : customerName;
    }


    private void submitSend() {
        if (saving) return;
        final LotDraft draft;
        try {
            draft = snapshotLot();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Check send entries", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String database = MyClass.getDatabase(); // Capture the login's database before starting the worker.
        final String from = String.valueOf(sendFrom.getSelectedItem());
        final String to = String.valueOf(sendTo.getSelectedItem());
        setSaving(true);
        new SwingWorker<SubmissionResult, Void>() {
            @Override
            protected SubmissionResult doInBackground() throws SQLException {
                Connection connection = MyClass.createConnection();
                if (connection == null) throw new SQLException("Could not connect to the database.");
                final long lotId;
                try {
                    // Insert the lot, its packets and items in one transaction; return the generated lot ID.
                    lotId = persistLot(connection, draft);
                } finally {
                    // A close error after commit must not turn a saved lot into a failed save.
                    try {
                        connection.close();
                    } catch (SQLException closeError) {
                        closeError.printStackTrace();
                    }
                }
                // Only a committed lot reaches this point. PDF failures never trigger another INSERT.
                try {
                    return new SubmissionResult(lotId, savePDF(lotId, draft, database, from, to), null);
                } catch (Exception pdfError) {
                    return new SubmissionResult(lotId, null, pdfError);
                }
            }

            @Override
            protected void done() {
                try {
                    SubmissionResult result = get();
                    String message = "Send saved successfully.\nLot number: " + result.lotId
                            + "\nPackets: " + draft.packets.size();
                    if (result.pdfError == null) {
                        message += "\n\nPDF saved to:\n" + result.pdfPath + "\n\nAfter OK, the browser print dialog will be requested.";
                    } else {
                        message += "\n\nThe lot is saved, but its PDF could not be created:\n"
                                + result.pdfError.getMessage()
                                + "\nDo not submit this lot again; its database records already exist.";
                    }
                    JOptionPane.showMessageDialog(Send.this, message,
                            result.pdfError == null ? "Send and PDF saved" : "Send saved - PDF not saved",
                            result.pdfError == null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    dispose();
                    processingManagementScreen.setVisible(true);
                    if (result.pdfError == null) openPdfForPrinting(result.pdfPath);
                } catch (Exception exception) {
                    Throwable cause = exception;
                    while (cause.getCause() != null && !(cause instanceof CommitUncertainException)) cause = cause.getCause();
                    if (cause instanceof CommitUncertainException) {
                        // Do not allow a second click to create a duplicate after a lost commit reply.
                        JOptionPane.showMessageDialog(Send.this,
                                cause.getMessage() + "\nCheck saved sends before retrying this lot.",
                                "Save status needs checking", JOptionPane.ERROR_MESSAGE);
                        setSaving(false);
                        submitButton.setEnabled(false);
                    } else {
                        setSaving(false);
                        JOptionPane.showMessageDialog(Send.this,
                                "Send was not saved. Your entries have been kept.\n" + cause.getMessage(),
                                "Could not save send", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    /** Read Swing state on the event thread; the worker receives a detached snapshot. */
    private LotDraft snapshotLot() {
        GodownOption from = (GodownOption) sendFrom.getSelectedItem();
        GodownOption to = (GodownOption) sendTo.getSelectedItem();
        if (from == null || to == null) {
            throw new IllegalArgumentException("Select both Send From and Send To godowns.");
        }
        if (from.id == to.id) {
            throw new IllegalArgumentException("Send From and Send To must be different godowns.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(String.valueOf(dateComboBox.getSelectedItem()),
                    DateTimeFormatter.ofPattern("dd-MM-uu").withResolverStyle(ResolverStyle.STRICT));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Select a valid send date.");
        }
        String user = MyClass.login == null ? "" : text(MyClass.login.getLoginID());
        if (user.isEmpty()) throw new IllegalArgumentException("Log in before submitting a send.");
        checkLength(user, 50, "Login name");
        List<PacketDraft> packets = new ArrayList<>();
        for (int index = 0; index < packetPanels.size(); index++) {
            PacketPanel packet = packetPanels.get(index);
            String label = "Packet " + (index + 1);
            if (packet.itemTable.isEditing() && !packet.itemTable.getCellEditor().stopCellEditing()) {
                throw new IllegalArgumentException(label + ": finish editing the current item first.");
            }
            int rowCount = packet.itemTableModel.getRowCount();
            if (rowCount == 0) continue; // Empty packets are not saved.
            if (!packet.remarkComboBox.isEnabled()) {
                throw new IllegalArgumentException(label + ": wait for the preset remark to finish saving.");
            }
            if (packet.itemSources.size() != rowCount) {
                throw new IllegalArgumentException(label + ": item source references do not match the rows.");
            }
            String customer = getCustomerName(packet);
            checkLength(customer, 255, label + " customer");
            String remark = text(packet.remarkComboBox.getEditor().getItem());
            checkLength(remark, 1000, label + " remark");
            List<ItemDraft> items = new ArrayList<>();
            for (int row = 0; row < rowCount; row++) {
                String itemLabel = label + ", item " + (row + 1);
                String name = text(packet.itemTableModel.getValueAt(row, 0));
                if (name.isEmpty()) throw new IllegalArgumentException(itemLabel + ": enter an item name.");
                checkLength(name, 255, itemLabel + " name");
                int quantity = positiveQuantity(packet.itemTableModel.getValueAt(row, 1), itemLabel);
                String itemRemark = text(packet.itemTableModel.getValueAt(row, 2));
                checkLength(itemRemark, 1000, itemLabel + " remark");
                PacketItemSource source = packet.itemSources.get(row);
                if (source.orderSlipItemId != null && customer.isEmpty()) {
                    throw new IllegalArgumentException(itemLabel + ": an order-slip item requires a customer.");
                }
                items.add(new ItemDraft(source.designId, source.orderSlipId, source.orderSlipItemId,
                        name, quantity, itemRemark));
            }
            packets.add(new PacketDraft(index + 1, customer, remark, items));
        }
        if (packets.isEmpty()) throw new IllegalArgumentException("Add at least one item before submitting.");
        return new LotDraft(date, from.id, to.id, user, packets);
    }

    private void setSaving(boolean busy) {
        saving = busy;
        if (busy) {
            enabledBeforeSave.clear();
            rememberAndDisable(panel);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            submitButton.setText("Saving...");
        } else {
            for (Map.Entry<java.awt.Component, Boolean> entry : enabledBeforeSave.entrySet()) {
                entry.getKey().setEnabled(entry.getValue());
            }
            enabledBeforeSave.clear();
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            submitButton.setText("Submit");
        }
    }

    private void rememberAndDisable(java.awt.Component component) {
        enabledBeforeSave.put(component, component.isEnabled());
        component.setEnabled(false);
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                rememberAndDisable(child);
            }
        }
    }

    static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    static void checkLength(String value, int maxLength, String label) {
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters.");
        }
    }

    static int positiveQuantity(Object value, String label) {
        try {
            int quantity = Integer.parseInt(text(value));
            if (quantity > 0) return quantity;
        } catch (NumberFormatException ignored) {
            // Fall through to the same actionable error for all invalid quantities.
        }
        throw new IllegalArgumentException(label + ": enter a whole-number quantity greater than zero.");
    }

    /**
     * Database part of Submit: inserts send_lots, then send_packets, then send_items.
     * Generated parent IDs link each child to its lot/packet. Commit only after every
     * insert succeeds; roll back on failure. Returns the saved lot number used by the PDF.
     * submitSend() handles validation, background work and user messages around this method.
     */
    static long persistLot(Connection connection, LotDraft draft) throws SQLException {
        connection.setAutoCommit(false);
        boolean committing = false;
        try {
            long lotId;
            String lotSql = "INSERT INTO " + DBStructure.SEND_LOTS_TABLE
                    + " (send_date, default_source_godown_id, default_destination_godown_id, created_by_user)"
                    + " VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(lotSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setDate(1, java.sql.Date.valueOf(draft.date));
                statement.setInt(2, draft.fromId);
                statement.setInt(3, draft.toId);
                statement.setString(4, draft.user);
                statement.executeUpdate();
                lotId = generatedId(statement);
            }
            String packetSql = "INSERT INTO " + DBStructure.SEND_PACKETS_TABLE
                    + " (send_lot_id, packet_number, customer_name, packet_remark) VALUES (?, ?, ?, ?)";
            String itemSql = "INSERT INTO " + DBStructure.SEND_ITEMS_TABLE
                    + " (send_packet_id, item_number, source_godown_id, destination_godown_id,"
                    + " order_slip_id, order_slip_item_id, design_id, item_name, sent_quantity, item_remark)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement packets = connection.prepareStatement(packetSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement items = connection.prepareStatement(itemSql)) {
                for (PacketDraft packet : draft.packets) {
                    packets.setLong(1, lotId);
                    packets.setInt(2, packet.number);
                    nullableString(packets, 3, packet.customer);
                    nullableString(packets, 4, packet.remark);
                    packets.executeUpdate();
                    long packetId = generatedId(packets);
                    for (int index = 0; index < packet.items.size(); index++) {
                        ItemDraft item = packet.items.get(index);
                        items.setLong(1, packetId);
                        items.setInt(2, index + 1);
                        items.setInt(3, draft.fromId);
                        items.setInt(4, draft.toId);
                        if (item.slipId == null) items.setNull(5, Types.INTEGER);
                        else items.setInt(5, item.slipId);
                        if (item.slipItemId == null) items.setNull(6, Types.BIGINT);
                        else items.setLong(6, item.slipItemId);
                        nullableString(items, 7, item.designId);
                        items.setString(8, item.name);
                        items.setInt(9, item.quantity);
                        nullableString(items, 10, item.remark);
                        items.executeUpdate();
                    }
                }
            }
            committing = true;
            connection.commit();
            return lotId;
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                exception.addSuppressed(rollbackError);
            }
            if (committing) {
                throw new CommitUncertainException("The database did not confirm whether the send was committed.", exception);
            }
            throw exception;
        }
    }

    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("The database did not return the new record ID.");
            return keys.getLong(1);
        }
    }

    private static void nullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    static final class CommitUncertainException extends SQLException {
        CommitUncertainException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class SubmissionResult {
        final long lotId;
        final java.nio.file.Path pdfPath;
        final Exception pdfError;

        SubmissionResult(long lotId, java.nio.file.Path pdfPath, Exception pdfError) {
            this.lotId = lotId;
            this.pdfPath = pdfPath;
            this.pdfError = pdfError;
        }
    }

    static final class LotDraft {
        final LocalDate date;
        final int fromId;
        final int toId;
        final String user;
        final List<PacketDraft> packets;

        LotDraft(LocalDate date, int fromId, int toId, String user, List<PacketDraft> packets) {
            this.date = date;
            this.fromId = fromId;
            this.toId = toId;
            this.user = user;
            this.packets = packets;
        }
    }

    static final class PacketDraft {
        final int number;
        final String customer;
        final String remark;
        final List<ItemDraft> items;

        PacketDraft(int number, String customer, String remark, List<ItemDraft> items) {
            this.number = number;
            this.customer = customer;
            this.remark = remark;
            this.items = items;
        }
    }

    static final class ItemDraft {
        final String designId;
        final Integer slipId;
        final Long slipItemId;
        final String name;
        final int quantity;
        final String remark;

        ItemDraft(String designId, Integer slipId, Long slipItemId, String name, int quantity, String remark) {
            this.designId = designId;
            this.slipId = slipId;
            this.slipItemId = slipItemId;
            this.name = name;
            this.quantity = quantity;
            this.remark = remark;
        }
    }

    private void loadGodowns() {
        new SwingWorker<List<GodownOption>, Void>() {
            @Override
            protected List<GodownOption> doInBackground() throws SQLException {
                String sql = "SELECT g.godown_id, g.godown_name, parent.godown_name AS parent_name "
                        + "FROM godowns g "
                        + "LEFT JOIN godowns parent ON parent.godown_id = g.parent_godown_id "
                        + "WHERE g.is_active = 1 "
                        + "ORDER BY COALESCE(parent.godown_name, g.godown_name), "
                        + "g.parent_godown_id IS NOT NULL, g.godown_name";

                List<GodownOption> godowns = new ArrayList<>();
                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) {
                        throw new SQLException("Could not connect to the database.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(sql);
                         ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String parentName = resultSet.getString("parent_name");
                            String godownName = resultSet.getString("godown_name");
                            String displayName = parentName == null
                                    ? godownName
                                    : parentName + " / " + godownName;
                            godowns.add(new GodownOption(resultSet.getInt("godown_id"), displayName));
                        }
                    }
                }
                return godowns;
            }

            @Override
            protected void done() {
                try {
                    List<GodownOption> godowns = get();
                    sendFrom.removeAllItems();
                    sendTo.removeAllItems();
                    for (GodownOption godown : godowns) {
                        sendFrom.addItem(godown);
                        sendTo.addItem(godown);
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Could not load godowns: " + exception.getMessage(),
                            "Godowns unavailable",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private RemarkComboBox createRemarkComboBox() {
        RemarkComboBox comboBox = new RemarkComboBox();
        comboBox.setEditable(true);
        remarkComboBoxes.add(comboBox);
        refreshRemarkOptions(comboBox, "");
        comboBox.addActionListener(event -> {
            if (comboBox.updating) return;
            if (comboBox.getSelectedItem() == ADD_PRESET_REMARK) {
                Object previousRemark = comboBox.previousRemark;
                comboBox.updating = true;
                comboBox.setSelectedItem(previousRemark);
                comboBox.updating = false;
                String input = JOptionPane.showInputDialog(Send.this,
                        "Enter the preset remark:", "Add preset remark", JOptionPane.PLAIN_MESSAGE);
                if (input == null) return;
                String remark = input.trim();
                if (remark.isEmpty() || remark.codePointCount(0, remark.length()) > 1000) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Enter a remark between 1 and 1000 characters.");
                    return;
                }
                savePresetRemark(comboBox, remark);
            } else {
                comboBox.previousRemark = comboBox.getEditor().getItem();
            }
        });
        return comboBox;
    }

    private void refreshRemarkOptions(RemarkComboBox comboBox, Object value) {
        comboBox.updating = true;
        try {
            comboBox.removeAllItems();
            comboBox.addItem("");
            for (String remark : presetRemarks) comboBox.addItem(remark);
            comboBox.addItem(ADD_PRESET_REMARK);
            comboBox.setSelectedItem(value);
            comboBox.previousRemark = value;
        } finally {
            comboBox.updating = false;
        }
    }

    private void refreshAllRemarkOptions() {
        for (RemarkComboBox comboBox : remarkComboBoxes) {
            refreshRemarkOptions(comboBox, comboBox.getEditor().getItem());
        }
    }

    private void loadPresetRemarks() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws SQLException {
                List<String> remarks = new ArrayList<>();
                String sql = "SELECT " + DBStructure.QUICKREMARKS_REMARK
                        + " FROM " + DBStructure.QUICKREMARKS_TABLE
                        + " ORDER BY " + DBStructure.QUICKREMARKS_REMARK_ID;
                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) throw new SQLException("Could not connect to the database.");
                    try (PreparedStatement statement = connection.prepareStatement(sql);
                         ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String remark = resultSet.getString(1);
                            if (remark != null && !remarks.contains(remark)) remarks.add(remark);
                        }
                    }
                }
                return remarks;
            }

            @Override
            protected void done() {
                try {
                    for (String remark : get()) {
                        if (!presetRemarks.contains(remark)) presetRemarks.add(remark);
                    }
                    refreshAllRemarkOptions();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Could not load preset remarks: " + exception.getMessage(),
                            "Preset remarks unavailable", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void savePresetRemark(RemarkComboBox comboBox, String remark) {
        comboBox.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SQLException {
                String sql = "INSERT INTO " + DBStructure.QUICKREMARKS_TABLE
                        + " (" + DBStructure.QUICKREMARKS_REMARK + ") VALUES (?)";
                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) throw new SQLException("Could not connect to the database.");
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, remark);
                        statement.executeUpdate();
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                comboBox.setEnabled(true);
                try {
                    get();
                    if (!presetRemarks.contains(remark)) presetRemarks.add(remark);
                    refreshAllRemarkOptions();
                    comboBox.setSelectedItem(remark);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Send.this,
                            "Could not save preset remark: " + exception.getMessage(),
                            "Preset remark not saved", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static final class RemarkComboBox extends JComboBox<Object> {
        private boolean updating;
        private Object previousRemark = "";
    }

    private static final class PacketPanel {
        private final JPanel panel;
        private final JComboBox<String> customerComboBox;
        private RemarkComboBox remarkComboBox;
        private JTable itemTable;
        private DefaultTableModel itemTableModel;
        // One source reference per packet row; quantities and remarks stay in the table.
        private final List<PacketItemSource> itemSources = new ArrayList<>();

        private PacketPanel(JPanel panel, JComboBox<String> customerComboBox) {
            this.panel = panel;
            this.customerComboBox = customerComboBox;
        }
    }

    private static final class SourceTableData {
        private final String[] columnNames;
        private final Object[][] rows;

        private SourceTableData(String[] columnNames, Object[][] rows) {
            this.columnNames = columnNames;
            this.rows = rows;
        }
    }

    private static final class GodownOption {
        private final int id;
        private final String displayName;

        private GodownOption(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
