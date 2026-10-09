package loginsignup.login.loggedin.accountingandledger.ledgerwindows;

import com.mysql.cj.callback.MysqlCallback;
import jdk.jshell.execution.Util;
import mainpack.MyClass;
import utils.DBStructure;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

import static utils.DBStructure.*;

public class LedgerWindow extends JFrame {

    public static final int CUSTOMER_MODE = 65 * 1;
    public static final int ITEM_MODE = 65 * 2;
    private static final int PURCHASE_MODE = 65 * 3;
    private JButton backButton;
    private JTable ledgerTable;
    private JPanel panel;
    private JLabel nameLabel;

    public LedgerWindow() {

    }

    public void init(String customerName, int mode) {
        setContentPane(panel);
        setTitle("Ledger " + customerName);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.aalScreen.setVisible(true);
                dispose();

            }
        });
        String[] columnNames = null;
        if (mode == ITEM_MODE)
            columnNames = new String[]{"id", "date", "debit", "credit", "balance"};
        else if (mode == CUSTOMER_MODE) {
            columnNames = new String[]{"id", "date", "debit", "credit", "balance"};

        } else if (mode == PURCHASE_MODE) {
            columnNames = new String[]{"id", "date", "Party Name", "debit", "credit"};
        }
        DefaultTableModel defaultTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ledgerTable.setModel(defaultTableModel);
        if (mode == CUSTOMER_MODE) {
            DefaultTableModel model = GetData.fetchCustomerLedger(customerName);

            if (model != null) {
                ledgerTable.setModel(model);
            }
        } else if (mode == ITEM_MODE) {
            DefaultTableModel model = GetData.fetchItemLedger(customerName, this);

            if (model != null) {
                ledgerTable.setModel(model);
            }
        }

//        fetchLedgerToModel(ledgerTable);


    }


    //    private void fetchCustomerLedger(String customerName) {
//        nameLabel.setText(customerName);
//        System.out.println("starting to fetch customer ledger");
}

class GetData {

    static final String ID_COLUMN = "id";
    static final int ID_COLUMN_INDEX = 0;
    static final String DATE_COLUMN = "date";
    static final int DATE_COLUMN_INDEX = 1;
    static final String DEBIT_COLUMN = "debit";
    static final int DEBIT_COLUMN_INDEX = 2;
    static final String CREDIT_COLUMN = "credit";
    static final int CREDIT_COLUMN_INDEX = 3;
    static final String BALANCE_COLUMN = "balance";
    static final int BALANCE_COLUMN_INDEX = 4;

