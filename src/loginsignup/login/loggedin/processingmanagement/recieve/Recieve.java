package loginsignup.login.loggedin.processingmanagement.recieve;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Recieve extends JFrame {
    private final ProcessingManagementScreen processingManagementScreen;
    private JPanel panel;
    private JButton backButton;
    private JComboBox comboBox1;
    private JComboBox comboBox2;
    private JButton prevButton;
    private JButton prevButton1;

    public Recieve(ProcessingManagementScreen processingManagementScreen) {
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
