package loginsignup.login.loggedin.billing.newBill;

import loginsignup.login.loggedin.billing.viewbills.ViewCustomerBill;
import mainpack.MyClass;
import org.jdesktop.swingx.prompt.PromptSupport;
import utils.CONSTANTS;
import utils.DBStructure;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;

import static mainpack.MyClass.*;

public class NewBill extends JFrame {
    public static final int SNO_INDEX = 0;
    public static final int ORDER_SLIP_QUANTITY_INDEX = 1;
    public static final int ITEM_NAME_INDEX = 2;
    public static final int QUANTITY_INDEX = 3;
    public static final int DESIGN_ID_INDEX = 4;
    public static final int LABOUR_INDEX = 5;             // "L"
    public static final int RAW_INDEX = 6;
    public static final int DULL_CHILLAI_INDEX = 7;       // "dc"
    public static final int MEENA_INDEX = 8;              // "M/CM"
    public static final int RHODIUM_INDEX = 9;            // "Rh"
    public static final int NAG_SETTING_INDEX = 10;       // "Nag"
    public static final int OTHER_BASE_INDEX = 11;        // "Other"
    public static final int OTHER_DETAILS_INDEX = 12;
    public static final int PLUSGOLD = 13; // "+G"
    public static final int GOLD_WEIGHT_INDEX = 14;       // "Gold(ing g)"
    public static final int GOLD_COST_INDEX = 15;
    public static final int TOTAL_COST_INDEX = 16;        // "total"
    private static int modular = 1;
    int itemID;
    private LocalDateTime dateTime;
    private double goldrate;
    private String customerName;

    public JButton getSubmitButton() {
        return submitButton;
    }

    public void setItemID(int itemID) {
        this.itemID = itemID;
    }

    public void createBillToOrderSlipAssosciation(int sNo, int itemID) {
        snoToItemIdMap.put(sNo, itemID);
    }

    public boolean updateThroughSlip = false;
    private JComboBox<String> customerComboBox;
    private JPanel panel;
    private int sno = 0;
    private JLabel idLabel;
    private int curBillID;
    private JTable billTable;
    private JButton backButton;
    private JComboBox<String> slipTypeComboBox;
    private JButton submitButton;
    private HashMap<Integer, Integer> snoToItemIdMap;
    private JButton resetButton;
    private JButton undoButton;
    private JTextField slipNumberField;
    private JTextField goldRateTextField;
    private JScrollPane scrollPane;
    private JComboBox<String> dateComboBox;
    private JLabel grandTotalLabel;
    private JButton resetButton1;
    private Connection transacTemp;
    public boolean notThroughOrderSlip = true;

    public JTable getBillTable() {
        return billTable;
    }

