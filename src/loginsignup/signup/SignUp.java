package loginsignup.signup;

import mainpack.MyClass;
import utils.CONSTANTS;

import javax.swing.*;
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
                List<String> structure = SignupDatabase.splitSchema(SignupDatabase.loadSchema());
                // Same localhost/root setup as the old batch file. No passwords in files or process arguments.
                try (Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://"+MyClass.HOST+":"+MyClass.PORT+"/mysql?useServerPrepStmts=false&characterEncoding=UTF-8",
                        "root", new String(rootPasswordCharacters))) {
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