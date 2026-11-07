package loginsignup.login.loggedin.partyManagement;

import loginsignup.login.loggedin.MainScreen;
import loginsignup.login.loggedin.partyManagement.addparty.AddParty;
import loginsignup.login.loggedin.partyManagement.modifyparty.ModifyParty;

import javax.swing.*;

public class PartyManagementScreen extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton addPartyButton;
    private JButton modifyPartyButton;

    private final MainScreen parentWindow;

    public PartyManagementScreen(MainScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(event -> {
            dispose();
            parentWindow.setAddPartyButtonEnabled(true);
            parentWindow.setVisible(true);
        });

        addPartyButton.addActionListener(event -> {
            AddParty addParty = new AddParty(PartyManagementScreen.this);
            addParty.init();
            addParty.setVisible(true);
            setVisible(false);
        });

        modifyPartyButton.addActionListener(event -> {
            ModifyParty modifyParty = new ModifyParty(PartyManagementScreen.this);
            modifyParty.init();
            modifyParty.setVisible(true);
            setVisible(false);
        });
    }
}