    public NewBill() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    private void linkBillToTransactions() {
        int billID = getCurBillID();
        String customerName = getCustomerName();

        String query = "UPDATE transactions SET billid = ? WHERE customer_name = ? AND billid IS NULL";

        try (Connection con = MyClass.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {

            pstmt.setInt(1, billID);
            pstmt.setString(2, customerName);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            Thread.dumpStack();
            JOptionPane.showMessageDialog(null, "Failed to update transactions!", "Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException();
        }


    }

    private String getStringValue(DefaultTableModel model, int row, int columnIndex, String defaultValue) {
        Object value = model.getValueAt(row, columnIndex);
        return (value != null && !value.toString().trim().isEmpty()) ? value.toString() : defaultValue;
    }

    private boolean insertData() {

        DefaultTableModel model = (DefaultTableModel) billTable.getModel();
        int rowCount = model.getRowCount();

        // Database connection setup
        int billID = -1;
        Connection conn = getTransacTemp();
        PreparedStatement billDetailsStatement = null;
        PreparedStatement inventoryStatement = null;
        PreparedStatement customerTableStatement = null;

        String customerName = customerComboBox.getSelectedItem() == null ? "" : customerComboBox.getSelectedItem().toString();

        if (customerComboBox.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(newBill, "select customer please");
            return false;
        }
        String updateCustomerTableQuery = "update customers set balance=balance+? where customer_name=?";
        String updateBillsTableQuery = "update bills set date=?,customer_name=? where billid=?";
        try {
            PreparedStatement billsTableStatement = conn.prepareStatement(updateBillsTableQuery);

            billsTableStatement.setTimestamp(1, Timestamp.valueOf(getDateTime()));
            billsTableStatement.setString(2, getCustomerName());
            billsTableStatement.setInt(3, getCurBillID());
            billsTableStatement.executeUpdate();
            billsTableStatement.close();
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(newBill, "error");
            Thread.dumpStack();
            throw new RuntimeException();
        }

        try {
//            conn = DriverManager.getConnection(url, user, password);

            updateBillsTableQuery = "INSERT INTO billdetails (BillID, SNo, ItemName, DesignID, OrderType, RawCost, LabourCost, DullChillaiCost, " + "MeenaColorMeenaCost, RhodiumCost, NagSettingCost, OtherBaseCosts, TotalBaseCosting, GoldRate, " + "GoldPlatingWeight, TotalGoldCost, TotalFinalCost, OrderSlipNumber,OtherBaseCostNotes,quantity) " + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?)";
            billDetailsStatement = conn.prepareStatement(updateBillsTableQuery);
            String inventoryQuery = "update inventory set TotalQuantity=TotalQuantity-? where DesignID=?";
            inventoryStatement = conn.prepareStatement(inventoryQuery);
            double grandTotal = 0;
            for (int i = 0; i < rowCount - 1; i++) {
                int serialNo = Integer.parseInt(getStringValue(model, i, SNO_INDEX, "0"));
                String itemName = getStringValue(model, i, ITEM_NAME_INDEX, "Unknown");

                if (itemName.isEmpty()) {
                    JOptionPane.showMessageDialog(newBill, "please do not leave item name empty");
                    throw new RuntimeException();
                }


                String designID = getStringValue(model, i, DESIGN_ID_INDEX, "NoID");
                String orderType = slipTypeComboBox.getSelectedItem() == null ? "" : slipTypeComboBox.getSelectedItem().toString();


                if (snoToItemIdMap.containsKey(serialNo)) {
                    int itemId = snoToItemIdMap.get(serialNo);

                    // Fetch OrderType from orderslip table using itemId
                    String query = "SELECT slip_type FROM order_slips WHERE item_id = ?";
                    PreparedStatement pstmt = conn.prepareStatement(query);
                    pstmt.setInt(1, itemId);
                    ResultSet rs = pstmt.executeQuery();

                    orderType = slipTypeComboBox.getSelectedItem().toString();
                    if (rs.next()) {
                        orderType = rs.getString("slip_type"); // Override with DB value
                    }
                    rs.close();
                    pstmt.close();
                }
                int quantity = Integer.parseInt(getStringValue(model, i, QUANTITY_INDEX, "0"));
                if (quantity == 0) {
                    JOptionPane.showMessageDialog(newBill, "please do not leave quantity field empty");
                    return false;
                }
                double raw = Double.parseDouble(getStringValue(model, i, RAW_INDEX, "0"));
                double labour = Double.parseDouble(getStringValue(model, i, LABOUR_INDEX, "0"));
                double dullChillai = Double.parseDouble(getStringValue(model, i, DULL_CHILLAI_INDEX, "0"));
                double mcm = Double.parseDouble(getStringValue(model, i, MEENA_INDEX, "0"));
                double rh = Double.parseDouble(getStringValue(model, i, RHODIUM_INDEX, "0"));
                double nag = Double.parseDouble(getStringValue(model, i, NAG_SETTING_INDEX, "0"));
                double other = Double.parseDouble(getStringValue(model, i, OTHER_BASE_INDEX, "0"));
                double totalBaseCost = Double.parseDouble(getStringValue(model, i, PLUSGOLD, "0"));
                double goldRate = newBill.goldrate;
                double goldPlatingWeight = Double.parseDouble(getStringValue(model, i, GOLD_WEIGHT_INDEX, "0"));
                double totalGoldCost = Double.parseDouble(getStringValue(model, i, GOLD_COST_INDEX, "0"));
                double totalFinalCost = Double.parseDouble(getStringValue(model, i, TOTAL_COST_INDEX, "0"));
                String otherDetails = getStringValue(model, i, OTHER_DETAILS_INDEX, "");
                int orderSlipNumber = snoToItemIdMap.getOrDefault(serialNo, 0);
                if ((model.getValueAt(i, ORDER_SLIP_QUANTITY_INDEX) == null || model.getValueAt(i, ORDER_SLIP_QUANTITY_INDEX).toString().trim().isEmpty()) && !designID.contentEquals("NoID")) {
                    inventoryStatement.setInt(1, quantity);
                    inventoryStatement.setString(2, designID);
                    inventoryStatement.addBatch();
                }
                billDetailsStatement.setInt(1, getCurBillID()); // Replace with actual BillID
                billDetailsStatement.setInt(2, serialNo);
                billDetailsStatement.setString(3, itemName);
                billDetailsStatement.setString(4, designID);
                billDetailsStatement.setString(5, orderType);
                billDetailsStatement.setDouble(6, raw);
                billDetailsStatement.setDouble(7, labour);
                billDetailsStatement.setDouble(8, dullChillai);
                billDetailsStatement.setDouble(9, mcm);
                billDetailsStatement.setDouble(10, rh);
                billDetailsStatement.setDouble(11, nag);
                billDetailsStatement.setDouble(12, other);
                billDetailsStatement.setDouble(13, totalBaseCost);
                billDetailsStatement.setDouble(14, goldRate);
                billDetailsStatement.setDouble(15, goldPlatingWeight);
                billDetailsStatement.setDouble(16, totalGoldCost);
                billDetailsStatement.setDouble(17, totalFinalCost);
                billDetailsStatement.setLong(18, orderSlipNumber);
                billDetailsStatement.setString(19, otherDetails);
                billDetailsStatement.setInt(20, quantity);
                billDetailsStatement.addBatch();
                grandTotal = grandTotal + totalFinalCost;

            }


            billDetailsStatement.executeBatch();
            inventoryStatement.executeBatch();
            customerTableStatement = conn.prepareStatement(updateCustomerTableQuery);

            customerTableStatement.setDouble(1, grandTotal);
            customerTableStatement.setString(2, customerName);
            customerTableStatement.executeUpdate();
            System.out.println(billID + " is the billid");
            conn.commit(); // Commit transaction

            //            JOptionPane.showMessageDialog(null, "Bill details saved successfully! bill id is " + getCurBillID());
            billTable.repaint();
            return true;
        } catch (Exception ex) {
            try {
                if (conn != null) {
                    conn.rollback(); // Rollback on error
                }

            } catch (SQLException rollbackEx) {
                Thread.dumpStack();
                throw new RuntimeException();
            }
            Thread.dumpStack();
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error saving bill details!", "Error", JOptionPane.ERROR_MESSAGE);
            return false;

        } finally {
            try {

                if (billDetailsStatement != null) billDetailsStatement.close();
                if (inventoryStatement != null) inventoryStatement.close();
                if (customerTableStatement != null) customerTableStatement.close();

            } catch (SQLException closeEx) {
                Thread.dumpStack();
            }
        }
    }

    private void setCurBillID(int billID) {
        curBillID = billID;
    }

    public int getCurBillID() {
        return curBillID;
    }

    private void setCurrentDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return;
        }
        dateTime = localDateTime;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    private void reCalculateValuesAndAppend() {
        List<Integer> mathColumns = Arrays.asList(LABOUR_INDEX, RAW_INDEX, DULL_CHILLAI_INDEX, MEENA_INDEX, RHODIUM_INDEX, NAG_SETTING_INDEX, OTHER_BASE_INDEX, PLUSGOLD, GOLD_WEIGHT_INDEX, GOLD_COST_INDEX, TOTAL_COST_INDEX);
        double plusG = 0;
//        TableModelListener[] listeners = removeModelListener(tableModel);
        int i = 0;
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            for (int col : mathColumns) {

                Object value = tableModel.getValueAt(row, col);
                double numericValue = 0.0;

                if (value != null && !value.toString().trim().isEmpty()) {
                    try {
                        numericValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        Thread.dumpStack();
                    }

                }

                if (i < 7) {
                    plusG += numericValue;
                }
                double goldrate = this.goldrate;
                double goldcost;
                if (i == 8) {
                    goldcost = goldrate * numericValue;
                    tableModel.setValueAt(goldcost == 0 ? "" : UtilityMethods.round(goldcost, 2), row, GOLD_COST_INDEX);

                    tableModel.setValueAt((goldcost + plusG) == 0 ? "" : UtilityMethods.round(goldcost + plusG, 2), row, TOTAL_COST_INDEX);
                }

                i++;

            }
            int quantity = tableModel.getValueAt(row, QUANTITY_INDEX) != null && !tableModel.getValueAt(row, QUANTITY_INDEX).toString().isEmpty() ? Integer.parseInt((tableModel.getValueAt(row, QUANTITY_INDEX).toString())) : 0;
            plusG = plusG * quantity;
            tableModel.setValueAt(plusG == 0 ? "" : UtilityMethods.round(plusG, 2), row, PLUSGOLD);

            plusG = 0;
            i = 0;
        }

