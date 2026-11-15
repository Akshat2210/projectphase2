package rootSettings.datamanager;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import mainpack.MyClass;
import rootSettings.RootSettiingsScreen;
import rootSettings.datamanager.datadeletion.DataDeletion;

public class DataManager extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton deleteDataButton;
    private JButton modifyDataButton;
    private JButton viewDataButton;
    private final RootSettiingsScreen parentWindow;
    private boolean initialized;

    public DataManager(RootSettiingsScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        setTitle(MyClass.TITLE + ": DATA MANAGER");
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parentWindow);
        backButton.addActionListener(event -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (parentWindow != null) parentWindow.setVisible(true);
            }
        });
        deleteDataButton.addActionListener(event -> {
            DataDeletion deletion = new DataDeletion(DataManager.this);
            deletion.init();
            deletion.setVisible(true);
            setVisible(false);
        });
        modifyDataButton.setEnabled(false);
        modifyDataButton.setToolTipText("Modify Data screen is not implemented yet");
        viewDataButton.setEnabled(false);
        viewDataButton.setToolTipText("View Data screen is not implemented yet");
    }
}
