package loginsignup.login.loggedin.transactionsandaccounts.journalEntry;

import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

public class journalEntry extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton submitButton;
    private JComboBox<String> dateComboBox;
    private JComboBox<String> comboBox2;
    private JComboBox<String> comboBox3;
    private JTextField textField1;

    public journalEntry() {
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //heygpt add the submit code here
                //heygpt also update the customers mysql table balance for appropriate transactions

            }
        });
    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("Journal Entry");
        setContentPane(panel);
        pack();
        UtilityMethods.generateAndAddDates(dateComboBox,false);
        populateAccountComboBoxes();
        backButton.addActionListener(e -> {
            dispose();
            MyClass.transactions.setVisible(true);
        });
    }

    private void populateAccountComboBoxes() {
        Set<String> accounts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        accounts.add("Bank");
        accounts.add("Cash");
        accounts.add("Expense");
        accounts.add("Income");
        accounts.add("Purchase");
        accounts.add("Sale");

        String customerQuery = "SELECT customer_name FROM customers " +
                "WHERE customer_name IS NOT NULL AND TRIM(customer_name) <> ''";
        try (Connection con = MyClass.createConnection();
             PreparedStatement statement = con.prepareStatement(customerQuery);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(resultSet.getString("customer_name").trim());
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not load accounts: " + exception.getMessage());
            return;
        }

        addAccountsToComboBox(comboBox2, accounts);
        addAccountsToComboBox(comboBox3, accounts);
    }

    private void addAccountsToComboBox(JComboBox<String> comboBox, Set<String> accounts) {
        comboBox.removeAllItems();
        comboBox.addItem("Select Account");
        for (String account : accounts) {
            comboBox.addItem(account);
        }
    }
}
