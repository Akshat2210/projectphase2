package rootSettings.accessmodify.advancesetting;

import mainpack.MyClass;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays database tables and per-table Read/Write access settings for a user.
 */
public class advancesetting extends JFrame {
    private static final String DATABASES_SQL =
            "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
            "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') " +
            "ORDER BY SCHEMA_NAME";
    private static final String TABLES_SQL =
            "SELECT TABLE_NAME FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
            "ORDER BY TABLE_NAME";
    private static final String DATABASE_PRIVILEGES_SQL =
            "SELECT Select_priv, Insert_priv, Update_priv, Delete_priv " +
            "FROM mysql.db WHERE User = ? AND Host = '%' AND Db = ?";
    private static final String TABLE_PRIVILEGES_SQL =
            "SELECT Table_name, Table_priv FROM mysql.tables_priv " +
            "WHERE User = ? AND Host = '%' AND Db = ?";

    private JButton backButton;
    private JList<String> databaseList;
    private JTable permissionListTable;
    private JLabel databaseNameLabel;
    private JPanel panel;

    private final DefaultListModel<String> databaseListModel = new DefaultListModel<>();
    private final DefaultTableModel permissionTableModel = new DefaultTableModel(
            new Object[]{"Table name", "Read", "Write"}, 0) {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? String.class : Boolean.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 0;
        }
    };
    private JFrame previousScreen;
    private String username;
    private boolean loadingPermissions;

    /**
     * Opens the table-permission screen for the user selected on the Users screen.
     * The username is used to load and update that user's privileges.
     */
    public void init(JFrame previousScreen, String username) {
        this.previousScreen = previousScreen;
        this.username = username;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(panel);
        setTitle(MyClass.TITLE + ": Advanced access settings - " + username);

        databaseList.setModel(databaseListModel);
        databaseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        permissionListTable.setModel(permissionTableModel);
        permissionListTable.setRowHeight(24);
        databaseNameLabel.setText("Select a database");

        databaseList.addListSelectionListener(this::databaseSelectionChanged);
        permissionTableModel.addTableModelListener(this::permissionChanged);
        backButton.addActionListener(event -> returnToPreviousScreen());

        loadDatabases();
        setMinimumSize(new Dimension(650, 400));
        pack();
        setLocationRelativeTo(previousScreen);
    }

    private void databaseSelectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            loadTables(databaseList.getSelectedValue());
        }
    }

    private void loadDatabases() {
        databaseListModel.clear();
        clearTableList("Select a database");

        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(DATABASES_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                databaseListModel.addElement(resultSet.getString("SCHEMA_NAME"));
            }
        } catch (SQLException exception) {
            showDatabaseError("Could not load the database list", exception);
        }
    }

    private void loadTables(String databaseName) {
        clearTableList(databaseName == null ? "Select a database" : "Tables in " + databaseName);
        if (databaseName == null) {
            return;
        }

        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(TABLES_SQL)) {
            PrivilegeState databasePrivileges = getDatabasePrivileges(connection, databaseName);
            Map<String, PrivilegeState> tablePrivileges = getTablePrivileges(connection, databaseName);

            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                loadingPermissions = true;
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    PrivilegeState tablePrivilege = tablePrivileges.getOrDefault(tableName, new PrivilegeState());
                    PrivilegeState effectivePrivileges = databasePrivileges.mergedWith(tablePrivilege);
                    boolean canRead = effectivePrivileges.read;
                    boolean canWrite = effectivePrivileges.hasWritePermission();
                    permissionTableModel.addRow(new Object[]{tableName, canRead, canWrite});
                }
                loadingPermissions = false;
            }
        } catch (SQLException exception) {
            loadingPermissions = false;
            showDatabaseError("Could not load tables for '" + databaseName + "'", exception);
        }
    }

    /**
     * Applies an edit immediately. Database-wide read/write grants are first
     * converted to equivalent table grants, so an unchecked box genuinely removes
     * access to only that table instead of being overridden by a database grant.
     */
    private void permissionChanged(TableModelEvent event) {
        if (loadingPermissions || event.getType() != TableModelEvent.UPDATE || event.getColumn() == 0) {
            return;
        }

        String databaseName = databaseList.getSelectedValue();
        if (databaseName == null || username == null || username.isBlank()) {
            return;
        }

        try {
            saveTablePermissions(databaseName, event.getFirstRow(), event.getColumn());
            loadTables(databaseName);
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    "Could not update table permissions. The screen will be refreshed with the actual saved permissions.\n"
                            + exception.getMessage(),
                    "Permission update failed",
                    JOptionPane.ERROR_MESSAGE);
            loadTables(databaseName);
        }
    }

    private void saveTablePermissions(String databaseName, int changedRow, int changedColumn) throws SQLException {
        validateAccountName(username);

        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement()) {
            PrivilegeState databasePrivileges = getDatabasePrivileges(connection, databaseName);
            Map<String, PrivilegeState> directTablePrivileges = getTablePrivileges(connection, databaseName);
            Map<String, PrivilegeState> requestedPermissions = effectivePermissionsForAllTables(
                    databasePrivileges, directTablePrivileges, changedRow, changedColumn);

            revokeDatabasePrivileges(statement, databaseName, databasePrivileges);

            for (Map.Entry<String, PrivilegeState> entry : requestedPermissions.entrySet()) {
                String tableName = entry.getKey();
                PrivilegeState requested = entry.getValue();
                PrivilegeState direct = directTablePrivileges.getOrDefault(tableName, new PrivilegeState());

                revokeUnrequestedTablePrivileges(statement, databaseName, tableName, direct, requested);
                grantRequestedTablePrivileges(statement, databaseName, tableName, direct, requested);
            }
        }
    }

    private Map<String, PrivilegeState> effectivePermissionsForAllTables(PrivilegeState databasePrivileges,
                                                                          Map<String, PrivilegeState> directTablePrivileges,
                                                                          int changedRow, int changedColumn) {
        Map<String, PrivilegeState> permissions = new HashMap<>();
        for (int row = 0; row < permissionTableModel.getRowCount(); row++) {
            String tableName = (String) permissionTableModel.getValueAt(row, 0);
            PrivilegeState effective = databasePrivileges.mergedWith(
                    directTablePrivileges.getOrDefault(tableName, new PrivilegeState()));
            if (row == changedRow) {
                boolean selected = Boolean.TRUE.equals(permissionTableModel.getValueAt(row, changedColumn));
                effective = changedColumn == 1
                        ? effective.withRead(selected)
                        : effective.withWrite(selected);
            }
            permissions.put(tableName, effective);
        }
        return permissions;
    }

    private PrivilegeState getDatabasePrivileges(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DATABASE_PRIVILEGES_SQL)) {
            statement.setString(1, username);
            statement.setString(2, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new PrivilegeState();
                }
                return new PrivilegeState(isGranted(resultSet.getString("Select_priv")),
                        isGranted(resultSet.getString("Insert_priv")),
                        isGranted(resultSet.getString("Update_priv")),
                        isGranted(resultSet.getString("Delete_priv")));
            }
        }
    }

    private Map<String, PrivilegeState> getTablePrivileges(Connection connection, String databaseName) throws SQLException {
        Map<String, PrivilegeState> permissions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(TABLE_PRIVILEGES_SQL)) {
            statement.setString(1, username);
            statement.setString(2, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String privileges = resultSet.getString("Table_priv").toUpperCase();
                    permissions.put(resultSet.getString("Table_name"),
                            new PrivilegeState(privileges.contains("SELECT"),
                                    privileges.contains("INSERT"),
                                    privileges.contains("UPDATE"),
                                    privileges.contains("DELETE")));
                }
            }
        }
        return permissions;
    }

    private void revokeDatabasePrivileges(Statement statement, String databaseName, PrivilegeState privileges)
            throws SQLException {
        if (privileges.hasPrivileges()) {
            statement.executeUpdate("REVOKE " + privilegeList(privileges) + " ON " + quoteIdentifier(databaseName)
                    + ".* FROM " + quoteUser());
        }
    }

    private void revokeUnrequestedTablePrivileges(Statement statement, String databaseName, String tableName,
                                                   PrivilegeState current, PrivilegeState requested) throws SQLException {
        PrivilegeState toRevoke = current.notIncludedIn(requested);
        if (toRevoke.hasPrivileges()) {
            statement.executeUpdate("REVOKE " + privilegeList(toRevoke) + " ON " + quoteIdentifier(databaseName)
                    + "." + quoteIdentifier(tableName) + " FROM " + quoteUser());
        }
    }

    private void grantRequestedTablePrivileges(Statement statement, String databaseName, String tableName,
                                               PrivilegeState current, PrivilegeState requested) throws SQLException {
        PrivilegeState toGrant = requested.notIncludedIn(current);
        if (toGrant.hasPrivileges()) {
            statement.executeUpdate("GRANT " + privilegeList(toGrant) + " ON " + quoteIdentifier(databaseName)
                    + "." + quoteIdentifier(tableName) + " TO " + quoteUser());
        }
    }

    private String privilegeList(PrivilegeState privileges) {
        List<String> privilegeNames = new ArrayList<>();
        if (privileges.read) {
            privilegeNames.add("SELECT");
        }
        if (privileges.insert) {
            privilegeNames.add("INSERT");
        }
        if (privileges.update) {
            privilegeNames.add("UPDATE");
        }
        if (privileges.delete) {
            privilegeNames.add("DELETE");
        }
        return String.join(", ", privilegeNames);
    }

    private String quoteUser() {
        return "'" + username + "'@'%" + "'";
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private boolean isGranted(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private void clearTableList(String label) {
        databaseNameLabel.setText(label);
        permissionTableModel.setRowCount(0);
    }

    private void showDatabaseError(String message, SQLException exception) {
        JOptionPane.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Database error", JOptionPane.ERROR_MESSAGE);
    }

    private void returnToPreviousScreen() {
        if (previousScreen != null) {
            previousScreen.setVisible(true);
        }
        dispose();
    }

    private void validateAccountName(String accountName) {
        if (accountName == null || !accountName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid user name.");
        }
    }

    private static final class PrivilegeState {
        private final boolean read;
        private final boolean insert;
        private final boolean update;
        private final boolean delete;

        private PrivilegeState() {
            this(false, false, false, false);
        }

        private PrivilegeState(boolean read, boolean insert, boolean update, boolean delete) {
            this.read = read;
            this.insert = insert;
            this.update = update;
            this.delete = delete;
        }

        private boolean hasPrivileges() {
            return read || insert || update || delete;
        }

        private boolean hasWritePermission() {
            return insert || update || delete;
        }

        private PrivilegeState mergedWith(PrivilegeState other) {
            return new PrivilegeState(read || other.read, insert || other.insert,
                    update || other.update, delete || other.delete);
        }

        private PrivilegeState notIncludedIn(PrivilegeState other) {
            return new PrivilegeState(read && !other.read, insert && !other.insert,
                    update && !other.update, delete && !other.delete);
        }

        private PrivilegeState withRead(boolean allowed) {
            return new PrivilegeState(allowed, insert, update, delete);
        }

        private PrivilegeState withWrite(boolean allowed) {
            return new PrivilegeState(read, allowed, allowed, allowed);
        }
    }
}
