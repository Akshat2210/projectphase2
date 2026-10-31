package loginsignup.login.loggedin.transactionsandaccounts.viewTransactions;

import loginsignup.login.loggedin.transactionsandaccounts.TransactionsScreen;
import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ViewTransactions extends JFrame {
    private JButton backButton;
    private JComboBox<String> dateComboBox;
    private JComboBox<String> partyNameComboBox;
    private JPanel panel;
    private JTable transactionsTable;
    DefaultTableModel tableModel;
    private final TransactionsScreen parentWindow;

    public ViewTransactions(TransactionsScreen parentWindow) {

        this.parentWindow = parentWindow;
    }

    public void init() {
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UtilityMethods.generateAndAddDates(dateComboBox, true);
        UtilityMethods.generateAndAddNames(partyNameComboBox);
        dateComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getData();
            }
        });
        partyNameComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getData();
            }
        });
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parentWindow.setVisible(true);
                setVisible(false);
                for (ActionListener listener : partyNameComboBox.getActionListeners())
                    partyNameComboBox.removeActionListener(listener);
                for (ActionListener listener : dateComboBox.getActionListeners())
                    dateComboBox.removeActionListener(listener);
            }
        });

        String[] columnNames = new String[]{"party name", "date", "amount", "remark", "billID", "transaction_id"};
        tableModel = new DefaultTableModel(columnNames, 0){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        transactionsTable.setModel(tableModel);
        getData();

        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void getData() {

        tableModel.setRowCount(0);
        String query = "Select transaction_id,customer_name,amount,date,remark,billid from transactions ";
        boolean dateNotZero = dateComboBox.getSelectedIndex() != 0;
        boolean nameNotZero = partyNameComboBox.getSelectedIndex() != 0;
        String date = "";
        String customerName = "";
        if (dateNotZero || nameNotZero) {
            query += "WHERE ";

            if (dateNotZero) {
                date = dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString();

                query += "date = ? ";
                if (nameNotZero) {
                    customerName = partyNameComboBox.getSelectedItem() == null ? "" : partyNameComboBox.getSelectedItem().toString();
                    query += "AND customer_name = ?";
                }
            } else {
                customerName = partyNameComboBox.getSelectedItem().toString();
                query += "customer_name = ?";
            }
        }


        try (Connection con=MyClass.createConnection();PreparedStatement stmt=con.prepareStatement(query,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)){

            if (dateNotZero || nameNotZero) {
                if (dateNotZero) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy");
                    LocalDate localDate = LocalDate.parse(date, formatter);
                    java.sql.Date sqlDate = java.sql.Date.valueOf(localDate);
                    stmt.setDate(1, sqlDate);
                    if (nameNotZero) stmt.setString(2, customerName);
                } else {
                    stmt.setString(1, customerName);
                }
            }
            ResultSet rs = stmt.executeQuery();
            rs.afterLast();
            while (rs.previous()) {
                TableColumnModel columnModel = transactionsTable.getColumnModel();

                String[] values = new String[6];

                values[columnModel.getColumnIndex("amount")] = rs.getString("amount");
                values[columnModel.getColumnIndex("transaction_id")] = rs.getString("transaction_id");
                values[columnModel.getColumnIndex("date")] = rs.getString("date");
                values[columnModel.getColumnIndex("remark")] = rs.getString("remark");
                values[columnModel.getColumnIndex("party name")] = rs.getString("customer_name");
                values[columnModel.getColumnIndex("billID")] = rs.getString("billid");

                tableModel.addRow(values);

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
