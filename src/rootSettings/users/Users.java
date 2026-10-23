package rootSettings.users;

import mainpack.MyClass;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Users extends JFrame {
    private JButton backButton;
    private JPanel panel;
    private JList<String> userList;
    private JButton deleteUserButton;
    private JButton modifyAccessButton;

    public Users() {
    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();
        generateUserList();

        backButton.addActionListener(e -> {
            dispose();
            MyClass.rootSettiingsScreen.setVisible(true);
        });

        deleteUserButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Select a user to delete.");
                return;
            }

            int confirmation = JOptionPane.showConfirmDialog(
                    this,
                    "Delete user '" + selectedUser + "' and its database? This cannot be undone.",
                    "Confirm user deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirmation != JOptionPane.YES_OPTION) {
                return;
            }

            try {
                deleteDatabase(selectedUser);
                deleteUser(selectedUser);
                generateUserList();
                JOptionPane.showMessageDialog(this, "User '" + selectedUser + "' was deleted.");
            } catch (SQLException | IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(this, "Could not delete user: " + exception.getMessage());
            }
        });
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
        validateAccountName(database);
        String query = "DROP DATABASE IF EXISTS `" + database + "`;";

        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    private void validateAccountName(String accountName) {
        if (!accountName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid account name.");
        }
    }
}
