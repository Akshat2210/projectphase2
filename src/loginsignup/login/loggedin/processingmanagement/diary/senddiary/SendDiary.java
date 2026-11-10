package loginsignup.login.loggedin.processingmanagement.diary.senddiary;

import mainpack.MyClass;
import loginsignup.login.loggedin.processingmanagement.diary.Diary;
import utils.DBStructure;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SendDiary extends JFrame {
    private JPanel panel;
    private JScrollPane packetScrollPane;
    private JButton backButton;
    private JComboBox<GodownOption> godownComboBox;
    private JComboBox<Long> lotComboBox;
    private JComboBox<String> customerFilter;
    private boolean godownsLoaded;
    private static final String SELECT_CUSTOMER = "Select Customer";
    private JButton button1;
    private JButton button2;
    private JPanel packetListPanel;
    private boolean updatingModels;
    private boolean initialized;
    private long lotListRequest;
    private long lotDataRequest;
    private final Diary diary;
    private boolean closed;

    public SendDiary() {
        this(null);
    }

    public SendDiary(Diary diary) {
        this.diary = diary;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        setTitle("Send Diary");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(panel);
        setMinimumSize(new Dimension(800, 550));
        packetListPanel = new JPanel();
        packetListPanel.setLayout(new BoxLayout(packetListPanel, BoxLayout.Y_AXIS));
        packetListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        packetScrollPane.setViewportView(packetListPanel);
        packetScrollPane.getVerticalScrollBar().setUnitIncrement(32);
        godownComboBox.setEditable(false);
        lotComboBox.setEditable(false);
        customerFilter.setEditable(false);
        customerFilter.setModel(new DefaultComboBoxModel<>(new String[]{SELECT_CUSTOMER}));
        customerFilter.setToolTipText("Show all sent packets for one customer, grouped by lot");
        javax.swing.Timer customerSelectionDelay = new javax.swing.Timer(200, event -> customerFilterChanged());
        customerSelectionDelay.setRepeats(false);
        customerFilter.addActionListener(event -> {
            if (!updatingModels) customerSelectionDelay.restart();
        });
        godownComboBox.setToolTipText("Filter lots by their source (Sent From) godown");
        lotComboBox.setToolTipText("Select a send lot number");
        backButton.addActionListener(event -> dispose());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                closed = true;
                ++lotListRequest;
                ++lotDataRequest;
                if (diary != null) diary.setVisible(true);
            }
        });
        button1.setEnabled(false);
        button2.setEnabled(false);
        button1.setToolTipText("Previous (older) lot in the selected godown");
        button2.setToolTipText("Next (newer) lot in the selected godown");
        // The lot list is ordered newest first, so older lots have a higher index.
        button1.addActionListener(event -> navigateLot(1));
        button2.addActionListener(event -> navigateLot(-1));
        javax.swing.Timer godownSelectionDelay = new javax.swing.Timer(200, event -> loadLotNumbers());
        godownSelectionDelay.setRepeats(false);
        godownComboBox.addActionListener(event -> {
            if (!updatingModels) godownSelectionDelay.restart();
        });
        javax.swing.Timer lotSelectionDelay = new javax.swing.Timer(200, event -> loadSelectedLot());
        lotSelectionDelay.setRepeats(false);
        lotComboBox.addActionListener(event -> {
            if (!updatingModels) lotSelectionDelay.restart();
        });
        showMessage("Loading godowns...");
        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        loadGodowns();
        loadCustomers();
    }

    private boolean hasSelectedCustomer() {
        return customerFilter.getSelectedIndex() > 0;
    }

    private void loadCustomers() {
        customerFilter.setEnabled(false);
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws SQLException {
                List<String> customers = new ArrayList<>();
                customers.add(SELECT_CUSTOMER);
                String sql = "SELECT customer_name FROM " + DBStructure.CUSTOMERS_TABLE
                        + " WHERE customer_name IS NOT NULL AND TRIM(customer_name) <> ''"
                        + " UNION SELECT customer_name FROM " + DBStructure.SEND_PACKETS_TABLE
                        + " WHERE customer_name IS NOT NULL AND TRIM(customer_name) <> '' ORDER BY customer_name";
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) customers.add(result.getString(1));
                }
                return customers;
            }

            @Override
            protected void done() {
                if (closed) return;
                try {
                    List<String> customers = get();
                    updatingModels = true;
                    try {
                        customerFilter.setModel(new DefaultComboBoxModel<>(customers.toArray(new String[0])));
                    } finally {
                        updatingModels = false;
                    }
                    customerFilter.setEnabled(true);
                } catch (Exception exception) {
                    // Keep normal lot browsing usable if the customer master is unavailable.
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(SendDiary.this,
                            "Could not load customers: " + cause.getMessage(), "Send Diary", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void customerFilterChanged() {
        ++lotListRequest;
        final long request = ++lotDataRequest;
        boolean customerSelected = hasSelectedCustomer();
        godownComboBox.setEnabled(!customerSelected && godownsLoaded);
        lotComboBox.setEnabled(false);
        updateLotNavigation();
        if (!customerSelected) {
            if (godownsLoaded) loadLotNumbers();
            else showMessage("Loading godowns...");
            return;
        }
        final String customer = (String) customerFilter.getSelectedItem();
        showMessage("Loading packets for " + customer + "...");
        new SwingWorker<List<LotData>, Void>() {
            @Override
            protected List<LotData> doInBackground() throws SQLException {
                try (Connection connection = openConnection()) {
                    return fetchCustomerLots(connection, customer);
                }
            }

            @Override
            protected void done() {
                if (closed || request != lotDataRequest) return;
                try {
                    List<LotData> lots = get();
                    if (lots.isEmpty()) showMessage("No sent packets found for " + customer + ".");
                    else displayLots(lots);
                } catch (Exception exception) {
                    showError("Could not load customer packets", exception);
                }
            }
        }.execute();
    }

    private void loadGodowns() {
        godownComboBox.setEnabled(false);
        lotComboBox.setEnabled(false);
        new SwingWorker<List<GodownOption>, Void>() {
            @Override
            protected List<GodownOption> doInBackground() throws SQLException {
                List<GodownOption> options = new ArrayList<>();
                options.add(new GodownOption(null, "All godowns"));
                // Include inactive godowns because historical sends still belong in the diary.
                String sql = "SELECT g.godown_id, g.godown_name, parent.godown_name AS parent_name"
                        + " FROM " + DBStructure.GODOWNS_TABLE + " g LEFT JOIN "
                        + DBStructure.GODOWNS_TABLE + " parent ON parent.godown_id = g.parent_godown_id"
                        + " ORDER BY COALESCE(parent.godown_name, g.godown_name), g.godown_name";
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        options.add(new GodownOption(result.getInt("godown_id"),
                                godownName(result.getString("parent_name"), result.getString("godown_name"))));
                    }
                }
                return options;
            }

            @Override
            protected void done() {
                if (closed) return;
                try {
                    List<GodownOption> options = get();
                    updatingModels = true;
                    try {
                        godownComboBox.setModel(new DefaultComboBoxModel<>(options.toArray(new GodownOption[0])));
                    } finally {
                        updatingModels = false;
                    }
                    godownsLoaded = true;
                    godownComboBox.setEnabled(!hasSelectedCustomer());
                    if (!hasSelectedCustomer()) loadLotNumbers();
                } catch (Exception exception) {
                    showError("Could not load godowns", exception);
                }
            }
        }.execute();
    }

    private void loadLotNumbers() {
        if (closed || hasSelectedCustomer()) return;
        GodownOption godown = (GodownOption) godownComboBox.getSelectedItem();
        if (godown == null) return;
        final long request = ++lotListRequest;
        ++lotDataRequest;
        updatingModels = true;
        try {
            lotComboBox.removeAllItems();
            lotComboBox.setEnabled(false);
            updateLotNavigation();
        } finally {
            updatingModels = false;
        }
        showMessage("Loading lot numbers...");
        new SwingWorker<List<Long>, Void>() {
            @Override
            protected List<Long> doInBackground() throws SQLException {
                List<Long> lots = new ArrayList<>();
                String sql = "SELECT l.send_lot_id FROM " + DBStructure.SEND_LOTS_TABLE + " l";
                if (godown.id != null) {
                    sql += " WHERE l.default_source_godown_id = ? OR EXISTS (SELECT 1 FROM "
                            + DBStructure.SEND_PACKETS_TABLE + " p JOIN " + DBStructure.SEND_ITEMS_TABLE
                            + " i ON i.send_packet_id = p.send_packet_id"
                            + " WHERE p.send_lot_id = l.send_lot_id AND i.source_godown_id = ?)";
                }
                sql += " ORDER BY l.send_lot_id DESC";
                try (Connection connection = openConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (godown.id != null) {
                        statement.setInt(1, godown.id);
                        statement.setInt(2, godown.id);
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) lots.add(result.getLong("send_lot_id"));
                    }
                }
                return lots;
            }

            @Override
            protected void done() {
                if (request != lotListRequest) return;
                try {
                    List<Long> lots = get();
                    updatingModels = true;
                    try {
                        lotComboBox.setModel(new DefaultComboBoxModel<>(lots.toArray(new Long[0])));
                        lotComboBox.setEnabled(!lots.isEmpty());
                        updateLotNavigation();
                    } finally {
                        updatingModels = false;
                    }
                    if (lots.isEmpty()) showMessage("No send lots found for this godown.");
                    else loadSelectedLot();
                } catch (Exception exception) {
                    showError("Could not load lot numbers", exception);
                }
            }
        }.execute();
    }

    private void navigateLot(int offset) {
        if (closed || updatingModels || hasSelectedCustomer() || !lotComboBox.isEnabled()) return;
        int current = lotComboBox.getSelectedIndex();
        int target = current + offset;
        if (current >= 0 && target >= 0 && target < lotComboBox.getItemCount()) {
            // The existing selection listener loads the chosen lot's packet cards.
            lotComboBox.setSelectedIndex(target);
        }
    }

    private void updateLotNavigation() {
        int index = lotComboBox.getSelectedIndex();
        boolean available = !hasSelectedCustomer() && lotComboBox.isEnabled() && index >= 0;
        button1.setEnabled(available && index < lotComboBox.getItemCount() - 1);
        button2.setEnabled(available && index > 0);
    }

    private void loadSelectedLot() {
        if (closed || hasSelectedCustomer()) return;
        updateLotNavigation();
        Long lotId = (Long) lotComboBox.getSelectedItem();
        final long request = ++lotDataRequest;
        if (lotId == null) {
            showMessage("Select a lot number.");
            return;
        }
        showMessage("Loading lot " + lotId + "...");
        new SwingWorker<LotData, Void>() {
            @Override
            protected LotData doInBackground() throws SQLException {
                try (Connection connection = openConnection()) {
                    return fetchLot(connection, lotId);
                }
            }

            @Override
            protected void done() {
                if (request != lotDataRequest) return;
                try {
                    LotData lot = get();
                    if (lot == null) showMessage("This send lot no longer exists.");
                    else displayLot(lot);
                } catch (Exception exception) {
                    showError("Could not load send lot", exception);
                }
            }
        }.execute();
    }

    static LotData fetchLot(Connection connection, long lotId) throws SQLException {
        List<LotData> lots = fetchLots(connection, lotId, null);
        return lots.isEmpty() ? null : lots.get(0);
    }

    static List<LotData> fetchCustomerLots(Connection connection, String customer) throws SQLException {
        return fetchLots(connection, null, customer);
    }

    private static List<LotData> fetchLots(Connection connection, Long lotId, String customer) throws SQLException {
        String sql = "SELECT l.send_lot_id, l.send_date, l.created_by_user, l.lot_remark,"
                + " src.godown_name AS source_name, sp.godown_name AS source_parent,"
                + " dst.godown_name AS destination_name, dp.godown_name AS destination_parent,"
                + " p.send_packet_id, p.packet_number, p.customer_name, p.packet_remark,"
                + " i.send_item_id, i.item_name, i.sent_quantity, i.item_remark,"
                + " COALESCE(SUM(forwarded.sent_quantity), 0) AS items_forwarded"
                + " FROM " + DBStructure.SEND_LOTS_TABLE + " l"
                + " LEFT JOIN " + DBStructure.GODOWNS_TABLE + " src ON src.godown_id = l.default_source_godown_id"
                + " LEFT JOIN " + DBStructure.GODOWNS_TABLE + " sp ON sp.godown_id = src.parent_godown_id"
                + " LEFT JOIN " + DBStructure.GODOWNS_TABLE + " dst ON dst.godown_id = l.default_destination_godown_id"
                + " LEFT JOIN " + DBStructure.GODOWNS_TABLE + " dp ON dp.godown_id = dst.parent_godown_id"
                + " LEFT JOIN " + DBStructure.SEND_PACKETS_TABLE + " p ON p.send_lot_id = l.send_lot_id"
                + " LEFT JOIN " + DBStructure.SEND_ITEMS_TABLE + " i ON i.send_packet_id = p.send_packet_id"
                + " LEFT JOIN " + DBStructure.SEND_ITEMS_TABLE + " forwarded ON forwarded.parent_send_item_id = i.send_item_id"
                + (lotId != null ? " WHERE l.send_lot_id = ?" : " WHERE p.customer_name = ?")
                + " GROUP BY l.send_lot_id, l.send_date, l.created_by_user, l.lot_remark,"
                + " src.godown_name, sp.godown_name, dst.godown_name, dp.godown_name,"
                + " p.send_packet_id, p.packet_number, p.customer_name, p.packet_remark,"
                + " i.send_item_id, i.item_name, i.sent_quantity, i.item_remark, i.item_number"
                + " ORDER BY l.send_lot_id DESC, p.packet_number, p.send_packet_id, i.item_number, i.send_item_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (lotId != null) statement.setLong(1, lotId);
            else statement.setString(1, customer);
            try (ResultSet result = statement.executeQuery()) {
                Map<Long, LotData> lots = new LinkedHashMap<>();
                Map<Long, PacketData> packets = new LinkedHashMap<>();
                while (result.next()) {
                    long currentLotId = result.getLong("send_lot_id");
                    LotData lot = lots.get(currentLotId);
                    if (lot == null) {
                        lot = new LotData();
                        lot.id = result.getLong("send_lot_id");
                        java.sql.Date date = result.getDate("send_date");
                        lot.date = date == null ? null : date.toLocalDate();
                        lot.source = godownName(result.getString("source_parent"), result.getString("source_name"));
                        lot.destination = godownName(result.getString("destination_parent"), result.getString("destination_name"));
                        lot.user = result.getString("created_by_user");
                        lot.remark = result.getString("lot_remark");
                        lots.put(lot.id, lot);
                    }
                    long packetId = result.getLong("send_packet_id");
                    if (result.wasNull()) continue;
                    PacketData packet = packets.get(packetId);
                    if (packet == null) {
                        packet = new PacketData();
                        packet.number = result.getInt("packet_number");
                        packet.customer = result.getString("customer_name");
                        packet.remark = result.getString("packet_remark");
                        packets.put(packetId, packet);
                        lot.packets.add(packet);
                    }
                    result.getLong("send_item_id");
                    if (!result.wasNull()) {
                        packet.rows.add(new Object[]{result.getString("item_name"),
                                result.getInt("sent_quantity"), result.getInt("items_forwarded"),
                                result.getString("item_remark")});
                    }
                }
                return new ArrayList<>(lots.values());
            }
        }
    }

    private void displayLot(LotData lot) {
        displayLots(java.util.Collections.singletonList(lot));
    }

    private void displayLots(List<LotData> lots) {
        packetListPanel.removeAll();
        for (int index = 0; index < lots.size(); index++) {
            if (index > 0) {
                packetListPanel.add(Box.createVerticalStrut(16));
                JSeparator separator = new JSeparator();
                separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
                packetListPanel.add(separator);
                packetListPanel.add(Box.createVerticalStrut(8));
            }
            appendLot(lots.get(index));
        }
        packetListPanel.revalidate();
        packetListPanel.repaint();
        SwingUtilities.invokeLater(() -> packetScrollPane.getVerticalScrollBar().setValue(0));
    }

    private void appendLot(LotData lot) {
        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBorder(BorderFactory.createTitledBorder("Lot " + lot.id));
        summary.add(valueField("Send date", lot.date == null ? "" : lot.date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))));
        summary.add(valueField("Send From", lot.source));
        summary.add(valueField("Send To", lot.destination));
        summary.add(valueField("Created by", lot.user));
        if (lot.remark != null && !lot.remark.isEmpty()) summary.add(valueField("Lot remark", lot.remark));
        summary.setAlignmentX(0);
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, summary.getPreferredSize().height));
        packetListPanel.add(summary);
        for (PacketData packet : lot.packets) {
            packetListPanel.add(Box.createVerticalStrut(8));
            packetListPanel.add(createPacketPanel(packet));
        }
        if (lot.packets.isEmpty()) packetListPanel.add(new JLabel("This lot has no packets."));
    }

    static JPanel createPacketPanel(PacketData packet) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setAlignmentX(0);
        card.setBorder(BorderFactory.createTitledBorder("Packet " + packet.number));
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.add(valueField("Customer", packet.customer));
        details.add(valueField("Packet remark", packet.remark));
        card.add(details, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(packet.rows.toArray(new Object[0][]),
                new String[]{"Item name", "Quantity", "Items forwarded", "Item remark"}) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 || column == 2 ? Integer.class : String.class;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane items = new JScrollPane(table);
        items.getVerticalScrollBar().setUnitIncrement(26);
        card.add(items, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(600, 220));
        card.setMinimumSize(new Dimension(0, 220));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        return card;
    }

    private static JPanel valueField(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel caption = new JLabel(label);
        caption.setPreferredSize(new Dimension(100, 24));
        JTextField text = new JTextField(value == null ? "" : value);
        text.setEditable(false);
        row.add(caption, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    private void showMessage(String message) {
        packetListPanel.removeAll();
        packetListPanel.add(new JLabel(message));
        packetListPanel.revalidate();
        packetListPanel.repaint();
    }

    private void showError(String message, Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        showMessage(message + ".");
        JOptionPane.showMessageDialog(this, message + ": " + cause.getMessage(),
                "Send Diary", JOptionPane.ERROR_MESSAGE);
    }

    private static Connection openConnection() throws SQLException {
        Connection connection = MyClass.createConnection();
        if (connection == null) throw new SQLException("Could not connect to the database.");
        return connection;
    }

    static String godownName(String parent, String name) {
        if (name == null) return "";
        return parent == null || parent.isEmpty() ? name : parent + " / " + name;
    }

    static final class GodownOption {
        final Integer id;
        final String name;
        GodownOption(Integer id, String name) { this.id = id; this.name = name; }
        @Override
        public String toString() { return name; }
    }

    static final class LotData {
        long id;
        LocalDate date;
        String source, destination, user, remark;
        final List<PacketData> packets = new ArrayList<>();
    }

    static final class PacketData {
        int number;
        String customer, remark;
        final List<Object[]> rows = new ArrayList<>();
    }
}
