package loginsignup.login.loggedin.processingmanagement.godowns.addgodown;

import loginsignup.login.loggedin.processingmanagement.godowns.GodownScreen;
import mainpack.MyClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AddGodown extends JFrame {
    private final GodownScreen godownScreen;
    private JPanel panel;
    private JButton backButton;
    private JComboBox<GodownOption> parentGodownComboBox;
    private JTextField godownName;
    private JButton addGodownButton;

    public AddGodown(GodownScreen godownScreen) {
        this.godownScreen = godownScreen;
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                godownScreen.setVisible(true);
            }
        });

        loadParentGodowns();
        addGodownButton.addActionListener(event -> saveGodown());
    }

    private void saveGodown() {
        final String name = godownName.getText().trim();
        final Integer parentGodownId = getSelectedParentGodownId();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a godown name.",
                    "Godown name required",
                    JOptionPane.WARNING_MESSAGE);
            godownName.requestFocusInWindow();
            return;
        }

        addGodownButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SQLException {
                String sql = "INSERT INTO godowns (godown_name, parent_godown_id) VALUES (?, ?)";
                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) {
                        throw new SQLException("Could not connect to the database.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, name);
                        if (parentGodownId == null) {
                            statement.setNull(2, Types.INTEGER);
                        } else {
                            statement.setInt(2, parentGodownId);
                        }
                        statement.executeUpdate();
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                addGodownButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AddGodown.this,
                            "Godown added successfully.",
                            "Godown added",
                            JOptionPane.INFORMATION_MESSAGE);
                    godownName.setText("");
                    loadParentGodowns();
                    godownName.requestFocusInWindow();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(AddGodown.this,
                            "Could not add godown: " + exception.getMessage(),
                            "Godown not added",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * A parent is a top-level godown. Selecting the first entry means that the
     * new godown will itself be top-level and will save a null parent ID later.
     */
    private void loadParentGodowns() {
        new SwingWorker<List<GodownOption>, Void>() {
            @Override
            protected List<GodownOption> doInBackground() throws SQLException {
                String sql = "SELECT godown_id, godown_name FROM godowns "
                        + "WHERE is_active = 1 AND parent_godown_id IS NULL "
                        + "ORDER BY godown_name";
                List<GodownOption> godowns = new ArrayList<>();

                try (Connection connection = MyClass.createConnection()) {
                    if (connection == null) {
                        throw new SQLException("Could not connect to the database.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(sql);
                         ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            godowns.add(new GodownOption(
                                    resultSet.getInt("godown_id"),
                                    resultSet.getString("godown_name")
                            ));
                        }
                    }
                }
                return godowns;
            }

            @Override
            protected void done() {
                try {
                    parentGodownComboBox.removeAllItems();
                    parentGodownComboBox.addItem(new GodownOption(null, "Select Godown"));
                    for (GodownOption godown : get()) {
                        parentGodownComboBox.addItem(godown);
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(AddGodown.this,
                            "Could not load parent godowns: " + exception.getMessage(),
                            "Godowns unavailable",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Returns null when the new godown has no parent. */
    private Integer getSelectedParentGodownId() {
        GodownOption selectedGodown = (GodownOption) parentGodownComboBox.getSelectedItem();
        return selectedGodown == null ? null : selectedGodown.id;
    }

    private static final class GodownOption {
        private final Integer id;
        private final String displayName;

        private GodownOption(Integer id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