        for (int row = 0; row < tableModel.getRowCount() - 1; row++)
            checkAndRemoveRow(row, tableModel, SNO_INDEX, false);
        billTable.repaint();
        scrollPane.repaint();
//        addModelListeners(listeners, tableModel);

    }

    private void setGoldRate(double goldrate) {
        this.goldrate = goldrate;
    }

    private double getDoubleValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {

            return 0.0; // Return 0.0 if parsing fails
        }
    }

    public Connection getTransacTemp() {
        return transacTemp;
    }

    DefaultTableModel tableModel;
    Vector<String> billDetails = new Vector<>();

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("CREATE SELL BILL");
        setContentPane(panel);

        pack();
        ((AbstractDocument) goldRateTextField.getDocument()).setDocumentFilter(UtilityMethods.getDocFilter());

        goldRateTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Not needed for plain text fields.
            }

            private void textChanged() {
                double goldrate = 0;
                try {
                    goldrate = goldRateTextField.getText().isEmpty() ? 0 : Double.parseDouble(goldRateTextField.getText());
                } catch (NumberFormatException e) {
                    Thread.dumpStack();
                    throw new RuntimeException();
                }
                setGoldRate(goldrate);
                reCalculateValuesAndAppend();
            }
        });

        snoToItemIdMap = new HashMap<>();
        backButton.addActionListener(e -> {
            setVisible(false);
            billingScreen.setVisible(true);
            dispose();
            if (searchResultWindow != null) searchResultWindow.dispose();
            try {
                getTransacTemp().close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }


        });
        NewBill temp = this;
        slipNumberField.addActionListener(e -> {
            String query;
            if (customerComboBox.isEnabled()) {
                query = "SELECT slip_id FROM order_slips WHERE slip_id=" + slipNumberField.getText();
            } else
                query = "SELECT slip_id FROM order_slips WHERE slip_id=" + slipNumberField.getText() + " AND customer_name='" + getCustomerName() + "';";

            try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {


                try (ResultSet rs = stmt.executeQuery(query);) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(newBill, "orderslip not found");
                        slipNumberField.setText("");
                        return;
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(newBill, "sql exception occured");
                Thread.dumpStack();
                throw new RuntimeException();

            }

            if (searchResultWindow.isVisible()) {
                searchResultWindow.dispose();
            }
            searchResultWindow = new SearchResultWindow(Integer.parseInt(slipNumberField.getText()));
            searchResultWindow.setVisible(true);
            submitButton.setEnabled(false);
            backButton.setEnabled(false);
            UtilityMethods.splitFrame(temp, searchResultWindow, UtilityMethods.HORIZONTAL_SPLIT);
        });
        submitButton.addActionListener(e -> {
            if (insertData()) {
                linkBillToTransactions();
                printBill(getCurBillID());
                dispose();
                newBill = new NewBill();
                newBill.init();
                newBill.setVisible(true);
            }
        });
        dateComboBox.addActionListener(e -> setCurrentDate(UtilityMethods.parseDate(dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString())));
        resetButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();

                newBill = new NewBill();
                newBill.init();
                newBill.setVisible(true);
            }
        });
        if (!customerComboBox.isEnabled()) customerComboBox.setEnabled(true);
        billTable.getInputMap(JTable.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteRow");
        billTable.getActionMap().put("deleteRow", new AbstractAction() {
            @Override

            public void actionPerformed(ActionEvent e) {
                Action oldAction = billTable.getActionMap().get("deleteRow");
                billTable.getActionMap().remove("deleteRow");
                int selectedRow = billTable.getSelectedRow(); // Get selected row
                TableModelListener[] listeners = UtilityMethods.removeModelListener((tableModel));

                if (selectedRow != -1) {
                    // Ensure a row is selected
                    if (billTable.getRowCount() != 1)
                        checkAndRemoveRow(selectedRow, tableModel, SNO_INDEX, true);// Remove the row
                }

                UtilityMethods.addModelListeners(listeners, tableModel);

                billTable.getActionMap().put("deleteRow", oldAction);
                int selectedrow = billTable.getSelectedRow();
                tableModel.fireTableDataChanged();
                billTable.setRowSelectionInterval(selectedrow, selectedrow);
            }

        });

        try {
            if (transacTemp != null && !transacTemp.isClosed()) transacTemp.close();
            transacTemp = createConnection();
            transacTemp.setAutoCommit(false);
        } catch (SQLException e) {
            Thread.dumpStack();
            throw new RuntimeException();
        }
        Connection conn = getTransacTemp();

        String sql1 = "INSERT INTO bills () VALUES ()";
        try (PreparedStatement pstmt = conn.prepareStatement(sql1, Statement.RETURN_GENERATED_KEYS)) {
            conn.setAutoCommit(false); // Start transaction
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) setCurBillID((rs.getInt(1)));
            idLabel.setText("bill id: " + getCurBillID());

        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(newBill, "error");
            exception.printStackTrace();
            throw new RuntimeException();
        }

        PromptSupport.setPrompt("Slip Number", slipNumberField);
        PromptSupport.setForeground(Color.GRAY, slipNumberField);
        PromptSupport.setPrompt("gold rate", goldRateTextField);
        PromptSupport.setForeground(Color.GRAY, goldRateTextField);
        listOfNonEditableCells = new Vector<>();
        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1);
        billDetails = new Vector<>();
        billDetails.add("SNo");
        billDetails.add("OrderSlip/quantity");
        billDetails.add("ItemName");

        billDetails.add("Quantity");

        billDetails.add("DesignID");

        billDetails.add("L");

        billDetails.add("Raw");

        billDetails.add("dc");

        billDetails.add("M/CM");

        billDetails.add("Rh");

        billDetails.add("Nag");

        billDetails.add("Other");
        billDetails.add("OtherDetails");
        billDetails.add("+G");
        billDetails.add("Gold(ing g)");
        billDetails.add("gold cost");
        billDetails.add("total");

        tableModel = new DefaultTableModel(billDetails, 1) {
            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == SNO_INDEX) return false;
                if (column == ORDER_SLIP_QUANTITY_INDEX) return false;
                if (column == TOTAL_COST_INDEX) return false;
                if (column == PLUSGOLD) return false;
                if (column == GOLD_COST_INDEX) return false;
                String value = tableModel.getValueAt(row, ORDER_SLIP_QUANTITY_INDEX) == null ? "" : tableModel.getValueAt(row, ORDER_SLIP_QUANTITY_INDEX).toString();
                return (column != QUANTITY_INDEX && column != DESIGN_ID_INDEX) || value.isEmpty();
            }

        };

        billTable.setModel(tableModel);
        while (!today.isBefore(oneYearAgo)) {
            today = today.minusDays(1);
        }
        tableModel.addTableModelListener(e -> {
            // Get the row index where the data is stored

            int row = e.getFirstRow();
            int col = e.getColumn();
            if (row == -1 || col == -1) {
                return;
            }


            int snoValue = (tableModel.getValueAt(row, SNO_INDEX) != null && !tableModel.getValueAt(row, SNO_INDEX).toString().isEmpty()) ? Integer.parseInt(tableModel.getValueAt(row, SNO_INDEX).toString().contentEquals("") ? "0" : tableModel.getValueAt(row, SNO_INDEX).toString()) : -1;
            TableModelListener[] listeners = UtilityMethods.removeModelListener(tableModel);
            if (customerComboBox.getSelectedIndex() == 0) {
                JOptionPane.showMessageDialog(NewBill.this, "please select customer");
                tableModel.setValueAt("", row, col);
                UtilityMethods.addModelListeners(listeners, tableModel);
                return;
            }
            if (customerComboBox.isEnabled()) {
                customerComboBox.setEnabled(false);
            }
            if (snoValue == -1) {
                sno++;

                tableModel.setValueAt(sno, row, SNO_INDEX);
                snoValue = sno;

//                    tableModel.getValueAt(row, SNO_INDEX) == null ? -1 : Integer.parseInt(tableModel.getValueAt(row, SNO_INDEX).toString().contentEquals("") ? "0" : tableModel.getValueAt(row, SNO_INDEX).toString());
            }


            if (row == tableModel.getRowCount() - 1) {
                // Check if any column in this row has data
                boolean rowHasData = false;
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    if (tableModel.getValueAt(row, i) != null && !tableModel.getValueAt(row, i).toString().trim().isEmpty()) {
                        rowHasData = true;
                        break;
                    }
                }


                // If the last row has data, add a new empty row
                if (rowHasData) {
                    tableModel.addRow(new Vector<>(billDetails.size()));
                }

            }
            int designIdIndex = DESIGN_ID_INDEX;
            int quantityIndex = QUANTITY_INDEX;

            int quantity = 0;
            try {
                quantity = (tableModel.getValueAt(row, quantityIndex) == null || tableModel.getValueAt(row, quantityIndex).toString().trim().isEmpty()) ? 0 : Integer.parseInt(tableModel.getValueAt(row, quantityIndex).toString().trim());
            } catch (NumberFormatException ex) {
                tableModel.setValueAt("", row, col);

            }

            if (col == designIdIndex && !searchResultWindow.isSearchFlag()) {
                String designID = tableModel.getValueAt(row, designIdIndex) == null ? "" : tableModel.getValueAt(row, designIdIndex).toString().trim();

                if (!designID.isEmpty()) {
                    String query = "SELECT itemname,price FROM inventory WHERE DesignID = ?";

                    try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query)) {

                        stmt.setString(1, designID);
                        ResultSet rs = stmt.executeQuery();

                        if (rs.next()) {

                            listOfNonEditableCells.add(new Integer[]{row, ITEM_NAME_INDEX});
                            String itemName = rs.getString("itemname");
                            String price = rs.getString("price");
                            tableModel.setValueAt(itemName, row, ITEM_NAME_INDEX);
                            tableModel.setValueAt(price, row, RAW_INDEX);
                        } else {
                            listOfNonEditableCells.removeIf(cell -> cell[0] == row && cell[1] == ITEM_NAME_INDEX);

                            tableModel.setValueAt("", row, designIdIndex);
                            tableModel.setValueAt("", row, ITEM_NAME_INDEX);
                            tableModel.setValueAt("", row, RAW_INDEX);
                        }

                        DefaultTableModel newModel = redoModel();

                        billTable.setModel(newModel);
                        tableModel = newModel;
                    } catch (SQLException ex) {
//                        Thread.dumpStack();
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(null, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                } else {
                    // If designID field is empty, clear ItemName as well
                    tableModel.setValueAt("", row, designIdIndex);
                    tableModel.setValueAt("", row, ITEM_NAME_INDEX);
                    tableModel.setValueAt("", row, RAW_INDEX);
                    listOfNonEditableCells.removeIf(cell -> cell[0] == row && cell[1] == ITEM_NAME_INDEX);

                }
            }

            if ((col == quantityIndex)) {

                String designID = UtilityMethods.parseString(tableModel.getValueAt(row, designIdIndex));
                if (notThroughOrderSlip && designID != null && !designID.toString().contentEquals("")) {
                    String query = "select "+DBStructure.INVENTORY_TOTAL_QUANTITY+" from "+DBStructure.INVENTORY_TABLE+" where "+DBStructure.INVENTORY_DESIGN_ID+"=?;";
                    try (Connection con = MyClass.createConnection(); PreparedStatement preparedStatement = con.prepareStatement(query)) {
                        preparedStatement.setString(1,designID);
                        try (ResultSet resultSet = preparedStatement.executeQuery(query);) {
                            if (resultSet.next()) if (resultSet.getInt("totalquantity") < quantity)
                                JOptionPane.showMessageDialog(newBill, "warning stock not remaining");
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }


                }
                if (snoToItemIdMap.containsKey(snoValue)) {
                    try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
                        String query = "select * from "+DBStructure.ORDER_SLIPS_TABLE+" where "+DBStructure.ORDER_SLIPS_ITEM_ID+"=" + snoToItemIdMap.get(snoValue);
                        int item_id = snoToItemIdMap.get(snoValue);
                        int netQuantity = 0;
                        for (int key : snoToItemIdMap.keySet()) {
                            if (snoToItemIdMap.get(key) == item_id) {
                                netQuantity = netQuantity + ((tableModel.getValueAt(key - 1, quantityIndex) != null && !tableModel.getValueAt(key - 1, quantityIndex).toString().isEmpty()) ? Integer.parseInt(tableModel.getValueAt(key - 1, quantityIndex).toString()) : 0);
                            }
                            try (ResultSet rs = stmt.executeQuery(query)) {
                                if (rs.next()) {
                                    int quantityTemp = rs.getInt("quantity") - rs.getInt("billed_quantity");
                                    if (quantityTemp < netQuantity) {
                                        JOptionPane.showMessageDialog(newBill, "invalid quanity has been entered for an item connected to a order slip");
                                        tableModel.setValueAt("", row, quantityIndex);
                                    }
                                } else {
                                    JOptionPane.showMessageDialog(newBill, "error has occured");
                                    System.exit(-1);
                                    throw new RuntimeException();
                                }
                            }
                        }

                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }

            double labour = getDoubleValue(tableModel.getValueAt(row, LABOUR_INDEX));
            tableModel.setValueAt(labour == 0 ? "" : labour, row, LABOUR_INDEX);
            double raw = getDoubleValue(tableModel.getValueAt(row, RAW_INDEX));
            tableModel.setValueAt(raw == 0 ? "" : raw, row, RAW_INDEX);
            double dullChillai = getDoubleValue(tableModel.getValueAt(row, DULL_CHILLAI_INDEX));
            tableModel.setValueAt(dullChillai == 0 ? "" : dullChillai, row, DULL_CHILLAI_INDEX);
            double mCm = getDoubleValue(tableModel.getValueAt(row, MEENA_INDEX));
            tableModel.setValueAt(mCm == 0 ? "" : mCm, row, MEENA_INDEX);
            double rh = getDoubleValue(tableModel.getValueAt(row, RHODIUM_INDEX));
            tableModel.setValueAt(rh == 0 ? "" : rh, row, RHODIUM_INDEX);
            double nag = getDoubleValue(tableModel.getValueAt(row, NAG_SETTING_INDEX));
            tableModel.setValueAt(nag == 0 ? "" : nag, row, NAG_SETTING_INDEX);
            double other = getDoubleValue(tableModel.getValueAt(row, OTHER_BASE_INDEX));
            tableModel.setValueAt(other == 0 ? "" : other, row, OTHER_BASE_INDEX);
            double plusG;
            double goldIngG = getDoubleValue(tableModel.getValueAt(row, GOLD_WEIGHT_INDEX));
            tableModel.setValueAt(goldIngG == 0 ? "" : goldIngG, row, GOLD_WEIGHT_INDEX);
            double goldCost;
            double total;

            plusG = labour + raw + dullChillai + mCm + rh + nag + other;
            goldCost = goldIngG * goldrate;
            plusG *= quantity;
            total = plusG + goldCost;
            tableModel.setValueAt(total == 0 ? "" : UtilityMethods.round(total, 2), row, TOTAL_COST_INDEX);
            tableModel.setValueAt(plusG == 0 ? "" : UtilityMethods.round(plusG, 2), row, PLUSGOLD);
            tableModel.setValueAt(goldCost == 0 ? "" : UtilityMethods.round(goldCost, 2), row, GOLD_COST_INDEX);


            if (updateThroughSlip) {
                createBillToOrderSlipAssosciation(sno, itemID);
                updateThroughSlip = false;
            }
            checkAndRemoveRow(row, tableModel, SNO_INDEX, false);
            double grandTotal = 0;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                double value = tableModel.getValueAt(i, TOTAL_COST_INDEX) == null ? 0 : tableModel.getValueAt(i, TOTAL_COST_INDEX).toString().isEmpty() ? 0 : Double.parseDouble(tableModel.getValueAt(i, TOTAL_COST_INDEX).toString());
                grandTotal += value;
            }
            grandTotalLabel.setText("Grand total:" + UtilityMethods.round(grandTotal, 2));
            if (col == ITEM_NAME_INDEX) {

                String value = codeToItemName.get(getStringValue(tableModel, row, ITEM_NAME_INDEX, "").trim().toUpperCase());
                if (value != null) tableModel.setValueAt(value, row, ITEM_NAME_INDEX);
            }
            billTable.repaint();
            UtilityMethods.addModelListeners(listeners, tableModel);
        });


        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("select type_name from ordertype;")) {
                while (rs.next()) {
                    slipTypeComboBox.addItem(rs.getString(1));
                }
            }
            pack();
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        UtilityMethods.generateAndAddNames(customerComboBox);
        UtilityMethods.generateAndAddDates(dateComboBox, false);
        customerComboBox.addActionListener(e -> {
            setCustomerName(customerComboBox.getSelectedItem() == null ? "" : customerComboBox.getSelectedItem().toString());
            String query = "SELECT MIN(" + DBStructure.ORDER_SLIPS_MAIN_SLIP_ID + ") FROM " + DBStructure.ORDER_SLIPS_MAIN_TABLE + " WHERE " + DBStructure.ORDER_SLIPS_MAIN_CUSTOMER_NAME + " = ?";
            int slipID = -1;
            try (Connection con = MyClass.createConnection(); PreparedStatement preparedStatement = con.prepareStatement(query)) {

                if (customerComboBox.getSelectedIndex() != 0) {
                    preparedStatement.setString(1, getCustomerName());
                    System.out.println("debug mode customer name is " + getCustomerName());

                } else {
                    preparedStatement.setString(1, UtilityMethods.parseString(customerComboBox.getItemAt(1)));
                    System.out.println("debug mode customer name is " + UtilityMethods.parseString(customerComboBox.getItemAt(1)));

                }
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next() && !rs.wasNull()) {
                        rs.getInt(1);
                        if (!rs.wasNull()) {
                            slipID = rs.getInt(1);
                        }
                        System.out.println("yes " + slipID);
                    }
                }
                if (slipID == -1) {
                    System.out.println("slip not found");
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                    if (searchResultWindow != null && !searchResultWindow.isVisible())
                        searchResultWindow.dispose();
                    return;
                }

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(newBill, ex.getMessage());
                throw new RuntimeException(ex);
            }
            System.out.println("slip id in debug mode is " + slipID);
            if (searchResultWindow.isVisible()) searchResultWindow.dispose();
            searchResultWindow = new SearchResultWindow(slipID);
            searchResultWindow.setTableModel(searchResultWindow.generateSlipListModel(getCustomerName()));
            searchResultWindow.setFocusableWindowState(false);
            searchResultWindow.setVisible(true);
            searchResultWindow.setFocusableWindowState(true);
            UtilityMethods.splitFrame(temp, searchResultWindow, UtilityMethods.HORIZONTAL_SPLIT);
        });

    }

    private void printBill(int curBillID) {
        ViewCustomerBill viewCustomerBill = new ViewCustomerBill();
        viewCustomerBill.init();
        viewCustomerBill.setVisible(false);
        viewCustomerBill.loadBillData(viewCustomerBill.getBillTable(),curBillID);
        DefaultTableModel model = (DefaultTableModel) viewCustomerBill.getBillTable().getModel();
        try {
            String fileName="billsOut/bill_"+viewCustomerBill.getCustomerName()+"_"+viewCustomerBill.getBillID()+".pdf";
            UtilityMethods.saveBillAsPdf( model ,viewCustomerBill.getBillID(),viewCustomerBill.getDate(),viewCustomerBill.getCustomerName(), CONSTANTS.CUSTOMER_BILL,fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(NewBill.this, "Could not print bill "+e.getMessage(),"error in printing", JOptionPane.ERROR_MESSAGE);

        }
    }
//    private void insertRandomValues(int rows) {
//        Random rand = new Random();
//
//        for (int r = 0; r < rows; r++) {
//            // Create a new row with empty values
//            Vector<Object> rowData = new Vector<>(Collections.nCopies(tableModel.getColumnCount(), ""));
//
//            tableModel.addRow(rowData); // add empty row first
//
//            for (int c = 0; c < tableModel.getColumnCount(); c++) {
//                if (!tableModel.isCellEditable(r, c)) continue; // skip non-editable cells
//                if (c == DESIGN_ID_INDEX) continue; // skip DesignID as requested
//
//                // Insert a dummy/random value
//                if (c == ITEM_NAME_INDEX || c == OTHER_DETAILS_INDEX) {
//                    tableModel.setValueAt("RandomText" + rand.nextInt(100), r, c);
//                } else if (c == RAW_INDEX || c == LABOUR_INDEX || c == DULL_CHILLAI_INDEX || c == MEENA_INDEX ||
//                        c == RHODIUM_INDEX || c == NAG_SETTING_INDEX || c == OTHER_BASE_INDEX) {
//                    tableModel.setValueAt(rand.nextInt(100), r, c); // Random number 0-99
//                } else {
//                    tableModel.setValueAt(rand.nextDouble() * 10, r, c); // For gold weight etc.
//                }
//            }
//        }
//    }


    //    private void generateBillID() {
//        try {
//            Statement stmt1;
//            stmt1 = C.createStatement();
//            ResultSet rs1;
//            String query = "SELECT MAX(BillID) FROM bills;";
//            rs1 = stmt1.executeQuery(query);
//            int newBillID; // Default BillID if no bills exist
//            if (rs1.next()) {
//                int lastBillID = rs1.getInt(1);
//                if (rs1.wasNull()) {
//                    newBillID = 1; // If no bills, set BillID to 1
//                } else {
//                    newBillID = lastBillID + 1; // Increment the last BillID
//                }
//                setCurBillID(newBillID);
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
    public void insertRandomValues(int rows, int date, String customer_name) {
        Random random = new Random();

        if (customer_name == null)
            customerComboBox.setSelectedIndex(1 + random.nextInt(customerComboBox.getItemCount() - 1));
        else
            customerComboBox.setSelectedItem(customer_name);

        for (int i = 0; i < rows; i++) {
            String[] JEWELRY_ITEMS = {
                    "Solitaire Ring", "Pearl Stud Earrings", "Diamond Pendant", "Gold Bangle", "Kundan Necklace",
                    "Ruby Cocktail Ring", "Meenakari Jhumka", "Emerald Choker", "Platinum Chain", "CZ Toe Ring",
                    "Antique Kada", "Temple Design Pendant", "Rose Gold Bracelet", "Dual-tone Hoops", "Navratna Necklace",
                    "Sapphire Studs", "Victorian Brooch", "Adjustable Finger Ring", "Oxidized Anklet", "Layered Mangalsutra",
                    "Traditional Nose Pin", "Butterfly Charm Pendant", "Bridal Matha Patti", "Tanzanite Drop Earrings", "Floral Armlet (Bajuband)"
            };


            int row = tableModel.getRowCount() - 1; // newly created row

            // ITEM_NAME_INDEX
            tableModel.setValueAt(JEWELRY_ITEMS[random.nextInt(JEWELRY_ITEMS.length)], row, ITEM_NAME_INDEX);
            int goldRate = 9500 + random.nextInt(500);
            goldRateTextField.setText(goldRate + "");

            dateComboBox.setSelectedIndex(date);
            //            customerComboBox.setSelectedIndex(1);
            // QUANTITY_INDEX
            int quantity = 1 + random.nextInt(30);  // random int from 1 to 30
            tableModel.setValueAt(quantity, row, QUANTITY_INDEX);

            // LABOUR_INDEX ("L")
            double labour = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(labour, row, LABOUR_INDEX);

            // RAW_INDEX
            double raw = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(raw, row, RAW_INDEX);

            // DULL_CHILLAI_INDEX ("dc")
            double dc = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(dc, row, DULL_CHILLAI_INDEX);

            // MEENA_INDEX ("M/CM")
            double meena = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(meena, row, MEENA_INDEX);

            // RHODIUM_INDEX ("Rh")
            double rhodium = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(rhodium, row, RHODIUM_INDEX);

            // NAG_SETTING_INDEX ("Nag")
            double nag = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(nag, row, NAG_SETTING_INDEX);

            // OTHER_BASE_INDEX ("Other")
            double other = Math.round((1 + random.nextDouble() * 499) * 100.0) / 100.0;
            tableModel.setValueAt(other, row, OTHER_BASE_INDEX);

            // OTHER_DETAILS_INDEX
            tableModel.setValueAt("Note " + (random.nextInt(10) + 1), row, OTHER_DETAILS_INDEX);

            // GOLD_WEIGHT_INDEX ("Gold(ing g)") - 1 to 5
            double goldWeight = Math.round((1 + random.nextDouble() * 4) * 100.0) / 100.0;
            tableModel.setValueAt(goldWeight, row, GOLD_WEIGHT_INDEX);
        }
    }

    private void checkAndRemoveRow(int row, DefaultTableModel tableModel, int snoIndex, boolean forceRemove) {
        // Ensure the row index is valid
//        Thread.dumpStack();

        if (row < 0 || row >= tableModel.getRowCount() - 1) {
            return; // Invalid row index, exit method last row can not be deleted
        }
        boolean isEmpty = true;

        // Check if all columns in the given row are empty
        if (!forceRemove) for (int col = 0; col < tableModel.getColumnCount(); col++) {
            if (col == snoIndex) continue;
            Object value = tableModel.getValueAt(row, col);
            if (value != null && !value.toString().trim().isEmpty()) {
                isEmpty = false;
                break;
            }
        }

        if (isEmpty) {
//            try {

//            }
//            catch ()
            relinkAssociations(row + 1);
            tableModel.removeRow(row);
            reCheckEnableDisableInItemNameColumn();

            int sno = 0;
            for (int i = 0; i < tableModel.getRowCount() - 1; i++) {
                sno++; // Increment SNo
                tableModel.setValueAt(sno, i, snoIndex); // Set new value in SNo column
            }
            tableModel.setValueAt("", billTable.getRowCount() - 1, SNO_INDEX);
            this.sno = sno;
        }
    }

    private void reCheckEnableDisableInItemNameColumn() {
        listOfNonEditableCells.removeIf(cell -> cell[1] == ITEM_NAME_INDEX);
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String value = tableModel.getValueAt(i, DESIGN_ID_INDEX) == null ? "" : tableModel.getValueAt(i, DESIGN_ID_INDEX).toString();
            if (!value.isEmpty()) listOfNonEditableCells.add(new Integer[]{i, ITEM_NAME_INDEX});
        }
    }

    private void relinkAssociations(int removeSNO) //THIS FUNCTION IS ONLY CALLED WHEN DELETING A ITEM ROW FROM BILL TABLE WHICH
