package loginsignup.login.loggedin.partyManagement.modifyparty;

import loginsignup.login.loggedin.partyManagement.PartyManagementScreen;

import javax.swing.*;

public class ModifyParty extends JFrame {
    private JPanel panel;
    private JButton backButton;

    private final PartyManagementScreen parentWindow;

    public ModifyParty(PartyManagementScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(event -> {
            dispose();
            parentWindow.setVisible(true);
        });
    }
}
