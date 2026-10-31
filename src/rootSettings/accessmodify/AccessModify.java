package rootSettings.accessmodify;

import mainpack.MyClass;
import rootSettings.accessmodify.advancesetting.advancesetting;
import rootSettings.users.Users;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccessModify extends JFrame {
    private JButton backButton;
    private JPanel panel;
    private JList<String> permittedList;
    private JList<String> restrictedList;
    private JButton button1;
    private JButton button2;
    private JButton modifyButton;
    private JButton advanceButton;
    private String username;
    private final DefaultListModel<String> permittedModel = new DefaultListModel<>();
    private final DefaultListModel<String> restrictedModel = new DefaultListModel<>();
    private final Users parentWindow;

    public AccessModify(Users parentWindow) {
    this.parentWindow=parentWindow;
    }

    public void init(String username) {
        this.username = username;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        setTitle(MyClass.TITLE + ": MODIFY DATABASE ACCESS");
        permittedList.setModel(permittedModel);
        restrictedList.setModel(restrictedModel);
        permittedList.setBorder(BorderFactory.createTitledBorder("Permitted databases"));
        restrictedList.setBorder(BorderFactory.createTitledBorder("Restricted databases"));
        loadDatabaseLists();
        pack();

        backButton.addActionListener(e -> {
            dispose();
            parentWindow.setVisible(true);
        });

        button1.addActionListener(e -> moveSelectedDatabases(restrictedList, restrictedModel, permittedModel));
        button2.addActionListener(e -> moveSelectedDatabases(permittedList, permittedModel, restrictedModel));

        modifyButton.addActionListener(e -> saveAccessChanges());

        advanceButton.addActionListener(e -> {
            if (username == null || username.isBlank()) {
                JOptionPane.showMessageDialog(this,
                        "Please select a user before opening Advanced Settings.",
                        "No user selected",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            advancesetting advancedSettings = new advancesetting();
            advancedSettings.init(this, username);
            advancedSettings.setVisible(true);
            setVisible(false);
        });
    }

    private void loadDatabaseLists() {
        permittedModel.clear();
        restrictedModel.clear();
        Set<String> permittedDatabases = new HashSet<>();
        List<String> allDatabases = new ArrayList<>();

        String databasesQuery = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
                "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') " +
                "ORDER BY SCHEMA_NAME";
        String permittedQuery = "SELECT DISTINCT Db FROM mysql.db WHERE User = ? AND Host = '%' AND Db IS NOT NULL";

        try (Connection connection = MyClass.createConnection();
             Statement databaseStatement = connection.createStatement()) {

            try (ResultSet databaseResult = databaseStatement.executeQuery(databasesQuery)) {
                while (databaseResult.next()) {
                    allDatabases.add(databaseResult.getString("SCHEMA_NAME"));
                }
            }

            try (PreparedStatement permittedStatement = connection.prepareStatement(permittedQuery)) {
                permittedStatement.setString(1, username);
                try (ResultSet permittedResult = permittedStatement.executeQuery()) {
                    while (permittedResult.next()) {
                        permittedDatabases.add(permittedResult.getString("Db"));
                    }
                }
            }

            for (String database : allDatabases) {
                if (permittedDatabases.contains(database)) {
                    permittedModel.addElement(database);
                } else {
                    restrictedModel.addElement(database);
                }
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this,
                    "Could not load database access: " + exception.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            modifyButton.setEnabled(false);
        }
    }

    private void moveSelectedDatabases(JList<String> sourceList, DefaultListModel<String> sourceModel,
                                       DefaultListModel<String> targetModel) {
        List<String> selectedDatabases = sourceList.getSelectedValuesList();
        if (selectedDatabases.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a database first.");
            return;
        }

        for (String database : selectedDatabases) {
            sourceModel.removeElement(database);
            targetModel.addElement(database);
        }
        sortModel(sourceModel);
        sortModel(targetModel);
    }

    private void sortModel(DefaultListModel<String> model) {
        List<String> databases = new ArrayList<>();
        for (int index = 0; index < model.getSize(); index++) {
            databases.add(model.getElementAt(index));
        }
        Collections.sort(databases);
        model.clear();
        for (String database : databases) {
            model.addElement(database);
        }
    }

    private void saveAccessChanges() {
        int confirmation = JOptionPane.showConfirmDialog(
                this,
                "Replace database access for user '" + username + "' with the databases in the Permitted list?",
                "Confirm access modification",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement()) {
            validateAccountName(username);
            statement.executeUpdate("REVOKE ALL PRIVILEGES, GRANT OPTION FROM '" + username + "'@'%'");

            for (int index = 0; index < permittedModel.getSize(); index++) {
                String database = permittedModel.getElementAt(index);
                validateAccountName(database);
                statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + database + "`.* TO '" + username + "'@'%'");
            }

            JOptionPane.showMessageDialog(this,
                    "Database access updated successfully for '" + username + "'.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    "Could not fully update database access. Some changes may already have been applied: " + exception.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void validateAccountName(String accountName) {
        if (accountName == null || !accountName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid user or database name.");
        }
    }
}
