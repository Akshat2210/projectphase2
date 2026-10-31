package loginsignup.login.loggedin.billing.viewbills;

import loginsignup.login.loggedin.billing.BillingScreen;
import mainpack.MyClass;
import utils.CONSTANTS;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import static mainpack.MyClass.billingScreen;
import static utils.UtilityMethods.*;

public class ViewBackendBill extends JFrame {
    private JButton backButton;
    private JTable billTable;
    private JComboBox<String> customerComboBox;
    private JComboBox<String> dateComboBox;
    private JButton prevButton;
    private JButton nextButton;
    private JPanel panel;
    private JTextField searchID;
    private JLabel customerNameLabel;
    private JLabel billIDLabel;
    private JLabel dateLabel;
    private final BillingScreen parentWindow;

    public ViewBackendBill(BillingScreen parentWindow) {


        this.parentWindow = parentWindow;
    }

    Vector<String> billDetails;

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        searchID.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    loadBillData(Integer.parseInt(searchID.getText()));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(ViewBackendBill.this, "error", "error", JOptionPane.ERROR_MESSAGE);
                }
                searchID.setText("");
            }
        });
        setContentPane(panel);
        generateAndAddDates(dateComboBox, true);
        generateAndAddNames(customerComboBox);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        System.out.println(0xb55);
        int m = 0b101001 + 0x545 + 0b10010111101001;
        billDetails = new Vector<>();
        billDetails.add("SNo");
        billDetails.add("OrderSlip");
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
        prevButton.addActionListener(e -> {
            String query1 = "SELECT MAX(BillID) FROM bills WHERE BillID < ?";

            if (customerComboBox.getSelectedIndex() != 0) {
                query1 += " AND customer_name = ?";
            }

            if (dateComboBox.getSelectedIndex() != 0) {
                query1 += " AND DATE(date) = ?";
            }

            try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query1)) {
                stmt.setInt(1, curBillID);
                int paramIndex = 2;
                if (customerComboBox.getSelectedIndex() != 0) {
                    stmt.setString(paramIndex++, customerComboBox.getSelectedItem().toString());
                }

                if (dateComboBox.getSelectedIndex() != 0) {
                    stmt.setString(paramIndex, dateComboBox.getSelectedItem().toString());
                }

                try (ResultSet rs = stmt.executeQuery();
                ) {
                    if (rs.next()) {
                        int prevID = rs.getInt(1);
                        if (!rs.wasNull()) {
                            loadBillData(prevID);
                            setCurBillID(prevID);
                        }
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        nextButton.addActionListener(e -> {
            String query = "SELECT MIN(BillID) FROM bills WHERE BillID > ?";

            if (customerComboBox.getSelectedIndex() != 0) {
                query += " AND customer_name = ?";
            }

            if (dateComboBox.getSelectedIndex() != 0) {
                query += " AND DATE(date) = ?";
            }

            try (Connection con= MyClass.createConnection(); PreparedStatement stmt=con.prepareStatement(query)){
                stmt.setInt(1, curBillID);
                int paramIndex = 2;
                if (customerComboBox.getSelectedIndex() != 0) {
                    stmt.setString(paramIndex++, customerComboBox.getSelectedItem().toString());
                }

                if (dateComboBox.getSelectedIndex() != 0) {
                    stmt.setString(paramIndex, dateComboBox.getSelectedItem().toString());
                }

                try(ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        int nextID = rs.getInt(1);
                        if (!rs.wasNull()) {
                            loadBillData(nextID);
                            setCurBillID(nextID);
                        }
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        DefaultTableModel tableModel = new DefaultTableModel(billDetails, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        billTable.setModel(tableModel);
        int value=loadBillData(minBillID());
        System.out.println(value);
        if(value!= CONSTANTS.SUCCESS_CODE) {
            dispose();

            parentWindow.setVisible(true);
        }

        backButton.addActionListener(e -> {
            setVisible(false);
            dispose();
            parentWindow.setVisible(true);
        });
    }

    public void setCurBillID(int curBillID) {
        this.curBillID = curBillID;
    }


    int curBillID;

    int loadBillData(int billid) {

        String query1 = "SELECT * FROM billdetails WHERE BillID = ?";
        String  query2 = "select customer_name,date from bills where billid =?";
        String customer_name = "";

        try (Connection con= MyClass.createConnection(); PreparedStatement stmt1=con.prepareStatement(query1); PreparedStatement stmt2= con.prepareStatement(query2)){
            stmt1.setInt(1, billid);
           try( ResultSet rs = stmt1.executeQuery();){
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(ViewBackendBill.this, "empty resultset returned");
                    Thread.dumpStack();
                    return CONSTANTS.FAIL_CODE;

                }
                DefaultTableModel model = (DefaultTableModel) billTable.getModel();
                model.setRowCount(0); // Clear existing data
                do {

                    Vector<String> row = new Vector<>();
                    row.add(rs.getString("SNo") == null ? "" : rs.getString("SNo")); // SNo
                    row.add(rs.getString("OrderSlipNumber") == null ? "" : rs.getString("OrderSlipNumber")); // OrderSlip/quantity
                    row.add(rs.getString("ItemName") == null ? "" : rs.getString("ItemName")); // ItemName
                    row.add(rs.getString("Quantity") == null ? "0" : rs.getString("Quantity")); // Quantity
                    row.add(rs.getString("DesignID") == null ? "" : rs.getString("DesignID")); // DesignID
                    row.add(rs.getString("LabourCost") == null ? "0" : rs.getString("LabourCost")); // L
                    row.add(rs.getString("RawCost") == null ? "0" : rs.getString("RawCost")); // Raw
                    row.add(rs.getString("DullChillaiCost") == null ? "0" : rs.getString("DullChillaiCost")); // DC
                    row.add(rs.getString("MeenaColorMeenaCost") == null ? "0" : rs.getString("MeenaColorMeenaCost")); // M/CM
                    row.add(rs.getString("RhodiumCost") == null ? "0" : rs.getString("RhodiumCost")); // Rh
                    row.add(rs.getString("NagSettingCost") == null ? "0" : rs.getString("NagSettingCost")); // Nag
                    row.add(rs.getString("OtherBaseCosts") == null ? "0" : rs.getString("OtherBaseCosts")); // Other
                    row.add(rs.getString("OtherBaseCostNotes") == null ? "" : rs.getString("OtherBaseCostNotes")); // OtherDetails
                    row.add(rs.getString("TotalBaseCosting") == null ? "0" : rs.getString("TotalBaseCosting")); // +G
                    row.add(rs.getString("GoldPlatingWeight") == null ? "0" : rs.getString("GoldPlatingWeight")); // Gold(g)
                    row.add(rs.getString("GoldRate") == null ? "0" : rs.getString("GoldRate")); // Gold Rate
                    row.add(rs.getString("TotalFinalCost") == null ? "0" : rs.getString("TotalFinalCost")); // Total

                    model.addRow(row);
                } while (rs.next());
            }

            stmt2.setInt(1, billid);
            try(ResultSet rs = stmt2.executeQuery()) {
                if (rs.next()) {
                    customer_name = rs.getString(1);
                    dateLabel.setText(UtilityMethods.parseDateString(rs.getDate(2)));
                }
                billIDLabel.setText("bill id:" + billid);
                customerNameLabel.setText(customer_name);
                curBillID = billid;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error loading bill data: " + e.getMessage(), e);
        }
        return CONSTANTS.SUCCESS_CODE;//success
    }

    int minBillID() {
        String query = "SELECT MIN(BillID) FROM billdetails";
        try (Connection con=MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1; // If no bills exist
    }

}
