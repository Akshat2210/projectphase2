package rootSettings;

import mainpack.MyClass;
import rootSettings.users.Users;
import utils.Variables;

import javax.swing.*;

public class RootSettiingsScreen extends JFrame {
    private JButton usersAndDatabaseButton;
    private JPanel panel;
    private JButton logoutButton;

    public RootSettiingsScreen() {
    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        usersAndDatabaseButton.addActionListener(e -> {
            MyClass.users = new Users();
            MyClass.users.init();
            MyClass.users.setVisible(true);
            setVisible(false);
        });

        logoutButton.addActionListener(e -> {
            MyClass.login.nullLoginParameters();
            Variables.devMode = false;
            MyClass.login.setDevModeSelected(false);
            MyClass.login.setVisible(true);
            dispose();
        });
    }
}
