package rootSettings.datamanager;

import mainpack.MyClass;
import org.jdesktop.swingx.JXMonthView;
import rootSettings.RootSettiingsScreen;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

public class DataManager extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JList<String> databaselist;
    private JButton deleteTillDateButton;
    private JButton deleteAllDataButton;
    private final RootSettiingsScreen parentWindow;

    public DataManager(RootSettiingsScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        setTitle(MyClass.TITLE + ": DATA MANAGER");
        databaselist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loadDatabases();
        pack();

        backButton.addActionListener(e -> {
            dispose();
            parentWindow.setVisible(true);
        });
        deleteAllDataButton.addActionListener(e -> confirmDeleteAll());
        deleteTillDateButton.addActionListener(e -> confirmDeleteTillDate());
    }

    private void loadDatabases() {
        DefaultListModel<String> model = new DefaultListModel<>();
        databaselist.setModel(model);
        String query = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
                "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') " +
                "ORDER BY SCHEMA_NAME";
        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            while (result.next()) {
                model.addElement(result.getString("SCHEMA_NAME"));
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not load databases: " + exception.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String selectedDatabase() {
        String database = databaselist.getSelectedValue();
        if (database == null) {
            JOptionPane.showMessageDialog(this, "Please select a database first.");
        }
        return database;
    }

    private void confirmDeleteAll() {
        String database = selectedDatabase();
        if (database == null) return;

        String expected = database.toUpperCase(Locale.ROOT);
        JTextField confirmation = new JTextField(24);
        Object[] fields = {
                "Delete all data in database '" + database + "'.",
                "Enter the database name in CAPITAL letters: " + expected,
                confirmation
        };
        while (JOptionPane.showConfirmDialog(this, fields, "Confirm delete all data",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            if (!expected.equals(confirmation.getText())) {
                JOptionPane.showMessageDialog(this, "Enter exactly: " + expected,
                        "Confirmation does not match", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            // TODO: Implement deletion for the original selected database name.
            JOptionPane.showMessageDialog(this, "Confirmation accepted for '" + database +
                    "'. Deletion is not implemented yet. No data was deleted.");
            return;
        }
    }

    private void confirmDeleteTillDate() {
        String database = selectedDatabase();
        if (database == null) return;

        JXMonthView calendar = new JXMonthView();
        calendar.setTraversable(true);
        calendar.setSelectionDate(new Date());
        JTextField confirmation = new JTextField(24);
        Object[] fields = {
                "Delete data through the selected date in database '" + database + "'.",
                "Select the cutoff date (inclusive):",
                calendar,
                "Type CONFIRM in CAPITAL letters:",
                confirmation
        };
        while (JOptionPane.showConfirmDialog(this, fields, "Confirm delete till date",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            Date selectedDate = calendar.getSelectionDate();
            if (selectedDate == null) {
                JOptionPane.showMessageDialog(this, "Please select a date.");
                continue;
            }
            if (!"CONFIRM".equals(confirmation.getText())) {
                JOptionPane.showMessageDialog(this, "Type exactly CONFIRM to continue.",
                        "Confirmation does not match", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            LocalDate cutoff = selectedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            try {
                deleteTill(database, cutoff);
                JOptionPane.showMessageDialog(this, "Data in '" + database + "' deleted through " +
                        new SimpleDateFormat("dd MMM yyyy").format(selectedDate) +
                        ". Account balances and stock have been carried forward.");
            } catch (SQLException exception) {
                JOptionPane.showMessageDialog(this, "Deletion failed: " + exception.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
    }

    /** Compacts history through cutoff (inclusive); called only after UI confirmation. */
    private void deleteTill(String database, LocalDate cutoff) throws SQLException {
        if (database == null || cutoff == null ||
                java.util.Arrays.asList("mysql", "sys", "information_schema", "performance_schema")
                        .contains(database.toLowerCase(Locale.ROOT))) {
            throw new SQLException("Select a non-system database and a valid cutoff date.");
        }
        java.sql.Date endExclusive = java.sql.Date.valueOf(cutoff.plusDays(1));
        try (Connection connection = MyClass.createConnection()) {
            // Root login may have no default database. Use the selected catalog, not login's catalog.
            connection.setCatalog(database);
            if (!database.equals(connection.getCatalog())) {
                throw new SQLException("Could not select the requested database.");
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                // Lock existing rows and ranges while snapshots and deletions are performed.
                String[] locks = {"SELECT customer_id FROM customers FOR UPDATE",
                        "SELECT DESIGNID FROM inventory FOR UPDATE", "SELECT BillID FROM bills FOR UPDATE",
                        "SELECT itemid_bill FROM billdetails FOR UPDATE",
                        "SELECT transaction_id FROM transactions FOR UPDATE", "SELECT id FROM journalentry FOR UPDATE"};
                for (String query : locks) {
                    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
                        while (rows.next()) { /* Consume the locking read. */ }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM bills WHERE date < ? AND COALESCE(drafts, 0) <> 0")) {
                    statement.setDate(1, endExclusive);
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        if (rows.getLong(1) != 0) {
                            throw new SQLException("Resolve draft bills before deleting this date range.");
                        }
                    }
                }

                Map<String, BigDecimal> opening = new LinkedHashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT accountname, SUM(debit - credit) AS net FROM journalentry " +
                                "WHERE date < ? GROUP BY accountname")) {
                    statement.setDate(1, endExclusive);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) opening.put(rows.getString("accountname"), rows.getBigDecimal("net"));
                    }
                }

                // Customer.balance already contains these movements: do not change it.
                // Use bills/receipts for legacy history, plus direct journals, exactly once.
                executeCutoff(connection, "UPDATE customers c LEFT JOIN (" +
                        "SELECT b.customer_name, SUM(bd.TotalFinalCost) AS net FROM bills b " +
                        "JOIN billdetails bd ON bd.BillID = b.BillID WHERE b.date < ? GROUP BY b.customer_name" +
                        ") b ON b.customer_name = c.customer_name LEFT JOIN (" +
                        "SELECT customer_name, SUM(amount) AS net FROM transactions WHERE date < ? GROUP BY customer_name" +
                        ") t ON t.customer_name = c.customer_name LEFT JOIN (" +
                        "SELECT accountname, SUM(debit-credit) AS net FROM journalentry " +
                        "WHERE date < ? AND transaction_type = 'direct' GROUP BY accountname" +
                        ") j ON j.accountname = c.customer_name " +
                        "SET c.openingaccount = COALESCE(c.openingaccount,0) + COALESCE(b.net,0) " +
                        "- COALESCE(t.net,0) + COALESCE(j.net,0)", endExclusive, 3);

                // Preserve TotalQuantity + openingstock and the item ledger's opening quantity.
                executeCutoff(connection, "UPDATE inventory i JOIN (" +
                        "SELECT bd.DesignID, SUM(bd.quantity) AS quantity FROM billdetails bd " +
                        "JOIN bills b ON b.BillID = bd.BillID WHERE b.date < ? GROUP BY bd.DesignID" +
                        ") x ON x.DesignID = i.DESIGNID SET i.openingstock = COALESCE(i.openingstock,0) " +
                        "- COALESCE(x.quantity,0), i.TotalQuantity = i.TotalQuantity + COALESCE(x.quantity,0)",
                        endExclusive, 1);

                // Detach surviving children before ON DELETE CASCADE can remove them.
                executeCutoff(connection, "UPDATE journalentry j JOIN bills b ON b.BillID = j.billid " +
                        "SET j.billid = NULL WHERE b.date < ? AND j.date >= ?", endExclusive, 2);
                executeCutoff(connection, "UPDATE journalentry j JOIN transactions t ON t.transaction_id = j.transaction_id " +
                        "SET j.transaction_id = NULL WHERE t.date < ? AND j.date >= ?", endExclusive, 2);
                executeCutoff(connection, "UPDATE transactions t JOIN bills b ON b.BillID = t.billid " +
                        "SET t.billid = NULL WHERE b.date < ? AND (t.date >= ? OR t.date IS NULL)", endExclusive, 2);

                executeCutoff(connection, "DELETE FROM journalentry WHERE date < ?", endExclusive, 1);
                executeCutoff(connection, "DELETE FROM transactions WHERE date < ?", endExclusive, 1);
                executeCutoff(connection, "DELETE FROM bills WHERE date < ?", endExclusive, 1);

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO journalentry (transaction_type,date,accountname,remark,billid,transaction_id,debit,credit) " +
                                "VALUES ('opening_balance',?,?,?,NULL,NULL,?,?)")) {
                    for (Map.Entry<String, BigDecimal> entry : opening.entrySet()) {
                        BigDecimal net = entry.getValue();
                        if (net.signum() == 0) continue;
                        statement.setDate(1, java.sql.Date.valueOf(cutoff));
                        statement.setString(2, entry.getKey());
                        statement.setString(3, "Balance carried forward through " + cutoff);
                        statement.setBigDecimal(4, net.max(BigDecimal.ZERO));
                        statement.setBigDecimal(5, net.negate().max(BigDecimal.ZERO));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    private void executeCutoff(Connection connection, String sql, java.sql.Date boundary, int parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= parameters; index++) statement.setDate(index, boundary);
            statement.executeUpdate();
        }
    }
}