    static DefaultTableModel fetchCustomerLedger(String customerName) {

        String[] columnNames = new String[]{ID_COLUMN, DATE_COLUMN, DEBIT_COLUMN, CREDIT_COLUMN, BALANCE_COLUMN};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        String query =
                "SELECT * FROM (" + "select 'customersTable' as type, 'opening balance' as id , '' as date, " + CUSTOMERS_OPENING_ACCOUNT + " as amount from " + CUSTOMERS_TABLE + " " +
                        "where " + CUSTOMERS_CUSTOMER_NAME + " = ? union " +
                        "select 'bill' as type, b." + BILLS_BILL_ID + " as id, b." + BILLS_DATE + " as date, sum(COALESCE(bd." + BILLDETAILS_TOTAL_FINAL_COST + ", 0)) as amount " +
                        "from " + BILLS_TABLE + " b " +
                        "join " + BILLDETAILS_TABLE + " bd on b." + BILLS_BILL_ID + " = bd." + BILLDETAILS_BILL_ID + " " +
                        "where b." + BILLS_CUSTOMER_NAME + " IS NOT NULL and b." + BILLS_CUSTOMER_NAME + " = ? and b." + BILLS_DRAFT + " = ? " +
                        "group by b." + BILLS_BILL_ID + ", b." + BILLS_DATE + " " +
                        "union " +
                        "select 'transaction' as type, " + TRANSACTIONS_TRANSACTION_ID + " as id, " +
                        TRANSACTIONS_DATE + " as date, COALESCE(" + TRANSACTIONS_AMOUNT + ", 0) as amount " +
                        "from " + TRANSACTIONS_TABLE + " " +
                        "where " + TRANSACTIONS_CUSTOMER_NAME + " IS NOT NULL and " + TRANSACTIONS_CUSTOMER_NAME + " = ? " +
                        ") as combined " +
                        "order by coalesce (date,'')";
        try (Connection con = UtilityMethods.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, customerName);
            pstmt.setString(2, customerName);
            pstmt.setBoolean(3, false);
            pstmt.setString(4, customerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                BigDecimal balance = BigDecimal.ZERO;
                while (rs.next()) {
                    String[] values = new String[columnNames.length];
                    String dateValue = rs.getString("date");
                    if (!dateValue.isEmpty()) {
                        try {
                            values[DATE_COLUMN_INDEX] = LocalDate.parse(dateValue, DateTimeFormatter.ofPattern("yyyy-MM-dd")).format(DateTimeFormatter.ofPattern("dd-MM-yy"));
                        } catch (java.time.format.DateTimeParseException e) {
                            values[DATE_COLUMN_INDEX] = LocalDate.parse(dateValue, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).format(DateTimeFormatter.ofPattern("dd-MM-yy"));
                        }
                    } else values[DATE_COLUMN_INDEX] = "";
                    BigDecimal amount = rs.getBigDecimal("amount");
                    System.out.println(amount);
                    boolean debitCredit;//false->debit,true ->credit
                    String type = rs.getString("type");
                    String prefix = "";
                    if (type.contentEquals("bill")) {
                        prefix = "bill#";
                        if (amount.doubleValue() < 0) {
                            debitCredit = true;
                            amount = amount.multiply(new BigDecimal(-1));

                        } else debitCredit = false;

                    } else if (type.contentEquals("transaction")) {
                        prefix = "TRAN#";
                        if (amount.doubleValue() < 0) {
                            debitCredit = false;
                            amount = amount.multiply(new BigDecimal(-1));
                        } else {
                            debitCredit = true;
                        }
                    } else if (type.contentEquals("customersTable")) {
                        prefix = "";

                        if (amount.doubleValue() < 0) {
                            debitCredit = true;
                            amount = amount.multiply(new BigDecimal(-1));
                        } else {
                            debitCredit = false;
                        }
                    } else {
                        return null;
                    }
                    values[ID_COLUMN_INDEX] = prefix.concat(rs.getString("id"));

                    if (debitCredit) {
                        balance = balance.subtract(amount);
                        values[CREDIT_COLUMN_INDEX] = amount.toPlainString();
                        values[DEBIT_COLUMN_INDEX] = "";
                    } else {
                        balance = balance.add(amount);
                        values[DEBIT_COLUMN_INDEX] = amount.toPlainString();
                        values[CREDIT_COLUMN_INDEX] = "";
                    }
                    values[BALANCE_COLUMN_INDEX] = balance.toPlainString();
                    tableModel.addRow(values);
                }
                String[] values = new String[columnNames.length];
                values[ID_COLUMN_INDEX] = "Closing balance";
                values[DATE_COLUMN_INDEX] = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"));
                values[DEBIT_COLUMN_INDEX] = "";
                values[CREDIT_COLUMN_INDEX] = "";
                values[BALANCE_COLUMN_INDEX] = balance.toPlainString();

                tableModel.addRow(values);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(MyClass.ledgerWindow, "error: " + e.getMessage());
            return null;
        }
        return tableModel;
    }