//            CORRUSPONDS TO A ORDER SLIP
    {
        if (snoToItemIdMap.get(removeSNO) == null) return;
        HashMap<Integer, Integer> tempMap = new HashMap<>();
        int itemid = snoToItemIdMap.get(removeSNO);
        int quantity = Integer.parseInt(tableModel.getValueAt(removeSNO - 1, QUANTITY_INDEX).toString());
        Connection temp = getTransacTemp();

        try {
            String query = "update order_slips set billed_quantity =billed_quantity-? where item_id=?";

            PreparedStatement stmt = temp.prepareStatement(query);
            stmt.setInt(1, quantity);
            stmt.setInt(2, itemid);


            stmt.executeUpdate();
            if (searchResultWindow.isVisible()) searchResultWindow.fetchData(searchResultWindow.getTableModel());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        Iterator<Integer> i = snoToItemIdMap.keySet().iterator();
        while (i.hasNext()) {
            int key = i.next();
            if (key < removeSNO) tempMap.put(key, snoToItemIdMap.get(key));
            if (key > removeSNO) {
                tempMap.put(key - 1, snoToItemIdMap.get(key));
            }
            if (key == removeSNO) i.remove();
        }
        snoToItemIdMap.clear();
        snoToItemIdMap.putAll(tempMap);

    }

    Vector<Integer[]> listOfNonEditableCells;

    DefaultTableModel redoModel() {
        Vector<Vector<String>> v = new Vector<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Vector<String> data = new Vector<>();

            for (int j = 0; j < tableModel.getColumnCount(); j++) {
                Object cellValue = tableModel.getValueAt(i, j);
                data.add(cellValue != null ? cellValue.toString() : ""); // Avoid NullPointerException
            }
            v.add(data);
        }


        DefaultTableModel m = new DefaultTableModel(v, billDetails) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Avoid IndexOutOfBoundsException

                // Loop through all non-editable cells
                for (Integer[] cell : listOfNonEditableCells) {
                    if (cell[0] == row && cell[1] == column) {
                        return false;
                    }
                }

                if (column == SNO_INDEX) return false;
                if (column == ORDER_SLIP_QUANTITY_INDEX) return false;
                if (column == TOTAL_COST_INDEX) return false;
                if (column == PLUSGOLD) return false;
                if (column == GOLD_COST_INDEX) return false;
                String value = tableModel.getValueAt(row, ORDER_SLIP_QUANTITY_INDEX) == null ? "" : tableModel.getValueAt(row, ORDER_SLIP_QUANTITY_INDEX).toString();
                return (column != QUANTITY_INDEX && column != DESIGN_ID_INDEX) || value.isEmpty();
            }
        };

        for (TableModelListener tlm : ((DefaultTableModel) billTable.getModel()).getTableModelListeners()) {
            m.addTableModelListener(tlm);
        }
        return m;
    }

    public JButton getBackButton() {
        return backButton;
    }
}

