package loginsignup.login.loggedin.processingmanagement;

import loginsignup.login.loggedin.processingmanagement.recieve.Recieve;
import loginsignup.login.loggedin.processingmanagement.send.Send;
import loginsignup.login.loggedin.processingmanagement.godowns.GodownScreen;
import mainpack.MyClass;
import loginsignup.login.loggedin.processingmanagement.diary.Diary;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProcessingManagementScreen extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton sendButton;
    private JButton recieveButton;
    private JButton godownsButton;
    private JButton diaryButton;

    public ProcessingManagementScreen() {
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                MyClass.mainScreen.setVisible(true);
                MyClass.mainScreen.setProcessingManagementButtonEnabled(true);
            }
        });
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                Send send = new Send(ProcessingManagementScreen.this);
                send.init();
                send.setVisible(true);
            }
        });
        recieveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                Recieve recieve = new Recieve(ProcessingManagementScreen.this);
                recieve.init();
                recieve.setVisible(true);
            }
        });
        diaryButton.addActionListener(event -> {
            Diary diary = new Diary(ProcessingManagementScreen.this);
            diary.init();
            diary.setVisible(true);
            setVisible(false);
        });
        godownsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                GodownScreen godownScreen = new GodownScreen(ProcessingManagementScreen.this);
                godownScreen.init();
                godownScreen.setVisible(true);
            }
        });
    }
}