    static DefaultTableModel fetchItemLedger(String designID, LedgerWindow ledgerWindow) {
        String[] columnNames = {
                ID_COLUMN,
                DATE_COLUMN,
                DEBIT_COLUMN,
                CREDIT_COLUMN,
                BALANCE_COLUMN
        };

        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        String openingStockQuery =
                "SELECT " + INVENTORY_OPENING_STOCK +
                        " FROM " + INVENTORY_TABLE +
                        " WHERE " + INVENTORY_DESIGN_ID + " = ?";

        String movementQuery =
                "SELECT bd." + BILLDETAILS_BILL_ID + " AS billId, " +
                        "b." + BILLS_DATE + " AS billDate, " +
                        "bd." + BILLDETAILS_QUANTITY + " AS quantity " +
                        "FROM " + BILLDETAILS_TABLE + " bd " +
                        "JOIN " + BILLS_TABLE + " b ON b." + BILLS_BILL_ID +
                        " = bd." + BILLDETAILS_BILL_ID + " " +
                        "WHERE bd." + BILLDETAILS_DESIGN_ID + " = ? " +
                        "AND b." + BILLS_DRAFT + " = ? " +
                        "ORDER BY b." + BILLS_DATE + ", bd." + BILLDETAILS_BILL_ID;

        try (Connection con = MyClass.createConnection()) {

            int balance = 0;

            // Opening stock row
            try (PreparedStatement openingStatement = con.prepareStatement(openingStockQuery)) {
                openingStatement.setString(1, designID);

                try (ResultSet rs = openingStatement.executeQuery()) {
                    if (rs.next()) {
                        balance = rs.getInt(INVENTORY_OPENING_STOCK);
                    }
                }
            }

            String[] openingRow = new String[columnNames.length];
            openingRow[ID_COLUMN_INDEX] = "Opening balance";
            openingRow[DATE_COLUMN_INDEX] = "";
            openingRow[DEBIT_COLUMN_INDEX] = balance >= 0 ? String.valueOf(balance) : "";
            openingRow[CREDIT_COLUMN_INDEX] = balance < 0 ? String.valueOf(Math.abs(balance)) : "";
            openingRow[BALANCE_COLUMN_INDEX] = String.valueOf(balance);
            tableModel.addRow(openingRow);

            // Bill and purchase movements
            try (PreparedStatement movementStatement = con.prepareStatement(movementQuery)) {
                movementStatement.setString(1, designID);
                movementStatement.setBoolean(2, false);

                try (ResultSet rs = movementStatement.executeQuery()) {
                    while (rs.next()) {
                        int quantity = rs.getInt("quantity");
                        java.sql.Date billDate = rs.getDate("billDate");

                        String[] row = new String[columnNames.length];
                        row[ID_COLUMN_INDEX] = "Bill#" + rs.getInt("billId");
                        row[DATE_COLUMN_INDEX] = billDate == null
                                ? ""
                                : UtilityMethods.parseDateString(billDate);

                        if (quantity < 0) {
                            // Purchase: negative stored quantity means stock increases.
                            row[DEBIT_COLUMN_INDEX] = String.valueOf(Math.abs(quantity));
                            row[CREDIT_COLUMN_INDEX] = "";
                        } else {
                            // Sale: positive stored quantity means stock decreases.
                            row[DEBIT_COLUMN_INDEX] = "";
                            row[CREDIT_COLUMN_INDEX] = String.valueOf(quantity);
                        }

                        balance = balance - quantity;
                        row[BALANCE_COLUMN_INDEX] = String.valueOf(balance);

                        tableModel.addRow(row);
                    }
                }
            }

            return tableModel;

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    ledgerWindow,
                    "Could not fetch item ledger: " + e.getMessage()
            );
            return null;
        }
    }
    private static String fetchDate(LedgerWindow ledgerWindow, int billId) {
        String dateQuery = "SELECT " + BILLS_DATE +
                " FROM " + BILLS_TABLE +
                " WHERE " + BILLS_BILL_ID + " = ?";

        try (Connection con = MyClass.createConnection();
             PreparedStatement dateStatement = con.prepareStatement(dateQuery)) {

            dateStatement.setInt(1, billId);

            try (ResultSet rs = dateStatement.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date billDate = rs.getDate(BILLS_DATE);

                    if (billDate != null) {
                        return UtilityMethods.parseDateString(billDate);
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    ledgerWindow,
                    "Could not fetch bill date: " + e.getMessage()
            );
        }

        return "";
    }
}
