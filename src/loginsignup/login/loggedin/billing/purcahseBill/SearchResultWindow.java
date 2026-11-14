package loginsignup.login.loggedin.billing.purcahseBill;

import mainpack.MyClass;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.*;
import java.sql.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static utils.DBStructure.*;

public class SearchResultWindow extends JFrame {
    private JPanel panel1;
    private JButton backButton;
    private JButton button2;
    private JTable dataTable;
    private JLabel cutomerName;
    private JLabel panaTypeLabel;
    private JComboBox<String> selectionComboBox;
    private final PurchaseBill purchaseBill;

    public SearchResultWindow(PurchaseBill purchaseBill) {

        this.purchaseBill = purchaseBill;
    }

    public void init() {
        setContentPane(panel1);
        setTitle(MyClass.TITLE + ": PURCHASE INVENTORY");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        selectionComboBox.setModel(new DefaultComboBoxModel<>(new String[]{"Inventory"}));
        panaTypeLabel.setText("Purchase items");
        button2.setText("Add item");
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"design id", "Item Name", "Quantity", "Purchase Price", "Sell Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        dataTable.setModel(model);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        dataTable.setRowSorter(sorter);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backButton.addActionListener(e -> closeSearch());
        button2.addActionListener(e -> activateSelection());
        javax.swing.Timer selectionDelay = new javax.swing.Timer(200, e -> refreshResults());
        selectionDelay.setRepeats(false);
        selectionComboBox.addActionListener(e -> selectionDelay.restart());
        dataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) activateSelection();
            }
        });
        dataTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "addInventoryItem");
        dataTable.getActionMap().put("addInventoryItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { activateSelection(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { closeSearch(); }
        });
        refreshResults();
        pack();
    }

    public void setCustomerName(String name) { cutomerName.setText(name); }

    public void refreshResults() {
        updateDisplayedInventory(Collections.emptyMap());
    }

    /**
     * Shows the current database stock plus quantities entered on the unsaved
     * purchase bill, so the search window remains consistent while editing.
     */
    public void updateDisplayedInventory(Map<String, Integer> pendingPurchases) {
        DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
        model.setRowCount(0);
        String query = "SELECT " + INVENTORY_DESIGN_ID + ", " + INVENTORY_ITEM_NAME + ", " +
                INVENTORY_TOTAL_QUANTITY + ", " + INVENTORY_BUY_PRICE + ", " + INVENTORY_SELL_PRICE +
                " FROM " + INVENTORY_TABLE + " ORDER BY " + INVENTORY_DESIGN_ID;
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String designId = utils.TextNormalization.trimNullable(rows.getString(INVENTORY_DESIGN_ID));
                designId = designId == null ? "" : designId.trim();
                int displayedQuantity = rows.getInt(INVENTORY_TOTAL_QUANTITY)
                        + pendingPurchases.getOrDefault(designId, 0);
                model.addRow(new Object[]{designId, utils.TextNormalization.trimNullable(rows.getString(INVENTORY_ITEM_NAME)),
                        displayedQuantity, rows.getBigDecimal(INVENTORY_BUY_PRICE),
                        rows.getBigDecimal(INVENTORY_SELL_PRICE)});
            }
            dataTable.revalidate();
            dataTable.repaint();
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not load inventory: " + exception.getMessage(),
                    "Inventory Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void activateSelection() {
        int row = dataTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an inventory item first.");
            return;
        }
        row = dataTable.convertRowIndexToModel(row);
        DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
        String text = JOptionPane.showInputDialog(this,
                "How many " + model.getValueAt(row, 1) + "(s) would you like to purchase?",
                "Quantity Selection", JOptionPane.QUESTION_MESSAGE);
        if (text == null) return;
        try {
            int quantity = Integer.parseInt(text.trim());
            if (quantity <= 0) throw new NumberFormatException();
            purchaseBill.addInventoryItem(model.getValueAt(row, 0), model.getValueAt(row, 1),
                    model.getValueAt(row, 3), quantity);
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Enter a whole quantity greater than zero.");
        }
    }

    private void closeSearch() {
        dispose();
        purchaseBill.setExtendedState(JFrame.MAXIMIZED_BOTH);
        purchaseBill.toFront();
    }
}
