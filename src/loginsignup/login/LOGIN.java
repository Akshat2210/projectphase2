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
    private String port = "3306";

    public String getPort() {
        return port;
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
                host = "localhost";
                url = "jdbc:mysql://" + host + ":" + port + "/" + database;
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
                if(!database.isEmpty()) {
                    MyClass.mainScreen = new MainScreen();
                    MyClass.mainScreen.init();
                    MyClass.mainScreen.setVisible(true);
                    setVisible(false);
                } else {
                    MyClass.rootSettiingsScreen = new RootSettiingsScreen();
                    MyClass.rootSettiingsScreen.init();
                    MyClass.rootSettiingsScreen.setVisible(true);}
                    setVisible(false);
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
