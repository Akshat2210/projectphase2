package loginsignup.login.loggedin.processingmanagement.godowns;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;
import loginsignup.login.loggedin.processingmanagement.godowns.addgodown.AddGodown;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GodownScreen extends JFrame {
    private final ProcessingManagementScreen processingManagementScreen;
    private JPanel panel;
    private JButton backButton;
    private JButton addGodownsButton;

    public GodownScreen(ProcessingManagementScreen processingManagementScreen) {
        this.processingManagementScreen = processingManagementScreen;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                processingManagementScreen.setVisible(true);
            }
        });
        addGodownsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                AddGodown addGodown = new AddGodown(GodownScreen.this);
                addGodown.init();
                addGodown.setVisible(true);
            }
        });
    }
}
