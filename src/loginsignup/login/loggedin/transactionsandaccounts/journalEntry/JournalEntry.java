package loginsignup.login.loggedin.transactionsandaccounts.journalEntry;

import loginsignup.login.loggedin.transactionsandaccounts.TransactionsScreen;
import mainpack.MyClass;
import utils.CONSTANTS;
import utils.DBStructure;
import utils.UtilityMethods;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.TreeSet;

public class JournalEntry extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton submitButton;
    private JComboBox<String> dateComboBox;
    private JComboBox<String> comboBox2;
    private JComboBox<String> comboBox3;
    private JTextField textField1;
    private JTextField textField2;
    private final TransactionsScreen parentWindow;

    public JournalEntry(TransactionsScreen parentWindow) {
        this.parentWindow = parentWindow;
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitDirectJournal();
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
            parentWindow.setVisible(true);
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

    private void submitDirectJournal() {
        String creditAccount = (String) comboBox2.getSelectedItem();
        String debitAccount = (String) comboBox3.getSelectedItem();
        String amountText = textField1.getText().trim();
        String remark = textField2.getText().trim();

        if (creditAccount == null || creditAccount.equals("Select Account") ||
                debitAccount == null || debitAccount.equals("Select Account")) {
            JOptionPane.showMessageDialog(this, "Select both debit and credit accounts.");
            return;
        }
        if (creditAccount.equalsIgnoreCase(debitAccount)) {
            JOptionPane.showMessageDialog(this, "Debit and credit accounts must be different.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
            if (amount.signum() <= 0 || amount.scale() > 2) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Enter a positive amount with no more than two decimal places.");
            return;
        }

        java.sql.Date entryDate;
        try {
            String selectedDate = (String) dateComboBox.getSelectedItem();
            entryDate = java.sql.Date.valueOf(LocalDate.parse(selectedDate, DateTimeFormatter.ofPattern("dd-MM-yy")));
        } catch (DateTimeParseException | NullPointerException exception) {
            JOptionPane.showMessageDialog(this, "Select a valid date.");
            return;
        }

        String journalQuery = "INSERT INTO " + DBStructure.JOURNAL_ENTRY_TABLE + " (" +
                DBStructure.JOURNAL_ENTRY_TRANSACTION_TYPE + ", " +
                DBStructure.JOURNAL_ENTRY_DATE + ", " +
                DBStructure.JOURNAL_ENTRY_ACCOUNT_NAME + ", " +
                DBStructure.JOURNAL_ENTRY_REMARK + ", " +
                DBStructure.JOURNAL_ENTRY_BILL_ID + ", " +
                DBStructure.JOURNAL_ENTRY_TRANSACTION_ID + ", " +
                DBStructure.JOURNAL_ENTRY_CREDIT + ", " +
                DBStructure.JOURNAL_ENTRY_DEBIT + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String customerBalanceQuery = "UPDATE " + DBStructure.CUSTOMERS_TABLE + " SET " +
                DBStructure.CUSTOMERS_BALANCE + " = " + DBStructure.CUSTOMERS_BALANCE + " + ? WHERE " +
                DBStructure.CUSTOMERS_CUSTOMER_NAME + " = ?";

        try (Connection connection = MyClass.createConnection();
             PreparedStatement journalStatement = connection.prepareStatement(journalQuery);
             PreparedStatement customerBalanceStatement = connection.prepareStatement(customerBalanceQuery)) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                addJournalLine(journalStatement, entryDate, creditAccount, remark, amount, BigDecimal.ZERO);
                addJournalLine(journalStatement, entryDate, debitAccount, remark, BigDecimal.ZERO, amount);
                journalStatement.executeBatch();

                updateCustomerBalance(customerBalanceStatement, creditAccount, amount.negate());
                updateCustomerBalance(customerBalanceStatement, debitAccount, amount);

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

            textField1.setText("");
            textField2.setText("");
            comboBox2.setSelectedIndex(0);
            comboBox3.setSelectedIndex(0);
            JOptionPane.showMessageDialog(this, "Direct journal entry saved.");
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not save journal entry: " + exception.getMessage());
        }
    }

    private void addJournalLine(PreparedStatement statement, java.sql.Date entryDate, String accountName,
                                String remark, BigDecimal credit, BigDecimal debit) throws SQLException {
        statement.setString(1, CONSTANTS.DIRECT_JOURNAL_ENUM);
        statement.setDate(2, entryDate);
        statement.setString(3, accountName);
        if (remark.isEmpty()) {
            statement.setNull(4, Types.VARCHAR);
        } else {
            statement.setString(4, remark);
        }
        statement.setNull(5, Types.INTEGER);
        statement.setNull(6, Types.INTEGER);
        statement.setBigDecimal(7, credit);
        statement.setBigDecimal(8, debit);
        statement.addBatch();
    }

    private void updateCustomerBalance(PreparedStatement statement, String accountName, BigDecimal change)
            throws SQLException {
        statement.setBigDecimal(1, change);
        statement.setString(2, accountName);
        statement.executeUpdate();
    }
}
