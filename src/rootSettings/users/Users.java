package rootSettings.users;

import mainpack.MyClass;
import rootSettings.RootSettiingsScreen;
import rootSettings.accessmodify.AccessModify;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Users extends JFrame {
    private JButton backButton;
    private JPanel panel;
    private JList<String> userList;
    private JButton deleteUserButton;
    private JButton modifyAccessButton;
    private final RootSettiingsScreen parentWindow;

    public Users(RootSettiingsScreen parentWindow) {
    this.parentWindow=parentWindow;
    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();
        generateUserList();

        backButton.addActionListener(e -> {
            dispose();
            parentWindow.setVisible(true);
        });

        modifyAccessButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Please select a user.");
                return;
            }

            AccessModify accessModify = new AccessModify(Users.this);
            accessModify.init(selectedUser);
            accessModify.setVisible(true);
            setVisible(false);
        });

        deleteUserButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Select a user to delete.");
                return;
            }

            List<String> deletedDatabases = new ArrayList<>();
            try {
                List<String> databases = databasesGrantedTo(selectedUser);
                JPanel choices = new JPanel();
                choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
                List<JCheckBox> checkboxes = new ArrayList<>();
                for (String database : databases) {
                    JCheckBox checkbox = new JCheckBox(database);
                    checkboxes.add(checkbox);
                    choices.add(checkbox);
                }
                if (databases.isEmpty()) choices.add(new JLabel("No directly granted databases found."));
                JScrollPane scroll = new JScrollPane(choices);
                scroll.setPreferredSize(new java.awt.Dimension(480, Math.min(300, Math.max(80, databases.size() * 28))));
                Object[] message = {
                        "Delete MySQL user '" + selectedUser + "'@'%' and the databases you check below.",
                        "These databases have direct grants to this user; they may also be used by other users.",
                        scroll,
                        "Unchecked databases will be kept. Check none to delete only the user.",
                        "No backup will be created. Deletion cannot be undone."
                };
                int confirmation = JOptionPane.showOptionDialog(this, message, "Select databases to delete",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                        new String[]{"Delete user and checked databases", "Cancel"}, "Cancel");
                if (confirmation != 0) return;

                // Validate the complete selection before executing any irreversible DDL.
                List<String> selectedDatabases = checkedDatabases(checkboxes);
                validateAccountName(selectedUser);
                for (String database : selectedDatabases) {
                    deleteDatabase(database);
                    deletedDatabases.add(database);
                }
                deleteUser(selectedUser);
                generateUserList();
                JOptionPane.showMessageDialog(this, "User '" + selectedUser + "' was deleted.\nDeleted databases: "
                        + (deletedDatabases.isEmpty() ? "None" : String.join(", ", deletedDatabases)));
            } catch (SQLException | IllegalArgumentException exception) {
                // DROP DATABASE / DROP USER cannot be rolled back together.
                JOptionPane.showMessageDialog(this, "Deletion stopped: " + exception.getMessage()
                        + "\nDatabases already deleted: "
                        + (deletedDatabases.isEmpty() ? "None confirmed" : String.join(", ", deletedDatabases))
                        + "\nRefresh the server state before retrying.", "Deletion stopped", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private List<String> checkedDatabases(List<JCheckBox> checkboxes) {
        List<String> selected = new ArrayList<>();
        for (JCheckBox checkbox : checkboxes) {
            if (checkbox.isSelected()) {
                validateDatabaseName(checkbox.getText());
                selected.add(checkbox.getText());
            }
        }
        return selected;
    }

    private List<String> databasesGrantedTo(String user) throws SQLException {
        validateAccountName(user);
        // MySQL records grants, not ownership. Include schema-, table- and column-level direct grants.
        String query = "SELECT s.SCHEMA_NAME FROM information_schema.SCHEMATA s "
                + "WHERE s.SCHEMA_NAME NOT IN ('mysql','sys','information_schema','performance_schema') AND ("
                + "EXISTS (SELECT 1 FROM mysql.db d WHERE d.User=? AND d.Host='%' AND s.SCHEMA_NAME LIKE d.Db) "
                + "OR EXISTS (SELECT 1 FROM mysql.tables_priv t WHERE t.User=? AND t.Host='%' AND t.Db=s.SCHEMA_NAME) "
                + "OR EXISTS (SELECT 1 FROM mysql.columns_priv c WHERE c.User=? AND c.Host='%' AND c.Db=s.SCHEMA_NAME)) "
                + "ORDER BY s.SCHEMA_NAME";
        List<String> databases = new ArrayList<>();
        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 1; index <= 3; index++) statement.setString(index, user);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) databases.add(result.getString(1));
            }
        }
        return databases;
    }

    private void generateUserList() {
        DefaultListModel<String> model = new DefaultListModel<>();
        String query = "SELECT User FROM mysql.user WHERE Host = '%' AND User <> 'root' ORDER BY User";

        try (Connection connection = MyClass.createConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                model.addElement(resultSet.getString("User"));
            }
            userList.setModel(model);
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not load users: " + exception.getMessage());
        }
    }

    private void deleteUser(String user) throws SQLException {
        validateAccountName(user);
        String query = "DROP USER IF EXISTS '" + user + "'@'%';";

        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    private void deleteDatabase(String database) throws SQLException {
        validateDatabaseName(database);
        String query = "DROP DATABASE IF EXISTS `" + database.replace("`", "``") + "`;";

        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    private void validateAccountName(String accountName) {
        if (accountName == null || !accountName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid account name.");
        }
    }

    private void validateDatabaseName(String database) {
        if (database == null || database.isBlank() || database.indexOf('\0') >= 0
                || List.of("mysql", "sys", "information_schema", "performance_schema")
                .contains(database.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Select a non-system database.");
        }
    }
}
