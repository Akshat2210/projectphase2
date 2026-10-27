package rootSettings;

import mainpack.MyClass;
import rootSettings.datamanager.DataManager;
import rootSettings.users.Users;
import utils.Variables;

import javax.swing.*;

public class RootSettiingsScreen extends JFrame {
    private JButton usersAndDatabaseButton;
    private JPanel panel;
    private JButton logoutButton;
    private JButton dataManagerButton;

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

        dataManagerButton.addActionListener(e -> {
            MyClass.dataManager = new DataManager();
            MyClass.dataManager.init();
            MyClass.dataManager.setVisible(true);
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

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
