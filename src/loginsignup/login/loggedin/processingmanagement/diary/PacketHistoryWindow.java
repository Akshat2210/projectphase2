package loginsignup.login.loggedin.processingmanagement.diary;

import mainpack.MyClass;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** The same read-only lineage is used by both processing diaries. */
public final class PacketHistoryWindow extends JDialog {
    private final JLabel status = new JLabel("Loading packet history...");
    private final JButton print = new JButton("Print");
    private final String database = MyClass.login.getDatabase();
    private final long packetId;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{
            "Item / design", "Send item", "Parent send item", "Event", "Date", "Send lot / packet",
            "Customer", "From", "To / received at", "Quantity", "User", "Order slip", "Remark"
    }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private PacketHistoryWindow(Window owner, long packetId) {
        super(owner, "Packet history", ModalityType.MODELESS);
        this.packetId = packetId;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(status, BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 || i == 12 ? 220 : 140);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        print.setEnabled(false);
        print.addActionListener(e -> printHistory());
        buttons.add(print);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(1150, 600);
        setLocationRelativeTo(owner);
        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() throws SQLException { return fetchHistory(packetId); }
            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    List<Object[]> rows = get();
                    for (Object[] row : rows) model.addRow(row);
                    print.setEnabled(!rows.isEmpty());
                    status.setText(rows.isEmpty() ? "No history found for this packet."
                            : "Complete item flow, including all forwarding branches and receive audits. * marks the selected packet. Receive counts are separate audit entries.");
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Could not load packet history.");
                    JOptionPane.showMessageDialog(PacketHistoryWindow.this, cause.getMessage(), "Packet history", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void printHistory() {
        // Capture the displayed data on the EDT; exporting never queries or changes records.
        List<Object[]> rows = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            Object[] values = new Object[model.getColumnCount()];
            for (int column = 0; column < values.length; column++) values[column] = model.getValueAt(row, column);
            rows.add(values);
        }
        print.setEnabled(false);
        new SwingWorker<java.nio.file.Path, Void>() {
            @Override protected java.nio.file.Path doInBackground() throws Exception {
                return PacketHistoryPdf.save(database, packetId, rows);
            }
            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    java.nio.file.Path pdf = get();
                    JOptionPane.showMessageDialog(PacketHistoryWindow.this, "PDF saved to:\n" + pdf
                            + "\n\nAfter OK, the browser print dialog will be requested.", "Packet history", JOptionPane.INFORMATION_MESSAGE);
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() throws Exception {
                            loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint.openPrintDialog(pdf);
                            return null;
                        }
                        @Override protected void done() {
                            if (!isDisplayable()) return;
                            print.setEnabled(true);
                            try { get(); }
                            catch (Exception e) {
                                Throwable cause = e.getCause() == null ? e : e.getCause();
                                JOptionPane.showMessageDialog(PacketHistoryWindow.this,
                                        "The PDF is saved.\n" + cause.getMessage() + "\n\nPDF: " + pdf,
                                        "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    }.execute();
                } catch (Exception e) {
                    print.setEnabled(true);
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(PacketHistoryWindow.this, "Could not create packet history PDF:\n" + cause.getMessage(),
                            "Packet history", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Attach to child controls too: Swing mouse events do not bubble to the card. */
    public static void attach(JComponent card, long packetId) {
        MouseAdapter listener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    new PacketHistoryWindow(SwingUtilities.getWindowAncestor(card), packetId).setVisible(true);
                }
            }
        };
        attachChildren(card, listener);
        card.setToolTipText("Double-click to view complete item history");
    }

    private static void attachChildren(Component component, MouseAdapter listener) {
        if (component instanceof JScrollBar || component instanceof javax.swing.table.JTableHeader) return;
        component.addMouseListener(listener);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) attachChildren(child, listener);
        }
    }

    private static List<Object[]> fetchHistory(long packetId) throws SQLException {
        // Walk up from the packet's actual item IDs, then down from each original item.
        // Names/designs are not unique enough to identify a processing lineage.
        String sql = "WITH RECURSIVE ancestry AS ("
                + " SELECT send_item_id,parent_send_item_id FROM send_items WHERE send_packet_id=?"
                + " UNION DISTINCT SELECT p.send_item_id,p.parent_send_item_id FROM send_items p"
                + " JOIN ancestry a ON p.send_item_id=a.parent_send_item_id),"
                + " flow AS (SELECT i.send_item_id,i.parent_send_item_id,i.send_item_id root_id,0 depth"
                + " FROM send_items i JOIN ancestry a ON a.send_item_id=i.send_item_id WHERE i.parent_send_item_id IS NULL"
                + " UNION ALL SELECT i.send_item_id,i.parent_send_item_id,f.root_id,f.depth+1"
                + " FROM send_items i JOIN flow f ON i.parent_send_item_id=f.send_item_id)"
                + " SELECT i.*,f.depth,l.send_date,l.created_by_user,p.send_lot_id,p.packet_number,p.customer_name,"
                + " sg.godown_name source_name,sp.godown_name source_parent,dg.godown_name destination_name,dp.godown_name destination_parent,"
                + " r.receive_item_id,r.received_quantity,r.receive_item_remark,a.receive_action_id,a.receive_date,a.received_by_user,"
                + " rg.godown_name receive_name,rp.godown_name receive_parent"
                + " FROM flow f JOIN send_items i ON i.send_item_id=f.send_item_id"
                + " JOIN send_packets p ON p.send_packet_id=i.send_packet_id JOIN send_lots l ON l.send_lot_id=p.send_lot_id"
                + " LEFT JOIN godowns sg ON sg.godown_id=i.source_godown_id LEFT JOIN godowns sp ON sp.godown_id=sg.parent_godown_id"
                + " LEFT JOIN godowns dg ON dg.godown_id=i.destination_godown_id LEFT JOIN godowns dp ON dp.godown_id=dg.parent_godown_id"
                + " LEFT JOIN receive_items r ON r.send_item_id=i.send_item_id LEFT JOIN receive_actions a ON a.receive_action_id=r.receive_action_id"
                + " LEFT JOIN godowns rg ON rg.godown_id=a.receiving_godown_id LEFT JOIN godowns rp ON rp.godown_id=rg.parent_godown_id"
                + " ORDER BY f.root_id,f.depth,i.send_item_id,a.receive_date,a.receive_action_id,r.receive_item_id";
        List<Object[]> rows = new ArrayList<>();
        Connection connection = MyClass.createConnection();
        if (connection == null) throw new SQLException("Could not connect to the database.");
        try (Connection c = connection; PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setLong(1, packetId);
            try (ResultSet r = statement.executeQuery()) {
                long previous = -1;
                while (r.next()) {
                    long itemId = r.getLong("send_item_id");
                    String item = text(r.getString("item_name"));
                    String design = text(r.getString("design_id"));
                    if (!design.isEmpty()) item += " / " + design;
                    String lotPacket = r.getLong("send_lot_id") + " / " + r.getInt("packet_number")
                            + (r.getLong("send_packet_id") == packetId ? " *" : "");
                    if (itemId != previous) {
                        rows.add(new Object[]{item,itemId,r.getObject("parent_send_item_id"),
                                r.getObject("parent_send_item_id") == null ? "Sent" : "Forwarded",r.getDate("send_date"),lotPacket,
                                r.getString("customer_name"),godown(r,"source"),godown(r,"destination"),r.getInt("sent_quantity"),
                                r.getString("created_by_user"),r.getObject("order_slip_id"),r.getString("item_remark")});
                        previous = itemId;
                    }
                    if (r.getObject("receive_item_id") != null) {
                        rows.add(new Object[]{item,itemId,r.getObject("parent_send_item_id"),"Receive audit #" + r.getLong("receive_action_id"),
                                r.getDate("receive_date"),lotPacket,r.getString("customer_name"),"",godown(r,"receive"),
                                r.getInt("received_quantity"),r.getString("received_by_user"),r.getObject("order_slip_id"),r.getString("receive_item_remark")});
                    }
                }
            }
        }
        return rows;
    }

    private static String godown(ResultSet result, String prefix) throws SQLException {
        String parent = text(result.getString(prefix + "_parent"));
        String name = text(result.getString(prefix + "_name"));
        return parent.isEmpty() ? name : parent + " / " + name;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
