package loginsignup.login;

import loginsignup.login.loggedin.MainScreen;
import loginsignup.login.loggedin.rootAdmin.RootScreen;
import mainpack.MyClass;
import rootSettings.RootSettiingsScreen;
import utils.CONSTANTS;
import utils.UtilityMethods;
import utils.Variables;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

public class LOGIN extends JFrame {
    private JTextField userTextField;

    public void setUserText(String userText) {
        userTextField.setText(userText);
    }

    public void setPasswordText(String password) {
        passwordField.setText(password);
    }

    public void setDatabaseText(String database) {
        databaseField.setText(database);
    }

    private JPasswordField passwordField;

    public JPasswordField getPasswordField() {
        return passwordField;
    }

    private JButton LOGINButton;

    private JButton QUITButton;
    private JButton BACKButton;
    private JPanel panel;
    private JCheckBox isDevMode;
    private JTextField databaseField;
    private String loginID;
    private char[] password;
    private String host;
    private String database = "";
    private String port = MyClass.PORT;

    public String getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getUrl() {
        return url;
    }

    public String getDatabase() {
        return database;
    }

    private String url;

    public String getPassword() {
        return password == null ? null : new String(password);
    }

    public String getLoginID() {
        return loginID;
    }

    public LOGIN() {
//        init();
        isDevMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isDevMode.isSelected()) {
                    String tempPass = JOptionPane.showInputDialog(LOGIN.this, "input devveloper password:");

                    if (!UtilityMethods.parseString(tempPass).equals("gurukripa")) {

                        JOptionPane.showMessageDialog(LOGIN.this, "wrong dev pass.. if you are not a developer.. please stay away bro");
                        isDevMode.setSelected(false);
                        Variables.devMode = false;
                    }else {
                        Variables.devMode =true;

                    }
                }else {Variables.devMode =false;}
            }
        });
    }

    public void init() {
        setContentPane(panel);
        setTitle(MyClass.TITLE + ": LOGIN WINDOW");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        QUITButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        pack();
        BACKButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                MyClass.login_signup.setVisible(true);

            }
        });
        LOGIN temp = this;
        LOGINButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                loginID = userTextField.getText().trim();
                database = databaseField.getText().trim();
                if (!"root".contentEquals(loginID) && database.isEmpty()) {
                    database = loginID;
                }
                host = MyClass.HOST;
                url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?connectTimeout=" + MyClass.TIMEOUT
                        + "&socketTimeout=" + MyClass.TIMEOUT;
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
                password = passwordField.getPassword();

                try (Connection con = MyClass.createConnection();
                ) {
                    if (con == null) {
                        nullLoginParameters();
                        return;
                    }
                } catch (SQLException ex) {
                    if (ex.getErrorCode() == CONSTANTS.SQL_DATABASE_ACCESS_DENIED_ERROR) {
                        JOptionPane.showMessageDialog(temp,
                                "This user does not have access to database '" + database + "'.",
                                "Access denied",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    nullLoginParameters();
                    return;
                }
                try {
                    clearDataFolder();
                } catch (java.io.IOException | SecurityException ex) {
                    JOptionPane.showMessageDialog(LOGIN.this,
                            "Login succeeded, but the data folder could not be completely cleared.\n" + ex.getMessage(),
                            "Data folder cleanup", JOptionPane.WARNING_MESSAGE);
                }
                if(!database.isEmpty()) {
                    MyClass.mainScreen = new MainScreen();
                    MyClass.mainScreen.init();
                    MyClass.mainScreen.setVisible(true);
                    setVisible(false);
                } else {
                    RootSettiingsScreen rootSettiingsScreen = new RootSettiingsScreen();
                    rootSettiingsScreen.init();
                    rootSettiingsScreen.setVisible(true);}
                    setVisible(false);
            }
        });

    }

    /** Clears generated files after successful login, retaining the data folder itself. */
    public static void clearDataFolder() throws java.io.IOException {
        java.nio.file.Path directory = java.nio.file.Paths.get("data").toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        // Refuse a redirected root so cleanup cannot reach outside the intended data directory.
        if (java.nio.file.Files.isSymbolicLink(directory)
                || !java.nio.file.Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory)) {
            throw new java.io.IOException("The data folder is not a regular local directory: " + directory);
        }
        java.nio.file.Files.walkFileTree(directory, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path folder,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws java.io.IOException {
                if (!folder.toAbsolutePath().normalize().startsWith(directory)
                        || !folder.toRealPath().startsWith(directory)) {
                    throw new java.io.IOException("Refusing to clear a directory outside data: " + folder);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws java.io.IOException {
                // walkFileTree does not follow symbolic links; delete the link itself, not its target.
                java.nio.file.Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path folder,
                    java.io.IOException error) throws java.io.IOException {
                if (error != null) throw error;
                if (!folder.equals(directory)) java.nio.file.Files.delete(folder);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
    public void clickLoginButton() {
        LOGINButton.doClick();
    }

    public void nullLoginParameters() {
        database = null;
        loginID = null;
        if (password != null) {
            Arrays.fill(password, '\0');
        }
        password = null;
    }

    public void setDevModeSelected(boolean b) {
        isDevMode.setSelected(b);
    }
}
