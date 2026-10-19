package loginsignup.login.loggedin.transactionsandaccounts.CashEntry;

import mainpack.MyClass;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class AddedTransactions extends JFrame {
    private JPanel panel;
    private JTable transactionTable;

    public AddedTransactions() {
    }

    public void init() {
        setContentPane(panel);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

    }

    public void fetchData(LocalDate date) {
        String[] columnNames = {"ID", "Date", "Party", "In", "Out"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);

        String query = "SELECT transaction_id, date, customer_name, amount FROM transactions WHERE date = ?";

        try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query);) {
            stmt.setDate(1, java.sql.Date.valueOf(date));  // Convert LocalDate to java.sql.Date
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("transaction_id");
                    java.sql.Date sqlDate = rs.getDate("date");
                    String party = rs.getString("customer_name");
                    double amount = rs.getDouble("amount");

                    // Depending on whether it's incoming or outgoing
                    Object[] row = new Object[]{
                            id,
                            sqlDate.toLocalDate(),  // back to LocalDate if needed
                            party,
                            amount >= 0 ? amount : null,
                            amount < 0 ? -amount : null
                    };
                    model.addRow(row);
                }
            }
            // At this point, you can set the model to your JTable
            transactionTable.setModel(model);

        } catch (SQLException e) {
//            throw new RuntimeException(e);
            e.printStackTrace();
            return;
        }
    }

}
