package loginsignup.login.loggedin.processingmanagement.diary;

import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;
import loginsignup.login.loggedin.processingmanagement.diary.senddiary.SendDiary;
import loginsignup.login.loggedin.processingmanagement.diary.recievediary.RecieveDiary;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Diary extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton sendDiaryButton;
    private JButton recieveDiaryButton;
    private final ProcessingManagementScreen processingManagementScreen;
    private boolean initialized;

    public Diary() {
        this(null);
    }

    public Diary(ProcessingManagementScreen processingManagementScreen) {
        this.processingManagementScreen = processingManagementScreen;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        setTitle("Processing Diary");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(processingManagementScreen);
        backButton.addActionListener(event -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (processingManagementScreen != null) processingManagementScreen.setVisible(true);
            }
        });
        sendDiaryButton.addActionListener(event -> {
            SendDiary sendDiary = new SendDiary(Diary.this);
            sendDiary.init();
            sendDiary.setVisible(true);
            setVisible(false);
        });
        recieveDiaryButton.addActionListener(event -> {
            RecieveDiary recieveDiary = new RecieveDiary(Diary.this);
            recieveDiary.init();
            recieveDiary.setVisible(true);
            setVisible(false);
        });
    }
}
