package loginsignup.login.loggedin.processingmanagement.diary.recievediary;

import loginsignup.login.loggedin.processingmanagement.diary.Diary;
import mainpack.MyClass;
import utils.DBStructure;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecieveDiary extends JFrame {
    // These controls are instantiated and laid out by RecieveDiary.form.
    private JPanel panel;
    private JButton backButton, previousButton, nextButton;
    private JComboBox<Option> godownComboBox, lotComboBox;
    private JComboBox<String> customerFilter;
    private JScrollPane packetScrollPane;
    private final JPanel packetListPanel = new JPanel();
    private final Diary diary;
    private final Timer filterDelay = new Timer(200, e -> loadEntries());
    private final Timer entryDelay = new Timer(200, e -> loadSelectedEntry());
    private boolean initialized, closed, updatingModels, loaded;
    private long listRequest, dataRequest;

    public RecieveDiary() { this(null); }
    public RecieveDiary(Diary diary) { this.diary = diary; }

    public void init() {
        if (initialized) return;
        initialized = true;
        setTitle("Receive Diary"); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(panel); setMinimumSize(new Dimension(850, 550));
        packetListPanel.setLayout(new BoxLayout(packetListPanel, BoxLayout.Y_AXIS));
        packetListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        packetScrollPane.setViewportView(packetListPanel);
        packetScrollPane.getVerticalScrollBar().setUnitIncrement(32);
        godownComboBox.setEditable(false); lotComboBox.setEditable(false); customerFilter.setEditable(false);
        filterDelay.setRepeats(false); entryDelay.setRepeats(false);
        godownComboBox.addActionListener(e -> filterChanged());
        customerFilter.addActionListener(e -> filterChanged());
        lotComboBox.addActionListener(e -> {
            if (!updatingModels && !closed) { ++dataRequest; updateNavigation(); entryDelay.restart(); }
        });
        previousButton.addActionListener(e -> navigate(1));
        nextButton.addActionListener(e -> navigate(-1));
        backButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true; ++listRequest; ++dataRequest; filterDelay.stop(); entryDelay.stop();
                if (diary != null) diary.setVisible(true);
            }
        });
        godownComboBox.setEnabled(false); customerFilter.setEnabled(false); lotComboBox.setEnabled(false);
        updateNavigation(); pack(); setLocationRelativeTo(diary); setExtendedState(MAXIMIZED_BOTH);
        loadFilters();
    }

    private boolean customerSelected() { return customerFilter.getSelectedIndex() > 0; }
    private void filterChanged() {
        if (closed || updatingModels || !loaded) return;
        ++listRequest; ++dataRequest; entryDelay.stop();
        godownComboBox.setEnabled(!customerSelected()); lotComboBox.setEnabled(false);
        updateNavigation(); message("Loading receive entries..."); filterDelay.restart();
    }
    private void loadFilters() {
        message("Loading filters...");
        new SwingWorker<Filters, Void>() {
            protected Filters doInBackground() throws SQLException {
                Filters filters = new Filters(); filters.godowns.add(new Option(null, "All godowns"));
                filters.customers.add("Select Customer");
                try (Connection c = openConnection()) {
                    try (PreparedStatement s = c.prepareStatement("SELECT g.godown_id,g.godown_name,p.godown_name FROM "
                            + DBStructure.GODOWNS_TABLE + " g LEFT JOIN " + DBStructure.GODOWNS_TABLE
                            + " p ON p.godown_id=g.parent_godown_id ORDER BY g.godown_name"); ResultSet r = s.executeQuery()) {
                        while (r.next()) filters.godowns.add(new Option(r.getLong(1), godownName(r.getString(3), r.getString(2))));
                    }
                    try (PreparedStatement s = c.prepareStatement("SELECT customer_name FROM " + DBStructure.CUSTOMERS_TABLE
                            + " WHERE customer_name IS NOT NULL AND TRIM(customer_name)<>'' UNION SELECT customer_name FROM "
                            + DBStructure.SEND_PACKETS_TABLE + " WHERE customer_name IS NOT NULL AND TRIM(customer_name)<>'' ORDER BY customer_name");
                         ResultSet r = s.executeQuery()) { while (r.next()) filters.customers.add(r.getString(1)); }
                }
                return filters;
            }
            protected void done() {
                if (closed) return;
                try {
                    Filters filters = get(); updatingModels = true;
                    try {
                        godownComboBox.setModel(new DefaultComboBoxModel<>(filters.godowns.toArray(new Option[0])));
                        customerFilter.setModel(new DefaultComboBoxModel<>(filters.customers.toArray(new String[0])));
                    } finally { updatingModels = false; }
                    loaded = true; godownComboBox.setEnabled(true); customerFilter.setEnabled(true); loadEntries();
                } catch (Exception e) { showError(e); }
            }
        }.execute();
    }
    private void loadEntries() {
        if (closed || !loaded) return;
        final long request = ++listRequest; ++dataRequest;
        final String customer = customerSelected() ? (String) customerFilter.getSelectedItem() : null;
        final Option godown = (Option) godownComboBox.getSelectedItem();
        lotComboBox.setEnabled(false); updateNavigation(); message("Loading receive entries...");
        new SwingWorker<List<Option>, Void>() {
            protected List<Option> doInBackground() throws SQLException {
                List<Option> entries = new ArrayList<>();
                String sql = "SELECT a.receive_action_id,a.send_lot_id FROM " + DBStructure.RECEIVE_ACTIONS_TABLE + " a WHERE 1=1";
                if (customer != null) sql += " AND EXISTS (SELECT 1 FROM " + DBStructure.RECEIVE_ITEMS_TABLE
                        + " r JOIN " + DBStructure.SEND_ITEMS_TABLE + " i ON i.send_item_id=r.send_item_id JOIN "
                        + DBStructure.SEND_PACKETS_TABLE + " p ON p.send_packet_id=i.send_packet_id WHERE r.receive_action_id=a.receive_action_id AND p.customer_name=?)";
                else if (godown != null && godown.id != null) sql += " AND a.receiving_godown_id=?";
                sql += " ORDER BY a.receive_action_id DESC";
                try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                    if (customer != null) s.setString(1, customer);
                    else if (godown != null && godown.id != null) s.setLong(1, godown.id);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) entries.add(new Option(r.getLong(1), "Receive " + r.getLong(1) + " / Lot " + r.getLong(2)));
                    }
                }
                return entries;
            }
            protected void done() {
                if (closed || request != listRequest) return;
                try {
                    List<Option> entries = get(); updatingModels = true;
                    try { lotComboBox.setModel(new DefaultComboBoxModel<>(entries.toArray(new Option[0]))); }
                    finally { updatingModels = false; }
                    lotComboBox.setEnabled(customer == null && !entries.isEmpty()); updateNavigation();
                    if (entries.isEmpty()) message("No receive entries found.");
                    else loadData(customer == null ? entries.get(0).id : null, customer);
                } catch (Exception e) { showError(e); }
            }
        }.execute();
    }
    private void navigate(int offset) {
        if (closed || !lotComboBox.isEnabled()) return;
        int target = lotComboBox.getSelectedIndex() + offset;
        if (target >= 0 && target < lotComboBox.getItemCount()) lotComboBox.setSelectedIndex(target);
    }
    private void updateNavigation() {
        int index = lotComboBox.getSelectedIndex();
        boolean enabled = !closed && !customerSelected() && lotComboBox.isEnabled() && index >= 0;
        previousButton.setEnabled(enabled && index < lotComboBox.getItemCount() - 1);
        nextButton.setEnabled(enabled && index > 0);
    }
    private void loadSelectedEntry() {
        if (closed || customerSelected() || !lotComboBox.isEnabled()) return;
        Option entry = (Option) lotComboBox.getSelectedItem();
        if (entry != null) loadData(entry.id, null);
    }
    private void loadData(Long actionId, String customer) {
        final long request = ++dataRequest; message("Loading received packets...");
        new SwingWorker<List<Entry>, Void>() {
            protected List<Entry> doInBackground() throws SQLException {
                String sql = "SELECT a.receive_action_id,a.send_lot_id,a.receive_date,a.received_by_user,a.receive_lot_remark,"
                        + " g.godown_name,gp.godown_name AS parent_name,p.send_packet_id,p.packet_number,p.customer_name,p.packet_remark,"
                        + " i.item_name,i.sent_quantity,i.item_remark,r.received_quantity,r.receive_item_remark FROM "
                        + DBStructure.RECEIVE_ACTIONS_TABLE + " a LEFT JOIN " + DBStructure.GODOWNS_TABLE + " g ON g.godown_id=a.receiving_godown_id"
                        + " LEFT JOIN " + DBStructure.GODOWNS_TABLE + " gp ON gp.godown_id=g.parent_godown_id LEFT JOIN "
                        + DBStructure.RECEIVE_ITEMS_TABLE + " r ON r.receive_action_id=a.receive_action_id LEFT JOIN "
                        + DBStructure.SEND_ITEMS_TABLE + " i ON i.send_item_id=r.send_item_id LEFT JOIN "
                        + DBStructure.SEND_PACKETS_TABLE + " p ON p.send_packet_id=i.send_packet_id"
                        + (actionId != null ? " WHERE a.receive_action_id=?" : " WHERE p.customer_name=?")
                        + " ORDER BY a.receive_action_id DESC,p.packet_number,p.send_packet_id,i.item_number,r.receive_item_id";
                Map<Long, Entry> entries = new LinkedHashMap<>();
                try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
                    if (actionId != null) s.setLong(1, actionId); else s.setString(1, customer);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            long id = r.getLong("receive_action_id"); Entry entry = entries.get(id);
                            if (entry == null) {
                                entry = new Entry(); entry.id = id; entry.lot = r.getLong("send_lot_id");
                                entry.date = r.getString("receive_date"); entry.user = r.getString("received_by_user");
                                entry.remark = r.getString("receive_lot_remark");
                                entry.godown = godownName(r.getString("parent_name"), r.getString("godown_name")); entries.put(id, entry);
                            }
                            long packetId = r.getLong("send_packet_id"); if (r.wasNull()) continue;
                            Packet packet = entry.packets.get(packetId);
                            if (packet == null) {
                                packet = new Packet(); packet.number = r.getInt("packet_number");
                                packet.customer = r.getString("customer_name"); packet.remark = r.getString("packet_remark");
                                entry.packets.put(packetId, packet);
                            }
                            packet.rows.add(new Object[]{r.getString("item_name"),r.getInt("sent_quantity"),r.getInt("received_quantity"),
                                    r.getString("item_remark"),r.getString("receive_item_remark")});
                        }
                    }
                }
                return new ArrayList<>(entries.values());
            }
            protected void done() {
                if (closed || request != dataRequest) return;
                try { displayEntries(get()); } catch (Exception e) { showError(e); }
            }
        }.execute();
    }
    private void displayEntries(List<Entry> entries) {
        packetListPanel.removeAll();
        if (entries.isEmpty()) { message("No received packets found."); return; }
        for (Entry entry : entries) {
            JPanel summary = new JPanel(); summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
            summary.setBorder(BorderFactory.createTitledBorder("Receive " + entry.id + " / Send lot " + entry.lot));
            summary.add(valueField("Receive date", entry.date)); summary.add(valueField("Received at", entry.godown));
            summary.add(valueField("Received by", entry.user)); summary.add(valueField("Receive remark", entry.remark));
            summary.setAlignmentX(0); summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, summary.getPreferredSize().height));
            packetListPanel.add(summary);
            for (Packet packet : entry.packets.values()) {
                JPanel card = new JPanel(new BorderLayout(8, 8)); card.setAlignmentX(0);
                card.setBorder(BorderFactory.createTitledBorder("Packet " + packet.number));
                JPanel details = new JPanel(); details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
                details.add(valueField("Customer", packet.customer)); details.add(valueField("Packet remark", packet.remark));
                card.add(details, BorderLayout.NORTH);
                DefaultTableModel model = new DefaultTableModel(packet.rows.toArray(new Object[0][]),
                        new String[]{"Item name","Sent quantity","Received quantity","Send remark","Receive remark"}) {
                    public boolean isCellEditable(int row, int column) { return false; }
                    public Class<?> getColumnClass(int column) { return column == 1 || column == 2 ? Integer.class : String.class; }
                };
                JTable table = new JTable(model); table.setRowHeight(26); table.setFillsViewportHeight(true);
                table.getTableHeader().setReorderingAllowed(false);
                JScrollPane items = new JScrollPane(table); items.getVerticalScrollBar().setUnitIncrement(26);
                card.add(items, BorderLayout.CENTER); card.setPreferredSize(new Dimension(700, 220));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
                packetListPanel.add(Box.createVerticalStrut(8)); packetListPanel.add(card);
            }
            packetListPanel.add(Box.createVerticalStrut(16));
        }
        packetListPanel.revalidate(); packetListPanel.repaint();
        SwingUtilities.invokeLater(() -> { if (!closed) packetScrollPane.getVerticalScrollBar().setValue(0); });
    }
    private static JPanel valueField(String label, String data) {
        JPanel row = new JPanel(new BorderLayout(8, 0)); JLabel caption = new JLabel(label);
        caption.setPreferredSize(new Dimension(110, 24));
        JTextField field = new JTextField(data == null ? "" : data); field.setEditable(false);
        row.add(caption, BorderLayout.WEST); row.add(field, BorderLayout.CENTER); return row;
    }
    private void message(String text) {
        packetListPanel.removeAll(); packetListPanel.add(new JLabel(text)); packetListPanel.revalidate(); packetListPanel.repaint();
    }
    private void showError(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause(); message("Could not load receive diary.");
        JOptionPane.showMessageDialog(this, "Could not load receive diary: " + cause.getMessage(), "Receive Diary", JOptionPane.ERROR_MESSAGE);
    }
    private static String godownName(String parent, String name) {
        if (name == null) return ""; return parent == null || parent.isEmpty() ? name : parent + " / " + name;
    }
    private static Connection openConnection() throws SQLException {
        Connection c = MyClass.createConnection(); if (c == null) throw new SQLException("Could not connect to database."); return c;
    }
    private static final class Option {
        final Long id; final String label;
        Option(Long id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
    private static final class Filters {
        final List<Option> godowns = new ArrayList<>(); final List<String> customers = new ArrayList<>();
    }
    private static final class Entry {
        long id, lot; String date, user, remark, godown;
        final Map<Long, Packet> packets = new LinkedHashMap<>();
    }
    private static final class Packet {
        int number; String customer, remark; final List<Object[]> rows = new ArrayList<>();
    }
}
