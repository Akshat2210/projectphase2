package loginsignup.login;

import loginsignup.login.loggedin.MainScreen;
import mainpack.MyClass;
import utils.UtilityMethods;
import utils.Variables;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.SQLException;

public class LOGIN extends JFrame {
    private JTextField userTextField;

    public void setUserText(String userText) {
        userTextField.setText(userText);
    }

    public void setPasswordText(String password) {
        passwordField.setText(password);
    }

    private JTextField passwordField;

    public JTextField getPasswordField() {
        return passwordField;
    }

    private JButton LOGINButton;

    private JButton QUITButton;
    private JButton BACKButton;
    private JPanel panel;
    private JCheckBox isDevMode;
    private JTextField databaseField;
    private String loginID;
    private String password;
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
        return password;
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

                loginID = userTextField.getText();
                if (!"root".contentEquals(loginID) && !databaseField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(temp, "please do not enter database name for  a non root user");
                    return;
                }
                host = "localhost";
                if (loginID.contentEquals("root"))
                    database = databaseField.getText();
                else
                    database = loginID;
                url = "jdbc:mysql://" + host + ":" + port + "/" + database;
                password = passwordField.getText();

                try (Connection con = MyClass.createConnection();
                ) {
                    if (con == null) {
                        nullLoginParameters();
                        return;
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                MyClass.mainScreen = new MainScreen();
                MyClass.mainScreen.init();
                MyClass.mainScreen.setVisible(true);
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
        password = null;
    }

    public void setDevModeSelected(boolean b) {
        isDevMode.setSelected(b);
    }
}
