package loginsignup.login.loggedin.settings;

import javax.swing.*;
import loginsignup.login.loggedin.MainScreen;
import utils.AppSettings;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class settingscreen extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton fontsAndDisplayButton;
    private final MainScreen parentWindow;

    public settingscreen(MainScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        setTitle("Settings");
        setContentPane(panel);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        backButton.addActionListener(e -> goBack());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { goBack(); }
        });
        fontsAndDisplayButton.addActionListener(e -> editPdfFonts());
        pack();
        setExtendedState(MAXIMIZED_BOTH);
    }

    private void goBack() {
        parentWindow.setVisible(true);
        dispose();
    }

    private void editPdfFonts() {
        JSpinner billing = scaleInput(AppSettings.getBillingPdfFontScale());
        JSpinner order = scaleInput(AppSettings.getOrderSlipPdfFontScale());
        JSpinner transaction = scaleInput(AppSettings.getTransactionPdfFontScale());
        JSpinner other = scaleInput(AppSettings.getPdfFontScale());
        JPanel fields = new JPanel(new GridLayout(0, 2, 12, 12));
        fields.add(new JLabel("Billing PDF font scale")); fields.add(billing);
        fields.add(new JLabel("Order-slip PDF font scale")); fields.add(order);
        fields.add(new JLabel("Transaction / Pana font scale")); fields.add(transaction);
        fields.add(new JLabel("Other PDFs (Send / Receive / History)")); fields.add(other);
        fields.add(new JLabel("Larger numbers make PDF text larger."));
        fields.add(new JLabel("Applies to future PDFs."));
        String user = AppSettings.getSelectedUsername();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Sign in before editing personal settings.");
            return;
        }
        Object[] actions = {"Save", "Cancel"};
        while (JOptionPane.showOptionDialog(this, fields, "PDF fonts - " + user,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]) == 0) {
            try {
                for (JSpinner input : new JSpinner[]{billing, order, transaction, other}) input.commitEdit();
                AppSettings.savePdfFontScales(number(other), number(billing), number(order), number(transaction));
                JOptionPane.showMessageDialog(this, "PDF font settings saved for " + user + ".");
                return;
            } catch (java.text.ParseException | IllegalArgumentException invalid) {
                JOptionPane.showMessageDialog(this, "Enter a positive font scale in every field.");
            } catch (java.io.IOException | SecurityException failure) {
                JOptionPane.showMessageDialog(this, "Could not save settings.\n" + failure.getMessage());
                return;
            }
        }
    }

    private static JSpinner scaleInput(float value) {
        JSpinner input = new JSpinner(new SpinnerNumberModel((double) value, null, null, 0.1));
        input.setEditor(new JSpinner.NumberEditor(input, "0.##"));
        return input;
    }

    private static float number(JSpinner input) { return ((Number) input.getValue()).floatValue(); }
}
