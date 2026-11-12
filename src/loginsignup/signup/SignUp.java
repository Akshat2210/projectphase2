package loginsignup.signup;

import mainpack.MyClass;
import utils.CONSTANTS;

import javax.swing.*;
import java.nio.file.Path;
import java.sql.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SignUp extends JFrame {
    private JTextField userTextField;
    private JTextField authorityTextField;
    private JPasswordField passwordTextField;
    private JButton SUBMITButton;
    private JPanel panel;
    private JPasswordField rootPasswordTextField;
    private JButton backButton;
    private JTextField adminUserNameField;

    public SignUp() { }

    public void init() {
        setContentPane(panel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        SUBMITButton.addActionListener(event -> {

            String username = userTextField.getText().trim();
            char[] passwordCharacters = passwordTextField.getPassword();
            char[] rootPasswordCharacters = rootPasswordTextField.getPassword();
            SUBMITButton.setEnabled(false);

            try {
                SignupDatabase.validateUsername(username);
                if (passwordCharacters.length == 0) {
                    throw new IllegalArgumentException("Username or password is empty!");
                }
                // Read and validate the complete import before creating anything in MySQL.
                Object[] databaseOptions = {"Blank database", "Restore from SQL backup", "Cancel"};
                int option = JOptionPane.showOptionDialog(
                        SignUp.this,
                        "How should the new database be created?",
                        "Choose database setup",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        databaseOptions,
                        databaseOptions[0]);

                Path path;
                if (option == 0) {
                    path = Path.of("src", "resources", "structure.sql");
                } else if (option == 1) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Select SQL backup file");
                    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    fileChooser.setAcceptAllFileFilterUsed(false);
                    fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                            "SQL files (*.sql)", "sql"));

                    if (fileChooser.showOpenDialog(SignUp.this) != JFileChooser.APPROVE_OPTION) {
                        return;
                    }
                    path = fileChooser.getSelectedFile().toPath();
                    if (!java.nio.file.Files.isRegularFile(path)) {
                        throw new IOException("Select a valid SQL backup file.");
                    }
                } else {
                    return;
                }
                List<String> structure = SignupDatabase.splitSchema(SignupDatabase.loadSchema(path));
                // Same localhost/root setup as the old batch file. No passwords in files or process arguments.
                String admin=adminUserNameField.getText().trim();
                admin=admin.isEmpty()?"q":admin;
                try (Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://" + MyClass.HOST + ":" + MyClass.PORT
                                + "/mysql?useServerPrepStmts=false&characterEncoding=UTF-8"
                                + "&connectTimeout=" + MyClass.TIMEOUT
                                + "&socketTimeout=" + MyClass.TIMEOUT,
                        admin, new String(rootPasswordCharacters))) {
                    SignupDatabase.create(connection, username, new String(passwordCharacters), structure);
                }
                JOptionPane.showMessageDialog(this, "User created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IllegalArgumentException | IOException error) {
                JOptionPane.showMessageDialog(this, error.getMessage(), "Signup not completed", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException error) {
                String message;
                if (error.getErrorCode() == CONSTANTS.SQL_INVALID_CREDENTIALS_ERROR) {
                    message = "Root password is incorrect, or root access was denied.";
                } else if (error.getMessage() != null && (error.getMessage().startsWith("USER ALREADY EXISTS")
                        || error.getMessage().contains(" failed (MySQL error "))) {
                    message = error.getMessage(); // SignupDatabase supplies sanitized step-level messages.
                } else {
                    message = "Could not complete signup (MySQL error " + error.getErrorCode() + ").";
                }
                JOptionPane.showMessageDialog(this, message, "Signup not completed", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(passwordCharacters, '\0');
                Arrays.fill(rootPasswordCharacters, '\0');
                passwordTextField.setText("");
                rootPasswordTextField.setText("");
                SUBMITButton.setEnabled(true);
            }
        });
        backButton.addActionListener(event -> {
            dispose();
            MyClass.login_signup.setVisible(true);
        });
        pack();
    }
}
