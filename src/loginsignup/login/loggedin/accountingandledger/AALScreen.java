package loginsignup.login.loggedin.accountingandledger;

import loginsignup.login.loggedin.accountingandledger.ledgerwindows.LedgerWindow;
import mainpack.MyClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AALScreen extends JFrame {
    private JButton backButton;
    private JList<String> itemList;
    private JList<String> customerList;
    private JButton cashStatementButton;
    private JButton expenseAccountButton;// component declaration
    private JPanel panel;
    private JPanel subPanel;

    public AALScreen() {

    }

    public void init() {
        setContentPane(panel);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getListOfCustomers();
        getListOfItem();
        setTitle("Accounting");
        // Assuming you have a JList<String> itemList
        itemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = itemList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedItem = itemList.getModel().getElementAt(index);
                        openItem(selectedItem);
                    }
                }
            }
        });
        customerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = customerList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedCustomer = customerList.getModel().getElementAt(index);
                        openCustomer(selectedCustomer);
                        System.out.println("selected customer is "+selectedCustomer);
                    }
                }
            }
        });
// Handle Enter key
        itemList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openItem");
        itemList.getActionMap().put("openItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedItem = itemList.getSelectedValue();
                if (selectedItem != null) {
                    openItem(selectedItem);
                }
            }
        });

        customerList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openItem");
        customerList.getActionMap().put("openItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedCustomer = customerList.getSelectedValue();
                if (selectedCustomer != null) {
                    openCustomer(selectedCustomer);
                }
            }
        });

// Your method to handle the item


        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                dispose();
                MyClass.mainScreen.setVisible(true);


            }
        });

    }

    private void openCustomer(String selectedCustomer) {
        setVisible(false);
        MyClass.ledgerWindow = new LedgerWindow();
        MyClass.ledgerWindow.init(selectedCustomer, LedgerWindow.CUSTOMER_MODE);
        MyClass.ledgerWindow.setVisible(true);
    }

    private void openItem(String selectedItem) {
        setVisible(false);
        String designID = selectedItem.split("->", 2)[0].trim();
        MyClass.ledgerWindow = new LedgerWindow();
        MyClass.ledgerWindow.init(customerList.getSelectedValue() == null ? designID : "", LedgerWindow.ITEM_MODE);
        MyClass.ledgerWindow.setVisible(true);

    }

    private void getListOfItem() {
        String query = "select designid,itemname from inventory";
        DefaultListModel<String> model = new DefaultListModel<>();
        try(Connection con=MyClass.createConnection(); Statement statement=con.createStatement()) {
          try (ResultSet rs = statement.executeQuery(query)){
                while (rs.next()) {
                    model.addElement(rs.getString("DesignID") + "->" + rs.getString("itemname"));
                }
            }itemList.setModel(model);


        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    private void getListOfCustomers() {
        String query = "select customer_name from customers";
        DefaultListModel<String> model = new DefaultListModel<>();

        try(Connection con = MyClass.createConnection(); Statement statement=con.createStatement()) {
            try(ResultSet rs = statement.executeQuery(query))
            {
                while (rs.next()) {
                    model.addElement(rs.getString(1));
                }
            }
            customerList.setModel(model);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

}
