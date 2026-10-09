package loginsignup.login.loggedin.rootAdmin;

import mainpack.MyClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RootScreen extends JFrame {
    private JButton backButton;

    public RootScreen() {

    }

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                MyClass.login.setVisible(true);
            }
        });

    }
}
