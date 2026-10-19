package loginsignup.login.loggedin.billing.purcahseBill;

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
import java.sql.*;
import java.util.Vector;

public class PurchaseBill extends JFrame {
    private static final int SUCCESS_DONE = 18 * 2;
    private JPanel panel;
    private int billid;
    private Connection con;

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

    public PurchaseBill() {

    }

    private boolean designIDexists(String designID) {
        String query = "SELECT 1 FROM " + INVENTORY_TABLE + " WHERE " + INVENTORY_DESIGN_ID + " = ? LIMIT 1";
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
        String updateInventoryQuery = "UPDATE " + INVENTORY_TABLE + " SET " + INVENTORY_TOTAL_QUANTITY + " = " + INVENTORY_TOTAL_QUANTITY + " + ? WHERE " + INVENTORY_DESIGN_ID + " = ?";
        String insertInventoryQuery = "INSERT INTO " + INVENTORY_TABLE + "(" + INVENTORY_DESIGN_ID + ", " + INVENTORY_TOTAL_QUANTITY + ", " + INVENTORY_ITEM_NAME + ", " + INVENTORY_BUY_PRICE + ", " + INVENTORY_SELL_PRICE + ") VALUES (?, ?, ?, ?, ?)";
        String updateOtherQuery = "update " + INVENTORY_TABLE + " set " + INVENTORY_BUY_PRICE + " = ?, " + INVENTORY_SELL_PRICE + " = ?, " + INVENTORY_ITEM_NAME + " = ? where " + INVENTORY_DESIGN_ID + " = ?";
        String customerTableQuery = "update " + CUSTOMERS_TABLE + " set " + CUSTOMERS_BALANCE + " = " + CUSTOMERS_BALANCE + " - ? where " + CUSTOMERS_CUSTOMER_NAME + " = ?";

        try (PreparedStatement billsTableStatement = con.prepareStatement(billsTableQuery); PreparedStatement customersTableStatement = con.prepareStatement(customerTableQuery); PreparedStatement updateStmt = con.prepareStatement(updateInventoryQuery); PreparedStatement insertStmt = con.prepareStatement(insertInventoryQuery); PreparedStatement billStmt = con.prepareStatement(billDetailsTableQuery); PreparedStatement updateRawPriceStatement = con.prepareStatement(updateOtherQuery);) {
            con.setAutoCommit(false);
            int billNumber = UtilityMethods.getBillNumber(con);

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
                String designID = designIDObj == null ? "" : designIDObj.toString();
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
                designID = model.getValueAt(i, designIDIndex) == null ? "" : model.getValueAt(i, designIDIndex).toString();
                quantity = getIntegerValue(model.getValueAt(i, quantityIndex));
                itemName = model.getValueAt(i, itemNameIndex) == null ? "" : model.getValueAt(i, itemNameIndex).toString();
                int price = model.getValueAt(i, rawCostIndex) == null ? 0 : getIntegerValue(model.getValueAt(i, rawCostIndex));
                int sellPrice = model.getValueAt(i, sellPriceIndex) == null ? 0 : getIntegerValue(model.getValueAt(i, sellPriceIndex));
                System.out.println(designIDexists(designID));
                if (designIDexists(designID)) {
                    if (designID.contentEquals("")) continue;
                    updateStmt.setInt(1, quantity);
                    updateStmt.setString(2, designID);
                    updateRawPriceStatement.setDouble(1, price);
                    updateRawPriceStatement.setDouble(2, sellPrice);
                    updateRawPriceStatement.setString(3, itemName);
                    updateRawPriceStatement.setString(4, designID);
                    updateRawPriceStatement.addBatch();
                    updateStmt.addBatch();

                } else {
                    if (designID.contentEquals("")) continue;
                    insertStmt.setString(1, designID);
                    insertStmt.setInt(2, quantity);
                    insertStmt.setString(3, itemName);
                    insertStmt.setInt(4, price);
                    insertStmt.setInt(5, sellPrice);
                    insertStmt.addBatch();
                }
            }
            customersTableStatement.setBigDecimal(1, grandTotal);
            customersTableStatement.setString(2, customerName);
            customersTableStatement.executeUpdate();
            billsTableStatement.executeUpdate();
            billStmt.executeBatch();
            updateStmt.executeBatch();
            updateRawPriceStatement.executeBatch();
            insertStmt.executeBatch();
            con.commit();
            JOptionPane.showMessageDialog(PurchaseBill.this, "bill created successfully");
            return true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(MyClass.purchaseBill, "Error: " + ex.getMessage());
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
        pack();
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
            MyClass.purchaseBill = new PurchaseBill();
            MyClass.billingScreen.setVisible(true);
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

                        dispose();
                        try {
                            con.close();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(PurchaseBill.this, "Error: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                        MyClass.purchaseBill = new PurchaseBill();
                        MyClass.purchaseBill.init();
                        MyClass.purchaseBill.setVisible(true);
                    }

                } else {
                    JOptionPane.showMessageDialog(MyClass.purchaseBill, "select a customer");
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
            double rawCost = 0;
            //ccase of unintended calls of the model
            TableColumnModel columnModel = billTable.getColumnModel();

            if (col == quantityIndex) {
                try {
                    quantity = getDoubleValue(model.getValueAt(row, quantityIndex));
                    if (quantity == 0) model.setValueAt("", row, quantityIndex);

                } catch (NumberFormatException exception) {
                    model.setValueAt("", row, quantityIndex);
                }
                updateTotal(row);

            }
            if (col == rawCostIndex) {
                try {
                    rawCost = getDoubleValue(model.getValueAt(row, rawCostIndex));

                    if (rawCost == 0) model.setValueAt("", row, rawCostIndex);
                } catch (NumberFormatException ex) {
                    model.setValueAt("", row, rawCostIndex);
                }
                if (!getStringValue(model.getValueAt(row, designIDIndex)).isEmpty() && designIDexists(getStringValue(model.getValueAt(row, designIDIndex)))) {
                    JOptionPane.showMessageDialog(MyClass.purchaseBill, "raw getBuyPrice will be update in inventory, re enter design id to avoid this");
                }
                setDefaultSellPrice(row);
                updateTotal(row);
            }
            if (col == designIDIndex) {

                fetchAndAppend(model.getValueAt(row, designIDIndex) == null ? "noidblank" : model.getValueAt(row, designIDIndex).toString(), row);
                billTable.repaint();
            }
            if (col == itemNameIndex) {
                if (designIDexists(getStringValue(model.getValueAt(row, designIDIndex))))
                    JOptionPane.showMessageDialog(MyClass.purchaseBill, "आइटम का नाम इन्वन्टोरी मे बदल जाएगा, कृपया नाम न बदलने की स्थिति मे दुबारा से designid डालें ");
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
            }
        });


