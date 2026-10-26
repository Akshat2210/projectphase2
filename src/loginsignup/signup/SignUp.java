package loginsignup.signup;

import mainpack.MyClass;
import utils.CONSTANTS;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.sql.*;
import java.util.Arrays;

public class SignUp extends JFrame {
    private JTextField userTextField;
    private JTextField authorityTextField;
    private JPasswordField passwordTextField;
    private JButton SUBMITButton;
    private JPanel panel;
    private JPasswordField rootPasswordTextField;
    private JButton backButton;


    public SignUp() {


    }

    public void init() {
        setContentPane(panel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JFrame temp = this;
        SUBMITButton.addActionListener(e -> {
            String username = userTextField.getText();
            String authority = authorityTextField.getText();
            char[] passwordCharacters = passwordTextField.getPassword();
            char[] rootPasswordCharacters = rootPasswordTextField.getPassword();
            try {
            String password = new String(passwordCharacters);
            String rootPassword = new String(rootPasswordCharacters);
            boolean exists = false;
            if (username == null || password == null || username.length() == 0 || password.length() == 0) {
                JOptionPane.showMessageDialog(this, "Username or password is empty!", "eror", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String query = "SELECT user FROM mysql.user WHERE user = ?";

            try (
                    Connection con = DriverManager.getConnection(
                            "jdbc:mysql://localhost:3306/mysql",
                            "root",
                            rootPassword
                    );
                    PreparedStatement ps = con.prepareStatement(query)
            ) {

                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery();
                ) {
                    exists = rs.next();
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() == CONSTANTS.SQL_INVALID_CREDENTIALS_ERROR) {
                    JOptionPane.showMessageDialog(this, "root password is incorrect!", "eror", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                throw new RuntimeException(ex);
            }

            if (exists) {
                JOptionPane.showMessageDialog(null,
                        "USER ALREADY EXISTS");
                return;
            }
            String batContent =
                    "@echo off\r\n" +
                            "\r\n" +
                            "echo Creating database...\r\n" +
                            "mysql -u root -p" + rootPassword +
                            " -e \"CREATE DATABASE " + username + ";\"\r\n" +
                            "if errorlevel 1 goto error\r\n" +
                            "\r\n" +
                            "echo Creating user...\r\n" +
                            "mysql -u root -p" + rootPassword +
                            " -e \"CREATE USER '" + username + "'@'%%' IDENTIFIED BY '" + password + "';\"\r\n" +
                            "if errorlevel 1 goto error\r\n" +
                            "\r\n" +
                            "echo Granting privileges...\r\n" +
                            "mysql -u root -p" + rootPassword +
                            " -e \"GRANT ALL PRIVILEGES ON " + username + ".* TO '" + username + "'@'%%';\"\r\n" +
                            "if errorlevel 1 goto error\r\n" +
                            "\r\n" +
                            "echo Importing structure...\r\n" +
                            "mysql --binary-mode=1 -u root -p" + rootPassword +
                            " " + username + " < structure.sql\r\n" +
                            "if errorlevel 1 goto error\r\n" +
                            "\r\n" +
                            "echo SUCCESS\r\n" +
                            "exit /b 0\r\n" +
                            "\r\n" +
                            ":error\r\n" +
                            "echo FAILED\r\n" +
                            "exit /b 1";
            File file = new File("src/resources/createUser.bat");
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(batContent);
                fw.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            try {
                Process p = new ProcessBuilder("cmd.exe", "/c", "createUser.bat")
                        .directory(new File("src/resources"))
                        .start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    JOptionPane.showMessageDialog(this, "User created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "User creation could not be completed.", "error", JOptionPane.ERROR_MESSAGE);
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                ) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (file.exists() && !file.delete()) {
                    System.err.println("Could not delete temporary user-creation script: " + file.getPath());
                }
            }
            } finally {
                Arrays.fill(passwordCharacters, '\0');
                Arrays.fill(rootPasswordCharacters, '\0');
                passwordTextField.setText("");
                rootPasswordTextField.setText("");
            }


        });
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                MyClass.login_signup.setVisible(true);

            }
        });
        pack();
    }
}

