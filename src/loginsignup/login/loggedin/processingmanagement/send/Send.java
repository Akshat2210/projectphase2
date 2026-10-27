package loginsignup.login.loggedin.processingmanagement.send;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Send extends JFrame {
    private final ProcessingManagementScreen processingManagementScreen;
    private JPanel panel;
    private JButton backButton;
    private JComboBox sendFrom;
    private JComboBox sendTo;
    private JComboBox dateComboBox;

    public Send(ProcessingManagementScreen processingManagementScreen) {
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
    }
}
