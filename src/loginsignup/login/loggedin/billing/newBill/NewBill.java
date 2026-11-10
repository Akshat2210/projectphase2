package loginsignup.login.loggedin.billing.newBill;

import loginsignup.login.loggedin.billing.viewbills.ViewCustomerBill;
import loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public static final int INVENTORY_MODE = -35;
    int itemID;
    private LocalDateTime dateTime;
    private BigDecimal goldrate = BigDecimal.ZERO.setScale(2);
    private String customerName;
    private  SearchResultWindow searchResultWindow;
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

    private String getStringValue(DefaultTableModel model, int row, int columnIndex, String defaultValue) {
        Object value = model.getValueAt(row, columnIndex);
        return (value != null && !value.toString().trim().isEmpty()) ? value.toString() : defaultValue;
    }

    private BigDecimal getBigDecimalValue(DefaultTableModel model, int row, int columnIndex) {
        return new BigDecimal(getStringValue(model, row, columnIndex, "0").trim());
    }

    private boolean insertData() {
        Savepoint savepoint = null;

        DefaultTableModel model = (DefaultTableModel) billTable.getModel();
        int rowCount = model.getRowCount();

        // Database connection setup
        int billID = -1;
        Connection conn = getTransacTemp();
        PreparedStatement billDetailsStatement = null;
        PreparedStatement inventoryStatement = null;
        PreparedStatement customerTableStatement = null;
        PreparedStatement linkTransactionsStatement = null;
        PreparedStatement journalEntryStatement = null;
        PreparedStatement linkDirectJournalEntriesStatement = null;
        String customerName = customerComboBox.getSelectedItem() == null ? "" : customerComboBox.getSelectedItem().toString();

        if (customerComboBox.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(NewBill.this, "select customer please");
            return false;
        }
        String updateCustomerTableQuery = "update " + DBStructure.CUSTOMERS_TABLE + " set " + DBStructure.CUSTOMERS_BALANCE + "=" + DBStructure.CUSTOMERS_BALANCE + "+? where " + DBStructure.CUSTOMERS_CUSTOMER_NAME + "=?";
        String updateBillsTableQuery = "update " + DBStructure.BILLS_TABLE + " set " + DBStructure.BILLS_DATE + "=?," + DBStructure.BILLS_CUSTOMER_NAME + "=?, " + DBStructure.BILLS_BILL_NUMBER + "=? where " + DBStructure.BILLS_BILL_ID + "=?";
        String insertJournalEntryQuery = "INSERT INTO " + DBStructure.JOURNAL_ENTRY_TABLE + " (" + DBStructure.JOURNAL_ENTRY_TRANSACTION_TYPE + ", " + DBStructure.JOURNAL_ENTRY_DATE + ", " + DBStructure.JOURNAL_ENTRY_ACCOUNT_NAME + ", " + DBStructure.JOURNAL_ENTRY_BILL_ID + ", " + DBStructure.JOURNAL_ENTRY_TRANSACTION_ID + ", " + DBStructure.JOURNAL_ENTRY_CREDIT + ", " + DBStructure.JOURNAL_ENTRY_DEBIT + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        int billNumber = UtilityMethods.getBillNumber(getTransacTemp(), CONSTANTS.SALE_ENUM);
        try {
            try {
                savepoint = conn.setSavepoint();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(NewBill.this, e.getMessage());
                e.printStackTrace();
                return false;
            }
            PreparedStatement billsTableStatement = conn.prepareStatement(updateBillsTableQuery);

            billsTableStatement.setTimestamp(1, Timestamp.valueOf(getDateTime()));
            billsTableStatement.setString(2, getCustomerName());
            billsTableStatement.setInt(3, billNumber);
            System.out.println(billNumber + " is billnumber");
            billsTableStatement.setInt(4, getCurBillID());
            billsTableStatement.executeUpdate();
            billsTableStatement.close();
        } catch (Exception exception) {
            try {
                conn.rollback(savepoint);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(NewBill.this, e.getMessage());
                return false;
            }
            JOptionPane.showMessageDialog(NewBill.this, exception.getMessage());
            return false;
        }

        try {
//            conn = DriverManager.getConnection(url, user, password);
            String insertBillDetailsQuery = "INSERT INTO billdetails (BillID, SNo, ItemName, DesignID, OrderType, RawCost, LabourCost, DullChillaiCost, " + "MeenaColorMeenaCost, RhodiumCost, NagSettingCost, OtherBaseCosts, TotalBaseCosting, GoldRate, " + "GoldPlatingWeight, TotalGoldCost, TotalFinalCost, OrderSlipNumber,OtherBaseCostNotes,quantity) " + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?)";
            billDetailsStatement = conn.prepareStatement(insertBillDetailsQuery);
            String inventoryQuery = "update " + DBStructure.INVENTORY_TABLE + " set " + DBStructure.INVENTORY_TOTAL_QUANTITY + "=" + DBStructure.INVENTORY_TOTAL_QUANTITY + "-? where TRIM(" + DBStructure.INVENTORY_DESIGN_ID + ")=?";
            String linkDirectJournalEntriesQuery = "UPDATE " + DBStructure.JOURNAL_ENTRY_TABLE + " SET " + DBStructure.JOURNAL_ENTRY_BILL_ID + " = ? WHERE " + DBStructure.JOURNAL_ENTRY_ACCOUNT_NAME + " = ? AND " + DBStructure.JOURNAL_ENTRY_TRANSACTION_TYPE + " = ? AND " + DBStructure.JOURNAL_ENTRY_BILL_ID + " IS NULL AND " + DBStructure.JOURNAL_ENTRY_TRANSACTION_ID + " IS NULL AND " + DBStructure.JOURNAL_ENTRY_DATE + " <= ?";
            inventoryStatement = conn.prepareStatement(inventoryQuery);
            journalEntryStatement = conn.prepareStatement(insertJournalEntryQuery);
            linkDirectJournalEntriesStatement = conn.prepareStatement(linkDirectJournalEntriesQuery);
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (int i = 0; i < rowCount - 1; i++) {
                int serialNo = Integer.parseInt(getStringValue(model, i, SNO_INDEX, "0"));
                String itemName = getStringValue(model, i, ITEM_NAME_INDEX, "");
                if (itemName.isEmpty()) {
                    JOptionPane.showMessageDialog(NewBill.this, "please do not leave item name empty");
                    throw new RuntimeException();
                }


                String designID = getStringValue(model, i, DESIGN_ID_INDEX, "NoID").trim();
                String orderType = slipTypeComboBox.getSelectedItem() == null ? "" : slipTypeComboBox.getSelectedItem().toString();


                if (snoToItemIdMap.containsKey(serialNo)) {
                    int itemId = snoToItemIdMap.get(serialNo);

                    // Fetch OrderType from orderslip table using itemId
                    String query = "SELECT " + DBStructure.ORDER_SLIPS_SLIP_TYPE + " FROM " + DBStructure.ORDER_SLIPS_TABLE + " WHERE " + DBStructure.ORDER_SLIPS_ITEM_ID + " = ?";
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
                BigDecimal raw = getBigDecimalValue(model, i, RAW_INDEX);
                BigDecimal labour = getBigDecimalValue(model, i, LABOUR_INDEX);
                BigDecimal dullChillai = getBigDecimalValue(model, i, DULL_CHILLAI_INDEX);
                BigDecimal mcm = getBigDecimalValue(model, i, MEENA_INDEX);
                BigDecimal rh = getBigDecimalValue(model, i, RHODIUM_INDEX);
                BigDecimal nag = getBigDecimalValue(model, i, NAG_SETTING_INDEX);
                BigDecimal other = getBigDecimalValue(model, i, OTHER_BASE_INDEX);
                BigDecimal totalBaseCost = getBigDecimalValue(model, i, PLUSGOLD);
                BigDecimal goldRate = NewBill.this.goldrate;
                BigDecimal goldPlatingWeight = getBigDecimalValue(model, i, GOLD_WEIGHT_INDEX);
                BigDecimal totalGoldCost = getBigDecimalValue(model, i, GOLD_COST_INDEX);
                BigDecimal totalFinalCost = getBigDecimalValue(model, i, TOTAL_COST_INDEX);
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
                billDetailsStatement.setBigDecimal(6, raw);
                billDetailsStatement.setBigDecimal(7, labour);
                billDetailsStatement.setBigDecimal(8, dullChillai);
                billDetailsStatement.setBigDecimal(9, mcm);
                billDetailsStatement.setBigDecimal(10, rh);
                billDetailsStatement.setBigDecimal(11, nag);
                billDetailsStatement.setBigDecimal(12, other);
                billDetailsStatement.setBigDecimal(13, totalBaseCost);
                billDetailsStatement.setBigDecimal(14, goldRate);
                billDetailsStatement.setBigDecimal(15, goldPlatingWeight);
                billDetailsStatement.setBigDecimal(16, totalGoldCost);
                billDetailsStatement.setBigDecimal(17, totalFinalCost);
                billDetailsStatement.setLong(18, orderSlipNumber);
                billDetailsStatement.setString(19, otherDetails);
                billDetailsStatement.setInt(20, quantity);
                billDetailsStatement.addBatch();
                grandTotal = grandTotal.add(totalFinalCost);
            }

            // Debit the customer's receivable and credit sales for this bill.
            journalEntryStatement.setString(1, CONSTANTS.SALE_ENUM);
            journalEntryStatement.setDate(2, java.sql.Date.valueOf(getDateTime().toLocalDate()));
            journalEntryStatement.setString(3, customerName);
            journalEntryStatement.setInt(4, getCurBillID());
            journalEntryStatement.setNull(5, Types.INTEGER);
            journalEntryStatement.setBigDecimal(6, BigDecimal.ZERO);
            journalEntryStatement.setBigDecimal(7, grandTotal);
            journalEntryStatement.addBatch();

            journalEntryStatement.setString(1, CONSTANTS.SALE_ENUM);
            journalEntryStatement.setDate(2, java.sql.Date.valueOf(getDateTime().toLocalDate()));
            journalEntryStatement.setString(3, "Sales");
            journalEntryStatement.setInt(4, getCurBillID());
            journalEntryStatement.setNull(5, Types.INTEGER);
            journalEntryStatement.setBigDecimal(6, grandTotal);
            journalEntryStatement.setBigDecimal(7, BigDecimal.ZERO);
            journalEntryStatement.addBatch();
            billDetailsStatement.executeBatch();
            inventoryStatement.executeBatch();
            journalEntryStatement.executeBatch();

            // Attach only this customer's earlier, unlinked direct journal lines to this bill.
            linkDirectJournalEntriesStatement.setInt(1, getCurBillID());
            linkDirectJournalEntriesStatement.setString(2, customerName);
            linkDirectJournalEntriesStatement.setString(3, CONSTANTS.DIRECT_JOURNAL_ENUM);
            linkDirectJournalEntriesStatement.setDate(4, java.sql.Date.valueOf(getDateTime().toLocalDate()));
            linkDirectJournalEntriesStatement.executeUpdate();

            customerTableStatement = conn.prepareStatement(updateCustomerTableQuery);

            customerTableStatement.setBigDecimal(1, grandTotal);
            customerTableStatement.setString(2, customerName);
            customerTableStatement.executeUpdate();
            System.out.println(billID + " is the billid");

            String linkQuery = "UPDATE " + DBStructure.TRANSACTIONS_TABLE + " SET " + DBStructure.TRANSACTIONS_BILLID + " = ? WHERE " + DBStructure.TRANSACTIONS_CUSTOMER_NAME + " = ? AND " + DBStructure.TRANSACTIONS_BILLID + " IS NULL";
            linkTransactionsStatement = conn.prepareStatement(linkQuery);
            linkTransactionsStatement.setInt(1, getCurBillID());
            linkTransactionsStatement.setString(2, customerName);
            linkTransactionsStatement.executeUpdate();

            conn.commit(); // Commit transaction
            JOptionPane.showMessageDialog(NewBill.this, "✨ Bill No. " + billNumber + " has been saved successfully!\nYour bill is ready to print.");            //            JOptionPane.showMessageDialog(null, "Bill details saved successfully! bill id is " + getCurBillID());
            billTable.repaint();
            return true;
        } catch (Exception ex) {
            try {
                conn.rollback(savepoint);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(NewBill.this, "rollBackFailed\n" + e.getMessage());
                return false;
            }
            JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return false;
        } finally {
            try {
                if (billDetailsStatement != null) billDetailsStatement.close();
                if (inventoryStatement != null) inventoryStatement.close();
                if (customerTableStatement != null) customerTableStatement.close();
                if (linkTransactionsStatement != null) linkTransactionsStatement.close();
                if (journalEntryStatement != null) journalEntryStatement.close();
                if (linkDirectJournalEntriesStatement != null) linkDirectJournalEntriesStatement.close();
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
        TableModelListener[] listeners = UtilityMethods.removeModelListener(tableModel);
        try {
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                int quantity = tableModel.getValueAt(row, QUANTITY_INDEX) != null
                        && !tableModel.getValueAt(row, QUANTITY_INDEX).toString().isEmpty()
                        ? Integer.parseInt(tableModel.getValueAt(row, QUANTITY_INDEX).toString()) : 0;
                calculateRowValues(row, quantity);
            }
            for (int row = 0; row < tableModel.getRowCount() - 1; row++)
                checkAndRemoveRow(row, tableModel, SNO_INDEX, false);
            updateGrandTotalLabel();
            billTable.repaint();
            scrollPane.repaint();
        } finally {
            UtilityMethods.addModelListeners(listeners, tableModel);
                tableModel.fireTableDataChanged();

        }
    }

    private void setGoldRate(BigDecimal goldrate) {
        this.goldrate = goldrate.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getDecimalValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO; // Preserve existing invalid-input handling for now.
        }
    }

    private void calculateRowValues(int row, int quantity) {
        BigDecimal perPiece = BigDecimal.ZERO;
        int[] chargeColumns = {LABOUR_INDEX, RAW_INDEX, DULL_CHILLAI_INDEX,
                MEENA_INDEX, RHODIUM_INDEX, NAG_SETTING_INDEX, OTHER_BASE_INDEX};
        for (int column : chargeColumns) {
            BigDecimal charge = getDecimalValue(tableModel.getValueAt(row, column))
                    .setScale(2, RoundingMode.HALF_UP);
            tableModel.setValueAt(charge.signum() == 0 ? "" : charge, row, column);
            perPiece = perPiece.add(charge);
        }
        BigDecimal weight = getDecimalValue(tableModel.getValueAt(row, GOLD_WEIGHT_INDEX))
                .setScale(3, RoundingMode.HALF_UP);
        tableModel.setValueAt(weight.signum() == 0 ? "" : weight, row, GOLD_WEIGHT_INDEX);
        BigDecimal plusG = perPiece.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        // Plating weight is the row's weight; only the base charges are multiplied by quantity.
        BigDecimal goldCost = weight.multiply(goldrate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = plusG.add(goldCost).setScale(2, RoundingMode.HALF_UP);
        tableModel.setValueAt(total.signum() == 0 ? "" : total, row, TOTAL_COST_INDEX);
        tableModel.setValueAt(plusG.signum() == 0 ? "" : plusG, row, PLUSGOLD);
        tableModel.setValueAt(goldCost.signum() == 0 ? "" : goldCost, row, GOLD_COST_INDEX);
    }

    private void updateGrandTotalLabel() {
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            grandTotal = grandTotal.add(getDecimalValue(tableModel.getValueAt(i, TOTAL_COST_INDEX)));
        }
        grandTotalLabel.setText("Grand total:" + grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
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
                BigDecimal goldrate = BigDecimal.ZERO;
                try {
                    goldrate = goldRateTextField.getText().trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(goldRateTextField.getText().trim());
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
                getTransacTemp().rollback();
                getTransacTemp().close();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }


        });
        NewBill temp = this;
        submitButton.addActionListener(e -> {
            if (billTable.isEditing()) {
                JOptionPane.showMessageDialog(NewBill.this, "please complete changes and stop editing before submiting", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!allQuantityValid()) {

                return;
            }

            if (askConfirmation() && insertData()) {
//                linkBillToTransactions();
                try {
                    printBill(getCurBillID());
                } finally {
                    try {
                        getTransacTemp().close();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                }
              if (searchResultWindow != null) searchResultWindow.dispose();  
                dispose();
                NewBill newBill = new NewBill();
                newBill.init();
                newBill.setVisible(true);
            }
        });
        dateComboBox.addActionListener(e -> setCurrentDate(UtilityMethods.parseDate(dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString())));
        resetButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    try {
                        getTransacTemp().rollback();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        getTransacTemp().close();
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                if(searchResultWindow!=null)searchResultWindow.dispose();
                dispose();
                NewBill newBill = new NewBill();
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
                try {
                    if (selectedRow != -1) {
                        // Ensure a row is selected
                        if (billTable.getRowCount() != 1)
                            checkAndRemoveRow(selectedRow, tableModel, SNO_INDEX, true);// Remove the row
                    }
                } finally {

                    UtilityMethods.addModelListeners(listeners, tableModel);
                    billTable.getActionMap().put("deleteRow", oldAction);
                }

                int selectedrow = billTable.getSelectedRow();
                tableModel.fireTableDataChanged();
                updateGrandTotalLabel();
                if (searchResultWindow != null && searchResultWindow.isVisible()) {
                    searchResultWindow.refreshResults();
                }
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

        String billsQuery = "INSERT INTO " + DBStructure.BILLS_TABLE + " (" + DBStructure.BILLS_BILL_TYPE + ") VALUES (?)";
        try (PreparedStatement billsStatement = conn.prepareStatement(billsQuery, Statement.RETURN_GENERATED_KEYS)) {
            conn.setAutoCommit(false); // Start transaction
            billsStatement.setString(1, CONSTANTS.SALE_ENUM);
            billsStatement.executeUpdate();
            ResultSet rs = billsStatement.getGeneratedKeys();
            if (rs.next()) setCurBillID((rs.getInt(1)));
            idLabel.setText("bill id: " + getCurBillID());

        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(NewBill.this, "error");
            exception.printStackTrace();
            throw new RuntimeException();
        }


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

            try {
                if (customerComboBox.getSelectedIndex() == 0) {
                    JOptionPane.showMessageDialog(NewBill.this, "please select customer");
                    tableModel.setValueAt("", row, col);
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
if (col == designIdIndex &&
    (searchResultWindow == null || !searchResultWindow.isSearchFlag())) {
                    String designID = tableModel.getValueAt(row, designIdIndex) == null ? "" : tableModel.getValueAt(row, designIdIndex).toString().trim();
                    tableModel.setValueAt(designID, row, designIdIndex);

                    if (!designID.isEmpty()) {
                        String query = "SELECT " + DBStructure.INVENTORY_ITEM_NAME + ", " + DBStructure.INVENTORY_SELL_PRICE + " FROM " + DBStructure.INVENTORY_TABLE + " WHERE TRIM(" + DBStructure.INVENTORY_DESIGN_ID + ") = ?";

                        try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query)) {

                            stmt.setString(1, designID);
                            ResultSet rs = stmt.executeQuery();

                            if (rs.next()) {

                                listOfNonEditableCells.add(new Integer[]{row, ITEM_NAME_INDEX});
                                String itemName = rs.getString("itemname");
                                String price = rs.getString(DBStructure.INVENTORY_SELL_PRICE);
                                tableModel.setValueAt(itemName, row, ITEM_NAME_INDEX);
                                tableModel.setValueAt(price, row, RAW_INDEX);
                            } else {
                                listOfNonEditableCells.removeIf(cell -> cell[0] == row && cell[1] == ITEM_NAME_INDEX);

                                tableModel.setValueAt("", row, designIdIndex);
                                tableModel.setValueAt("", row, ITEM_NAME_INDEX);
                                tableModel.setValueAt("", row, RAW_INDEX);
                            }

                            DefaultTableModel newModel = constructModel();

                            billTable.setModel(newModel);
                            tableModel = newModel;
                        } catch (SQLException ex) {
//                        Thread.dumpStack();
                            JOptionPane.showMessageDialog(NewBill.this, ex.getMessage());
                            ex.printStackTrace();

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

                    String designID = UtilityMethods.parseString(tableModel.getValueAt(row, designIdIndex)).trim();
                    if (notThroughOrderSlip && designID != null && !designID.toString().contentEquals("")) {
                        String query = "SELECT " + DBStructure.INVENTORY_TOTAL_QUANTITY + " FROM " + DBStructure.INVENTORY_TABLE + " WHERE TRIM(" + DBStructure.INVENTORY_DESIGN_ID + ") = ?";

                        try (Connection con = MyClass.createConnection(); PreparedStatement preparedStatement = con.prepareStatement(query)) {

                            preparedStatement.setString(1, designID);

                            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                                if (resultSet.next()) {
                                    int availableQuantity = resultSet.getInt(DBStructure.INVENTORY_TOTAL_QUANTITY);

                                    if (availableQuantity < quantity) {
                                        JOptionPane.showMessageDialog(NewBill.this, "Warning: Stock is not remaining.");
                                    }
                                }
                            }

                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(NewBill.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                        }


                    }
                    if (snoToItemIdMap.containsKey(snoValue)) {
                        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
                            String query = "select * from " + DBStructure.ORDER_SLIPS_TABLE + " where " + DBStructure.ORDER_SLIPS_ITEM_ID + "=" + snoToItemIdMap.get(snoValue);
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
                                            JOptionPane.showMessageDialog(NewBill.this, "invalid quanity has been entered for an item connected to a order slip");
                                            tableModel.setValueAt("", row, quantityIndex);
                                        }
                                    } else {
                                        JOptionPane.showMessageDialog(NewBill.this, "Stock Updation failed to display");


                                    }
                                }
                            }

                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                }

                calculateRowValues(row, quantity);

                if (updateThroughSlip) {
                    createBillToOrderSlipAssosciation(sno, itemID);
                    updateThroughSlip = false;
                }
                checkAndRemoveRow(row, tableModel, SNO_INDEX, false);
                updateGrandTotalLabel();

                if (col == ITEM_NAME_INDEX) {

                    String value = codeToItemName.get(getStringValue(tableModel, row, ITEM_NAME_INDEX, "").trim().toUpperCase());
                    if (value != null) tableModel.setValueAt(value, row, ITEM_NAME_INDEX);
                }
                billTable.repaint();
            } finally {
                UtilityMethods.addModelListeners(listeners, tableModel);
            }
        });


        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("select " + DBStructure.ORDERTYPE_TYPE_NAME + " from " + DBStructure.ORDERTYPE_TABLE)) {
                while (rs.next()) {
                    slipTypeComboBox.addItem(rs.getString(1));
                }
            }
            pack();
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return;
        }
        UtilityMethods.generateAndAddNames(customerComboBox);
        UtilityMethods.generateAndAddDates(dateComboBox, false);
        customerComboBox.addActionListener(e -> {
            if (customerComboBox.getSelectedIndex() == 0) {
                if (searchResultWindow != null) {
                    searchResultWindow.dispose();
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                    searchResultWindow = null;
                }
                return;
            }
            setCustomerName(customerComboBox.getSelectedItem() == null ? "" : customerComboBox.getSelectedItem().toString());
            if (searchResultWindow == null || !searchResultWindow.isVisible()) {
                searchResultWindow = new SearchResultWindow(INVENTORY_MODE,NewBill.this);
                searchResultWindow.setAutoRequestFocus(false);
                searchResultWindow.setVisible(true);
            }
            searchResultWindow.setComboBoxModel(searchResultWindow.generateSlipListModel(getCustomerName()));
            searchResultWindow.setInventorySelected();
            UtilityMethods.splitFrame(temp, searchResultWindow, UtilityMethods.HORIZONTAL_SPLIT);
        });

    }

    private boolean allQuantityValid() {
        DefaultTableModel tableModel = (DefaultTableModel) billTable.getModel();
        int dataRowCount = tableModel.getRowCount() - 1; // Last row is the blank entry row
        if (dataRowCount <= 0) {
            JOptionPane.showMessageDialog(NewBill.this, "Please add at least one item.");
            return false;
        }

        for (int row = 0; row < dataRowCount; row++) {
            Object quantityValue = tableModel.getValueAt(row, QUANTITY_INDEX);
            String quantityText = quantityValue == null ? "" : quantityValue.toString().trim();

            int quantity;
            try {
                quantity = Integer.parseInt(quantityText);
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(NewBill.this, "Please enter a whole quantity greater than zero in row " + (row + 1) + ".");
                return false;
            }

            if (quantity <= 0) {
                JOptionPane.showMessageDialog(NewBill.this, "Please enter a quantity greater than zero in row " + (row + 1) + ".");
                return false;
            }
        }

        return true;
    }

    private boolean askConfirmation() {
        final boolean[] confirmed = {false};

        JDialog previewDialog = new JDialog(this, "Preview Bill", true);
        previewDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        previewDialog.setLayout(new BorderLayout(10, 10));

        JPanel detailsPanel = new JPanel(new GridLayout(0, 2, 10, 6));
        detailsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        Object selectedCustomer = customerComboBox.getSelectedItem();
        Object selectedDate = dateComboBox.getSelectedItem();
        Object selectedSlipType = slipTypeComboBox.getSelectedItem();

        detailsPanel.add(new JLabel("Customer:"));
        detailsPanel.add(new JLabel(selectedCustomer == null ? "" : selectedCustomer.toString()));

        detailsPanel.add(new JLabel("Date:"));
        detailsPanel.add(new JLabel(selectedDate == null ? "" : selectedDate.toString()));

        detailsPanel.add(new JLabel("Slip Type:"));
        detailsPanel.add(new JLabel(selectedSlipType == null ? "" : selectedSlipType.toString()));

        detailsPanel.add(new JLabel("Gold Rate:"));
        detailsPanel.add(new JLabel(goldRateTextField.getText()));

        detailsPanel.add(new JLabel("Grand Total:"));
        detailsPanel.add(new JLabel(grandTotalLabel.getText()));

        detailsPanel.add(new JLabel("Bill Number:"));
        detailsPanel.add(new JLabel("Will be assigned after saving"));

        DefaultTableModel sourceModel = (DefaultTableModel) billTable.getModel();

        Vector<String> columnNames = new Vector<>();
        for (int column = 0; column < sourceModel.getColumnCount(); column++) {
            columnNames.add(sourceModel.getColumnName(column));
        }

        DefaultTableModel previewModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (int row = 0; row < sourceModel.getRowCount(); row++) {
            Object[] rowData = new Object[sourceModel.getColumnCount()];

            for (int column = 0; column < sourceModel.getColumnCount(); column++) {
                rowData[column] = sourceModel.getValueAt(row, column);
            }

            previewModel.addRow(rowData);
        }

        JTable previewTable = new JTable(previewModel);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane previewScrollPane = new JScrollPane(previewTable);
        previewScrollPane.setBorder(BorderFactory.createTitledBorder("Bill Items"));

        JButton editButton = new JButton("Back to Edit");
        JButton confirmButton = new JButton("Confirm & Save");

        editButton.addActionListener(e -> previewDialog.dispose());

        confirmButton.addActionListener(e -> {
            confirmed[0] = true;
            previewDialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        buttonPanel.add(editButton);
        buttonPanel.add(confirmButton);

        previewDialog.add(detailsPanel, BorderLayout.NORTH);
        previewDialog.add(previewScrollPane, BorderLayout.CENTER);
        previewDialog.add(buttonPanel, BorderLayout.SOUTH);

        previewDialog.setSize(1100, 650);
        previewDialog.setLocationRelativeTo(this);
        previewDialog.setVisible(true);

        return confirmed[0];
    }

    private void printBill(int curBillID) {
        ViewCustomerBill viewCustomerBill = new ViewCustomerBill();
        viewCustomerBill.init();
        viewCustomerBill.setVisible(false);
        viewCustomerBill.loadBillData(viewCustomerBill.getBillTable(), curBillID);
        DefaultTableModel model = (DefaultTableModel) viewCustomerBill.getBillTable().getModel();
        try {
            java.nio.file.Path savedPdf = SaleBillPdf.save(model, viewCustomerBill.getBillID(), viewCustomerBill.getDate(), viewCustomerBill.getCustomerName(), MyClass.login.getDatabase(), java.nio.file.Paths.get("data"));
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    BrowserPdfPrint.openPrintDialog(savedPdf);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception printError) {
                        Throwable cause = printError.getCause() == null ? printError : printError.getCause();
                        JOptionPane.showMessageDialog(NewBill.this, "The bill and PDF are saved.\n" + cause.getMessage() + "\nPDF: " + savedPdf, "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        } catch (Exception pdfError) {
            JOptionPane.showMessageDialog(NewBill.this, "Bill is saved, but its PDF could not be created.\n" + pdfError.getMessage(), "Bill saved - PDF not saved", JOptionPane.WARNING_MESSAGE);
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
        else customerComboBox.setSelectedItem(customer_name);

        for (int i = 0; i < rows; i++) {
            String[] JEWELRY_ITEMS = {"Solitaire Ring", "Pearl Stud Earrings", "Diamond Pendant", "Gold Bangle", "Kundan Necklace", "Ruby Cocktail Ring", "Meenakari Jhumka", "Emerald Choker", "Platinum Chain", "CZ Toe Ring", "Antique Kada", "Temple Design Pendant", "Rose Gold Bracelet", "Dual-tone Hoops", "Navratna Necklace", "Sapphire Studs", "Victorian Brooch", "Adjustable Finger Ring", "Oxidized Anklet", "Layered Mangalsutra", "Traditional Nose Pin", "Butterfly Charm Pendant", "Bridal Matha Patti", "Tanzanite Drop Earrings", "Floral Armlet (Bajuband)"};


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
            updateOrderSlipQuantity(row + 1);
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

    private void updateOrderSlipQuantity(int removeSNO) {

        Integer itemid = snoToItemIdMap.get(removeSNO);
        if (itemid == null) {
            return;
        }
        int quantity = Integer.parseInt(tableModel.getValueAt(removeSNO - 1, QUANTITY_INDEX).toString());
        Connection temp = getTransacTemp();

        try {
            String query = "update " + DBStructure.ORDER_SLIPS_TABLE + " set " + DBStructure.ORDER_SLIPS_BILLED_QUANTITY + " =" + DBStructure.ORDER_SLIPS_BILLED_QUANTITY + "-? where " + DBStructure.ORDER_SLIPS_ITEM_ID + "=?";
            PreparedStatement stmt = temp.prepareStatement(query);
            stmt.setInt(1, quantity);
            stmt.setInt(2, itemid);


            stmt.executeUpdate();
            if (searchResultWindow!=null && searchResultWindow.isVisible()) searchResultWindow.fetchOrderSlip(searchResultWindow.getTableModel());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    private void reCheckEnableDisableInItemNameColumn() {
        listOfNonEditableCells.removeIf(cell -> cell[1] == ITEM_NAME_INDEX);
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String value = tableModel.getValueAt(i, DESIGN_ID_INDEX) == null ? "" : tableModel.getValueAt(i, DESIGN_ID_INDEX).toString();
            if (!value.isEmpty()) listOfNonEditableCells.add(new Integer[]{i, ITEM_NAME_INDEX});
        }
    }

    private void relinkAssociations(int removeSNO) {
        HashMap<Integer, Integer> tempMap = new HashMap<>();
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

    DefaultTableModel constructModel() {
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