        UtilityMethods.generateAndAddNames(customerComboBox);
        UtilityMethods.generateAndAddDates(dateComboBox, false);

        setBillid(fetchBillId());
        billidLabel.setText("bill id:" + getBillid());


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
        }

        return true;
    }

    private String getStringValue(Object valueAt) {
        if (valueAt != null) return valueAt.toString();
        return "";
    }

    private void fetchAndAppend(String designID, int row) {
        String query = "select * from " + INVENTORY_TABLE + " where " + INVENTORY_DESIGN_ID + "=?";
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
        double rawCost = getDoubleValue(model.getValueAt(row, rawCostIndex));
        double total = quantity * rawCost;

        model.setValueAt(total == 0 ? "" : total, row, totalIndex);
        model.fireTableRowsUpdated(row, row);
        billTable.repaint();
    }

    private void setDefaultSellPrice(int row) {
        Object currentSellPrice = model.getValueAt(row, sellPriceIndex);


        double purchasePrice = getDoubleValue(model.getValueAt(row, rawCostIndex));
        if (purchasePrice > 0) {
            model.setValueAt(Math.round(purchasePrice * 1.15), row, sellPriceIndex);
        }
    }

    private int getIntegerValue(Object value) {
        return (value != null && !value.toString().isEmpty()) ? Integer.parseInt(value.toString()) : 0;
    }

    private double getDoubleValue(Object value) {
        return (value != null && !value.toString().isEmpty()) ? Double.parseDouble(value.toString()) : 0.0;
    }


}
