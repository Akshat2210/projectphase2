package loginsignup.login.loggedin.partyManagement.modifyparty;

import loginsignup.login.loggedin.partyManagement.PartyManagementScreen;
import mainpack.MyClass;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class ModifyParty extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JComboBox<String> partyComboBox;
    private JTextField newPartyNameField;
    private JTextField newOpeningBalanceField;
    private JButton submitChangesButton;

    private final PartyManagementScreen parentWindow;

    public ModifyParty(PartyManagementScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        newOpeningBalanceField.setEnabled(false);
        loadParties();
        pack();

        partyComboBox.addActionListener(event -> {
            if (partyComboBox.getSelectedIndex() > 0) {
                newPartyNameField.setText(String.valueOf(partyComboBox.getSelectedItem()));
            } else {
                newPartyNameField.setText("");
            }
        });

        backButton.addActionListener(event -> {
            dispose();
            parentWindow.setVisible(true);
        });

        submitChangesButton.addActionListener(event -> submitChanges());
    }

    private void loadParties() {
        partyComboBox.removeAllItems();
        partyComboBox.addItem("Select Party");

        String query = "SELECT customer_name FROM customers ORDER BY customer_name";
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                partyComboBox.addItem(result.getString("customer_name"));
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Could not load parties: " + exception.getMessage(),
                    "Database error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void submitChanges() {
        if (partyComboBox.getSelectedIndex() <= 0 || partyComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Select a party to modify.");
            return;
        }

        String oldName = partyComboBox.getSelectedItem().toString().trim();
        String newName = newPartyNameField.getText().trim();

        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the new party name.");
            return;
        }
        if (newName.codePointCount(0, newName.length()) > 50) {
            JOptionPane.showMessageDialog(this, "Party name cannot exceed 50 characters.");
            return;
        }
        if (oldName.equals(newName)) {
            JOptionPane.showMessageDialog(this, "The party name has not changed.");
            return;
        }

        Set<String> reservedAccounts = Set.of(
                "Cash", "Sales", "Inventory", "Opening Balance Equity"
        );
        boolean reservedName = reservedAccounts.stream().anyMatch(account ->
                account.equalsIgnoreCase(oldName) || account.equalsIgnoreCase(newName));
        if (reservedName) {
            JOptionPane.showMessageDialog(this,
                    "Party names cannot use internal account names.");
            return;
        }

        int confirmation = JOptionPane.showConfirmDialog(this,
                "Rename '" + oldName + "' to '" + newName + "'?",
                "Confirm party rename", JOptionPane.YES_NO_OPTION);
        if (confirmation != JOptionPane.YES_OPTION) return;

        try {
            renameParty(oldName, newName);
            JOptionPane.showMessageDialog(this, "Party renamed successfully.");
            loadParties();
            partyComboBox.setSelectedIndex(0);
            newPartyNameField.setText("");
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Invalid party name", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Could not rename party: " + exception.getMessage(),
                    "Database error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameParty(String oldName, String newName) throws SQLException {
        try (Connection connection = MyClass.createConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                int customerId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT customer_id FROM customers WHERE customer_name = ? FOR UPDATE")) {
                    statement.setString(1, oldName);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("The selected party no longer exists.");
                        }
                        customerId = result.getInt("customer_id");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM customers WHERE customer_name = ? AND customer_id <> ?")) {
                    statement.setString(1, newName);
                    statement.setInt(2, customerId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            throw new IllegalArgumentException(
                                    "A party named '" + newName + "' already exists.");
                        }
                    }
                }

                // This update triggers ON UPDATE CASCADE in databases with the current schema.
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE customers SET customer_name = ? WHERE customer_id = ?")) {
                    statement.setString(1, newName);
                    statement.setInt(2, customerId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("The selected party could not be renamed.");
                    }
                }

                // Explicit updates cover non-FK columns and older databases missing a cascade.
                String[] updates = {
                        "UPDATE bills SET customer_name = ? WHERE customer_name = ?",
                        "UPDATE order_slips SET customer_name = ? WHERE customer_name = ?",
                        "UPDATE order_slips_main SET customer_name = ? WHERE customer_name = ?",
                        "UPDATE transactions SET customer_name = ? WHERE customer_name = ?",
                        "UPDATE send_packets SET customer_name = ? WHERE customer_name = ?",
                        "UPDATE journalentry SET accountname = ? WHERE accountname = ?"
                };

                for (String sql : updates) {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, newName);
                        statement.setString(2, oldName);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // The connection closes immediately after this block.
                }
            }
        }
    }
}
