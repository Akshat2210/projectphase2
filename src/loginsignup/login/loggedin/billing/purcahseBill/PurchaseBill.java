package loginsignup.login.loggedin.billing.purcahseBill;

import loginsignup.login.loggedin.billing.BillingScreen;
import mainpack.MyClass;
import utils.CONSTANTS;
import utils.UtilityMethods;

import static utils.DBStructure.*;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class PurchaseBill extends JFrame {
    private static final int SUCCESS_DONE = 18 * 2;
    private final BillingScreen parentWindow;
    private JPanel panel;
    private int billid;
    private Connection con;
    private SearchResultWindow searchResultWindow;

    public int getBillid() {
        return billid;
    }

    public void setBillid(int billid) {
        this.billid = billid;
    }

    private JButton backButton;
    private JComboBox<String> customerComboBox;
    private JComboBox<String> dateComboBox;
    private JLabel billidLabel;
    private JTable billTable;
    private JButton submitButton;
    Vector<String> headers;
    DefaultTableModel model;
    int quantityIndex;
    int snoIndex;
    int totalIndex;
    int rawCostIndex;
    int sellPriceIndex;
    int designIDIndex;
    int itemNameIndex;


    private boolean returnIsCellEditable(int row, int col, int[][] extraIndices) {
        if (col == snoIndex) return false;
        if (extraIndices != null) {
            for (int[] index : extraIndices) {
                if (index[0] == row && index[1] == col) return false;
                if (index[0] == -1 && index[1] == col) return false;
            }
        }
        return true;
    }

    public PurchaseBill(BillingScreen  billingScreen) {
    parentWindow=billingScreen;
    }

    private boolean designIDexists(String designID) {
        designID = designID == null ? "" : designID.trim();
        String query = "SELECT 1 FROM " + INVENTORY_TABLE + " WHERE TRIM(" + INVENTORY_DESIGN_ID + ") = ? LIMIT 1";
        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setString(1, designID);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // true if at least one row is found
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(PurchaseBill.this, e.getMessage());
            e.printStackTrace();
            return false; // or throw a custom exception if you prefer
        }
    }

    boolean insertData() {

        String customerName = customerComboBox.getSelectedItem() == null ? "" : customerComboBox.getSelectedItem().toString();
        String billDetailsTableQuery = "INSERT INTO " + BILLDETAILS_TABLE + " (" + BILLDETAILS_BILL_ID + ", " + BILLDETAILS_SNO + ", " + BILLDETAILS_DESIGN_ID + ", " + BILLDETAILS_ITEM_NAME + ", " + BILLDETAILS_QUANTITY + ", " + BILLDETAILS_RAW_COST + ", " + BILLDETAILS_TOTAL_FINAL_COST + ", " + "" + BILLDETAILS_ORDER_TYPE + ", " + BILLDETAILS_LABOUR_COST + ", " + BILLDETAILS_TOTAL_BASE_COSTING + ", " + BILLDETAILS_GOLD_RATE + ", " + BILLDETAILS_GOLD_PLATING_WEIGHT + ", " + BILLDETAILS_TOTAL_GOLD_COST + ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String billsTableQuery = "UPDATE " + BILLS_TABLE + " SET " + BILLS_DATE + " = ?, " + BILLS_CUSTOMER_NAME + " = ?, " + BILLS_BILL_NUMBER + " = ? " + "WHERE " + BILLS_BILL_ID + " = ?";
        String updateInventoryQuery = "UPDATE " + INVENTORY_TABLE + " SET " + INVENTORY_TOTAL_QUANTITY + " = " + INVENTORY_TOTAL_QUANTITY + " + ? WHERE TRIM(" + INVENTORY_DESIGN_ID + ") = ?";
        String insertInventoryQuery = "INSERT INTO " + INVENTORY_TABLE + "(" + INVENTORY_DESIGN_ID + ", " + INVENTORY_TOTAL_QUANTITY + ", " + INVENTORY_ITEM_NAME + ", " + INVENTORY_BUY_PRICE + ", " + INVENTORY_SELL_PRICE + ") VALUES (?, ?, ?, ?, ?)";
        String updateOtherQuery = "update " + INVENTORY_TABLE + " set " + INVENTORY_BUY_PRICE + " = ?, " + INVENTORY_SELL_PRICE + " = ?, " + INVENTORY_ITEM_NAME + " = ? where TRIM(" + INVENTORY_DESIGN_ID + ") = ?";
        String customerTableQuery = "update " + CUSTOMERS_TABLE + " set " + CUSTOMERS_BALANCE + " = " + CUSTOMERS_BALANCE + " - ? where " + CUSTOMERS_CUSTOMER_NAME + " = ?";
        String insertJournalEntryQuery = "INSERT INTO " + JOURNAL_ENTRY_TABLE + " (" +
                JOURNAL_ENTRY_TRANSACTION_TYPE + ", " +
                JOURNAL_ENTRY_DATE + ", " +
                JOURNAL_ENTRY_ACCOUNT_NAME + ", " +
                JOURNAL_ENTRY_BILL_ID + ", " +
                JOURNAL_ENTRY_TRANSACTION_ID + ", " +
                JOURNAL_ENTRY_CREDIT + ", " +
                JOURNAL_ENTRY_DEBIT + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement billsTableStatement = con.prepareStatement(billsTableQuery); PreparedStatement customersTableStatement = con.prepareStatement(customerTableQuery); PreparedStatement updateStmt = con.prepareStatement(updateInventoryQuery); PreparedStatement insertStmt = con.prepareStatement(insertInventoryQuery); PreparedStatement billStmt = con.prepareStatement(billDetailsTableQuery); PreparedStatement updateRawPriceStatement = con.prepareStatement(updateOtherQuery);
             PreparedStatement journalEntryStatement = con.prepareStatement(insertJournalEntryQuery);
        ) {
            con.setAutoCommit(false);
            int billNumber = UtilityMethods.getBillNumber(con, CONSTANTS.PURCHASE_ENUM);

            billsTableStatement.setTimestamp(1, Timestamp.valueOf(UtilityMethods.getDate(dateComboBox.getSelectedItem())));
            billsTableStatement.setString(2, customerName);
            billsTableStatement.setInt(3, billNumber);
            billsTableStatement.setInt(4, billid);
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (int i = 0; i < model.getRowCount() - 1; i++) {
                Object snoObj = model.getValueAt(i, snoIndex);
                Object designIDObj = model.getValueAt(i, designIDIndex);
                Object itemNameObj = model.getValueAt(i, itemNameIndex);
                Object quantityObj = model.getValueAt(i, quantityIndex);
                Object rawCostObj = model.getValueAt(i, rawCostIndex);
                Object totalCostObj = model.getValueAt(i, totalIndex);
                int sno = snoObj == null ? 0 : Integer.parseInt(snoObj.toString());
                String designID = designIDObj == null ? "" : designIDObj.toString().trim();
                String itemName = itemNameObj == null ? "" : itemNameObj.toString();
                int quantity = quantityObj == null || quantityObj.toString().isEmpty() ? 0 : Integer.parseInt(quantityObj.toString());
                BigDecimal rawCost = rawCostObj == null ? BigDecimal.ZERO : new BigDecimal(rawCostObj.toString());
                BigDecimal totalCost = totalCostObj == null || totalCostObj.toString().isEmpty() ? BigDecimal.ZERO : new BigDecimal(totalCostObj.toString());
                
                billStmt.setInt(1, getBillid());
                billStmt.setInt(2, sno);
                billStmt.setString(3, designID);
                billStmt.setString(4, itemName);
                billStmt.setInt(5, -quantity);
                billStmt.setBigDecimal(6, rawCost);
                billStmt.setBigDecimal(7, totalCost.negate());
                grandTotal = grandTotal.add(totalCost);
                // Fill remaining required fields with defaults
                billStmt.setString(8, "purchase"); // example OrderType
                billStmt.setBigDecimal(9, BigDecimal.ZERO); // LabourCost
                billStmt.setBigDecimal(10, BigDecimal.ZERO); // TotalBaseCosting
                billStmt.setBigDecimal(11, BigDecimal.ZERO); // GoldRate
                billStmt.setBigDecimal(12, BigDecimal.ZERO); // GoldPlatingWeight
                billStmt.setBigDecimal(13, BigDecimal.ZERO); // TotalGoldCost
                billStmt.addBatch();
                designID = getDesignIdValue(i);
                quantity = getIntegerValue(model.getValueAt(i, quantityIndex));
                itemName = model.getValueAt(i, itemNameIndex) == null ? "" : model.getValueAt(i, itemNameIndex).toString();
                BigDecimal price = getDecimalValue(model.getValueAt(i, rawCostIndex));
                BigDecimal sellPrice = getDecimalValue(model.getValueAt(i, sellPriceIndex));
                System.out.println(designIDexists(designID));
                if (designIDexists(designID)) {
                    if (designID.contentEquals("")) continue;
                    updateStmt.setInt(1, quantity);
                    updateStmt.setString(2, designID);
                    updateRawPriceStatement.setBigDecimal(1, price);
                    updateRawPriceStatement.setBigDecimal(2, sellPrice);
                    updateRawPriceStatement.setString(3, itemName);
                    updateRawPriceStatement.setString(4, designID);
                    updateRawPriceStatement.addBatch();
                    updateStmt.addBatch();

                } else {
                    if (designID.contentEquals("")) continue;
                    insertStmt.setString(1, designID);
                    insertStmt.setInt(2, quantity);
                    insertStmt.setString(3, itemName);
                    insertStmt.setBigDecimal(4, price);
                    insertStmt.setBigDecimal(5, sellPrice);
                    insertStmt.addBatch();
                }
            }

            // Debit inventory and credit the supplier/customer for the full purchase bill.
            journalEntryStatement.setString(1, CONSTANTS.PURCHASE_ENUM);
            journalEntryStatement.setDate(2, java.sql.Date.valueOf(UtilityMethods.getDate(dateComboBox.getSelectedItem()).toLocalDate()));
            journalEntryStatement.setString(3, "Inventory");
            journalEntryStatement.setInt(4, getBillid());
            journalEntryStatement.setNull(5, Types.INTEGER);
            journalEntryStatement.setBigDecimal(6, BigDecimal.ZERO);
            journalEntryStatement.setBigDecimal(7, grandTotal);
            journalEntryStatement.addBatch();

            journalEntryStatement.setString(1, CONSTANTS.PURCHASE_ENUM);
            journalEntryStatement.setDate(2, java.sql.Date.valueOf(UtilityMethods.getDate(dateComboBox.getSelectedItem()).toLocalDate()));
            journalEntryStatement.setString(3, customerName);
            journalEntryStatement.setInt(4, getBillid());
            journalEntryStatement.setNull(5, Types.INTEGER);
            journalEntryStatement.setBigDecimal(6, grandTotal);
            journalEntryStatement.setBigDecimal(7, BigDecimal.ZERO);
            journalEntryStatement.addBatch();

            customersTableStatement.setBigDecimal(1, grandTotal);
            customersTableStatement.setString(2, customerName);
            customersTableStatement.executeUpdate();
            billsTableStatement.executeUpdate();
            billStmt.executeBatch();
            updateStmt.executeBatch();
            updateRawPriceStatement.executeBatch();
            insertStmt.executeBatch();
            journalEntryStatement.executeBatch();
            con.commit();
            JOptionPane.showMessageDialog(PurchaseBill.this, "bill created successfully");
            return true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + ex.getMessage());
            try {
                con.rollback();
                con.close();
                dispose();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
            return false;
        }
    }

    private int fetchBillId() {
        String billsTableQuery = "INSERT INTO " + BILLS_TABLE + " (" + BILLS_BILL_TYPE + ") VALUES (?)";

        try (PreparedStatement billsTableStatement = con.prepareStatement(
                billsTableQuery,
                Statement.RETURN_GENERATED_KEYS
        )) {
            billsTableStatement.setString(1, CONSTANTS.PURCHASE_ENUM);
            int insertedRows = billsTableStatement.executeUpdate();

            if (insertedRows != 1) {
                throw new SQLException("Could not create the bill header.");
            }

            try (ResultSet generatedKeys = billsTableStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    public void init() {
        setContentPane(panel);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        headers = new Vector<>();
        headers.add("Sno");
        headers.add("designID");
        headers.add("item name");
        headers.add("Quantity");
        headers.add("Raw cost");
        headers.add("Sell Price");

        headers.add("total");
        designIDIndex = headers.indexOf("designID");
        quantityIndex = headers.indexOf("Quantity");
        rawCostIndex = headers.indexOf("Raw cost");
        sellPriceIndex = headers.indexOf("Sell Price");
        totalIndex = headers.indexOf("total");
        snoIndex = headers.indexOf("Sno");
        itemNameIndex = headers.indexOf("item name");
        backButton.addActionListener(e -> {
            if (searchResultWindow != null) searchResultWindow.dispose();
            setVisible(false);
            dispose();
            if (con != null) {
                {
                    try {
                        con.rollback();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    try {
                        con.close();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }
            PurchaseBill purchaseBill = new PurchaseBill(parentWindow);
            parentWindow.setVisible(true);
        });
        try {
            con = MyClass.createConnection();
            con.setAutoCommit(false);
        } catch (SQLException e) {
            int option = JOptionPane.showConfirmDialog(PurchaseBill.this, "connection failed\n" + e.getMessage() + "\ntry again?");
            if (option == JOptionPane.YES_OPTION) {
                init();
            } else {
                backButton.doClick();
                e.printStackTrace();
            }
        }
        model = new DefaultTableModel(headers, 1) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return returnIsCellEditable(row, column, null);
            }
        };
        model.setValueAt(1, 0, snoIndex);
        billTable.setModel(model);
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println(billid);

                if (customerComboBox.getSelectedItem() != null && customerComboBox.getSelectedIndex() != 0) {
                    if(!allQuantityValid()) {
                        return;
                    }
                    if (insertData() ) {
                        if (searchResultWindow != null) searchResultWindow.dispose();

                        dispose();
                        try {
                            con.close();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                        PurchaseBill purchaseBill = new PurchaseBill(parentWindow);
                        purchaseBill.init();
                        purchaseBill.setVisible(true);
                    }

                } else {
                    JOptionPane.showMessageDialog(PurchaseBill.this, "select a customer");
                }


            }
        });

        model.addTableModelListener(e -> {

            int col = e.getColumn();
            int row = e.getFirstRow();
            if (col == -1 || row == -1) {
                System.out.println("unexpected return from modellisterer");
                Thread.dumpStack();
                return;
            }
            TableModelListener[] listeners = UtilityMethods.removeModelListener(model);
            double quantity = 0;
            BigDecimal rawCost = BigDecimal.ZERO;
            //ccase of unintended calls of the model
            TableColumnModel columnModel = billTable.getColumnModel();

            if (col == quantityIndex) {
                try {
                    quantity = getIntegerValue(model.getValueAt(row, quantityIndex));
                    if (quantity == 0) model.setValueAt("", row, quantityIndex);

                } catch (NumberFormatException exception) {
                    model.setValueAt("", row, quantityIndex);
                }
                updateDisplayedInventory();
                updateTotal(row);

            }
            if (col == rawCostIndex) {
                try {
                    rawCost = getDecimalValue(model.getValueAt(row, rawCostIndex));

                    if (rawCost.signum() == 0) model.setValueAt("", row, rawCostIndex);
                } catch (NumberFormatException ex) {
                    model.setValueAt("", row, rawCostIndex);
                }
                String designId = getDesignIdValue(row);
                if (!designId.isEmpty() && designIDexists(designId)) {
                    JOptionPane.showMessageDialog(PurchaseBill.this, "raw getBuyPrice will be update in inventory, re enter design id to avoid this");
                }
                setDefaultSellPrice(row);
                updateTotal(row);
            }
            if (col == designIDIndex) {
                String designId = getDesignIdValue(row);
                model.setValueAt(designId, row, designIDIndex);
                fetchAndAppend(designId, row);
                updateDisplayedInventory();
                billTable.repaint();
            }
            if (col == itemNameIndex) {
                if (designIDexists(getDesignIdValue(row)))
                    JOptionPane.showMessageDialog(PurchaseBill.this, "आइटम का नाम इन्वन्टोरी मे बदल जाएगा, कृपया नाम न बदलने की स्थिति मे दुबारा से designid डालें ");
                String stringValueUperCase = getStringValue(model.getValueAt(row, itemNameIndex)).toUpperCase();
                String valueToReplace = MyClass.codeToItemName.get(stringValueUperCase);
                if (valueToReplace != null) {
                    model.setValueAt(valueToReplace, row, itemNameIndex);
                }
            }

            doRowManipulation(row);
            UtilityMethods.addModelListeners(listeners, model);
        });
// Map the Delete key
        InputMap inputMap = billTable.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = billTable.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRow");

        actionMap.put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (model.getRowCount() == 1) return;
                model.removeRow(billTable.getSelectedRow());
                reAssignSno();
                updateDisplayedInventory();
            }
        });


        UtilityMethods.generateAndAddNames(customerComboBox);
        UtilityMethods.generateAndAddDates(dateComboBox, false);

        javax.swing.Timer customerSelectionDelay = new javax.swing.Timer(200, e -> {
            if (customerComboBox.getSelectedIndex() <= 0) {
                if (searchResultWindow != null) searchResultWindow.dispose();
                setExtendedState(JFrame.MAXIMIZED_BOTH);
                return;
            }
            if (searchResultWindow == null || !searchResultWindow.isDisplayable()) {
                searchResultWindow = new SearchResultWindow(this);
                searchResultWindow.init();
                searchResultWindow.setAutoRequestFocus(false);
            }
            searchResultWindow.setCustomerName(String.valueOf(customerComboBox.getSelectedItem()));
            searchResultWindow.setVisible(true);
            setExtendedState(JFrame.NORMAL);
            searchResultWindow.setExtendedState(JFrame.NORMAL);
            UtilityMethods.splitFrame(this, searchResultWindow, UtilityMethods.VERTI_SPLIT);
        });
        customerSelectionDelay.setRepeats(false);
        customerComboBox.addActionListener(e -> customerSelectionDelay.restart());

        setBillid(fetchBillId());
        billidLabel.setText("bill id:" + getBillid());
        setExtendedState(JFrame.MAXIMIZED_BOTH);

    }
    void addInventoryItem(Object designId, Object itemName, Object purchasePrice, int quantity) {
        if (billTable.isEditing() && !billTable.getCellEditor().stopCellEditing()) return;
        TableModelListener[] listeners = UtilityMethods.removeModelListener(model);
        try {
            int row = model.getRowCount() - 1;
            if (!isRowEmpty(row)) {
                model.addRow(new Object[model.getColumnCount()]);
                row++;
            }
            model.setValueAt(row + 1, row, snoIndex);
            model.setValueAt(designId == null ? "" : designId.toString().trim(), row, designIDIndex);
            model.setValueAt(itemName, row, itemNameIndex);
            model.setValueAt(purchasePrice, row, rawCostIndex);
            model.setValueAt(quantity, row, quantityIndex);
            setDefaultSellPrice(row);
            updateTotal(row);
            doRowManipulation(row);
        } finally {
            UtilityMethods.addModelListeners(listeners, model);
        }
        updateDisplayedInventory();
        // The search window writes several values while the model listener is suspended.
        // Refresh the table once the complete row has been added so it is visible immediately.
        billTable.revalidate();
        billTable.repaint();
    }

    private boolean allQuantityValid() {
        DefaultTableModel model = (DefaultTableModel) billTable.getModel();
        int dataRowCount = model.getRowCount() - 1; // last row is the blank entry row

        if (dataRowCount <= 0) {
            JOptionPane.showMessageDialog(
                    PurchaseBill.this,
                    "Please add at least one item."
            );
            return false;
        }

        for (int row = 0; row < dataRowCount; row++) {
            Object quantityValue = model.getValueAt(row, quantityIndex);
            String quantityText = quantityValue == null
                    ? ""
                    : quantityValue.toString().trim();

            int quantity;
            try {
                quantity = Integer.parseInt(quantityText);
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(
                        PurchaseBill.this,
                        "Please enter a whole quantity greater than zero in row " + (row + 1) + "."
                );
                return false;
            }

            if (quantity <= 0) {
                JOptionPane.showMessageDialog(
                        PurchaseBill.this,
                        "Please enter a quantity greater than zero in row " + (row + 1) + "."
                );
                return false;
            }

            try {
                getDecimalValue(model.getValueAt(row, rawCostIndex));
                getDecimalValue(model.getValueAt(row, sellPriceIndex));
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(
                        PurchaseBill.this,
                        "Enter non-negative buy and sell prices with no more than two decimal places in row "
                                + (row + 1) + "."
                );
                return false;
            }
        }

        return true;
    }

    private String getStringValue(Object valueAt) {
        if (valueAt != null) return valueAt.toString();
        return "";
    }

    private void fetchAndAppend(String designID, int row) {
        designID = designID == null ? "" : designID.trim();
        String query = "select * from " + INVENTORY_TABLE + " where TRIM(" + INVENTORY_DESIGN_ID + ")=?";
        try (PreparedStatement preparedStatement = con.prepareStatement(query)) {
            preparedStatement.setString(1, designID);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                model.setValueAt(rs.getString(INVENTORY_ITEM_NAME), row, itemNameIndex);
                model.setValueAt(rs.getString(INVENTORY_BUY_PRICE), row, rawCostIndex);
                setDefaultSellPrice(row);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(PurchaseBill.this, e.getMessage());
            e.printStackTrace();

        }
    }


    private void doRowManipulation(int row) {
        if (row == model.getRowCount() - 1) {
            if (!isRowEmpty(row)) {
                model.setRowCount(model.getRowCount() + 1);
                model.setValueAt(getIntegerValue(model.getValueAt(row, snoIndex)) + 1, row + 1, snoIndex);
            }

        } else {
            if (isRowEmpty(row)) {
                model.removeRow(row);
                reAssignSno();
            }
        }

    }

    private void reAssignSno() {
        for (int i = 0; i < model.getRowCount(); i++) {
            model.setValueAt(i + 1, i, snoIndex);
        }
    }

    private String getDesignIdValue(int row) {
        return getStringValue(model.getValueAt(row, designIDIndex)).trim();
    }

    /** Updates the search window with database stock plus this bill's pending purchases. */
    private void updateDisplayedInventory() {
        if (searchResultWindow == null || !searchResultWindow.isDisplayable()) return;

        Map<String, Integer> pendingPurchases = new HashMap<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String designId = getDesignIdValue(row);
            if (designId.isEmpty()) continue;

            int quantity = getIntegerValue(model.getValueAt(row, quantityIndex));
            pendingPurchases.merge(designId, quantity, Integer::sum);
        }
        searchResultWindow.updateDisplayedInventory(pendingPurchases);
    }

    private boolean isRowEmpty(int row) {
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (model.getValueAt(row, i) != null && !model.getValueAt(row, i).toString().trim().isEmpty()) {
                if (i == snoIndex) continue;
                return false;
            }
        }
        return true;
    }


    private void updateTotal(int row) {
        int quantity = getIntegerValue(model.getValueAt(row, quantityIndex));
        BigDecimal rawCost = getDecimalValue(model.getValueAt(row, rawCostIndex));
        BigDecimal total = rawCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        model.setValueAt(total.signum() == 0 ? "" : total, row, totalIndex);
        model.fireTableRowsUpdated(row, row);
        billTable.repaint();
    }

    private void setDefaultSellPrice(int row) {
        Object currentSellPrice = model.getValueAt(row, sellPriceIndex);


        BigDecimal purchasePrice = getDecimalValue(model.getValueAt(row, rawCostIndex));
        if (purchasePrice.signum() > 0) {
            model.setValueAt(purchasePrice.multiply(new BigDecimal("1.15"))
                    .setScale(2, RoundingMode.HALF_UP), row, sellPriceIndex);
        }
    }

    private int getIntegerValue(Object value) {
        return (value != null && !value.toString().isEmpty()) ? Integer.parseInt(value.toString()) : 0;
    }

    private BigDecimal getDecimalValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) return BigDecimal.ZERO.setScale(2);
        BigDecimal decimal = new BigDecimal(value.toString().trim());
        if (decimal.signum() < 0 || decimal.scale() > 2) throw new NumberFormatException();
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }


}
