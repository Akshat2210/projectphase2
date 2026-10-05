package loginsignup.login.loggedin.transactionsandaccounts.newtransaction;

import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Random;

public class NewTransaction extends JFrame {
    private LocalDate date;

    public JButton getBackButton() {
        return backButton;
    }

    Connection transacCon = null;

    public void setDate(Object date) {
        if (date == null) {
            this.date = null;
            return;
        } else if (date.toString().isEmpty()) {
            this.date = null;
            return;
        }
        this.date = LocalDate.parse(date.toString(), DateTimeFormatter.ofPattern("dd-MM-yy"));
    }

    public LocalDate getDate() {
        return date;
    }

    public NewTransaction() {

    }

    private void appendListOfCustomers(JComboBox comboBox) {

        String Query = "Select customer_name from  customers;";
        if (comboBox.getItemCount() != 0) comboBox.removeAllItems();
        comboBox.addItem("Select Customer");

        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(Query);) {
                while (rs.next()) {
                    comboBox.addItem(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }

    public void generateTransactions(int x, int date) {
        Random random = new Random();
        String[] remarks = {
                "Payment received", "Advance given", "Final settlement", "Partial payment", "Refund",
                "Adjustment", "Bonus", "Penalty", "Service charge", "Extra work"
        };

        for (int i = 0; i < x; i++) {
            // 1. Set a random date (index from 1 to 50)
            int dateCount = dateComboBox.getItemCount();
            if (date == -1) {
                if (dateCount > 1) {
                    dateComboBox.setSelectedIndex(1 + random.nextInt(Math.min(50, dateCount - 1)));
                }
            } else {
                dateComboBox.setSelectedIndex(date);
            }
            // 2. Set a random party name
            int partyCount = partyNameComboBox.getItemCount();
            if (partyCount > 0) {
                partyNameComboBox.setSelectedIndex(1 + random.nextInt(partyCount - 1));
            }

            // 3. Set a random amount between 400000 and 500000
            int amount = 400000 + random.nextInt(100000); // (100000 - 5000 + 1)
            amountTextField.setText(String.valueOf(amount));

            // 4. Randomly select in or out radio button

            inRadioButton.setSelected(true);


            // 5. Set random remark
            remarkTextField.setText(remarks[random.nextInt(remarks.length)]);

            // 6. Click submit
            submitButton.doClick();
        }
    }

    AddedTransactions addedTransactions;

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);

        pack();
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(inRadioButton);
        buttonGroup.add(outRadioButton);
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                addedTransactions.dispose();
                MyClass.transactions.setVisible(true);
                dispose();

            }
        });
        submitButton.addActionListener(
                e -> {


                    double amount;
                    String remark = remarkTextField.getText();
                    try {
                        amount = Double.parseDouble(amountTextField.getText());
                    } catch (NumberFormatException exception) {
                        JOptionPane.showMessageDialog(MyClass.newTransaction, "invalid amount entered");
                        throw new RuntimeException();
                    }

                    String customerName = partyNameComboBox.getSelectedItem() == null ? "" : partyNameComboBox.getSelectedItem().toString();
                    if (customerName.isEmpty() || partyNameComboBox.getSelectedIndex() == 0) {
                        JOptionPane.showMessageDialog(MyClass.newTransaction, "select valid party name");
                        return;

                    }
                    String transactionQuery = "INSERT INTO transactions (customer_name, amount, date,remark) VALUES (?, ?, ?,?)";
                    String customerTableQuery = "update customers set balance=balance-? where customer_name=?";
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy");
                    Date parsed = null;
                    try {
                        parsed = sdf.parse(dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString());
                    } catch (ParseException ex) {
                        throw new RuntimeException(ex);
                    }
                    if (outRadioButton.isSelected()) amount = amount * (-1);
                    java.sql.Date sqlDate = new java.sql.Date(parsed.getTime());


                    try {
                        transacCon = DriverManager.getConnection(MyClass.login.getUrl(), MyClass.login.getLoginID(), MyClass.login.getPassword());

                        transacCon.setAutoCommit(false);
                        PreparedStatement statement = transacCon.prepareStatement(transactionQuery);
                        statement.setString(1, customerName);
                        statement.setDouble(2, amount);
                        statement.setDate(3, sqlDate);
                        statement.setString(4, remark);
                        statement.executeUpdate();
                        statement.close();
                        statement = transacCon.prepareStatement(customerTableQuery);

                        statement.setDouble(1, amount);
                        statement.setString(2, customerName);
                        statement.executeUpdate();

                        transacCon.commit();
                        addedTransactions.fetchData(getDate());
                        transacCon.close();
                    } catch (SQLException ex) {
                        if (transacCon != null) {
                            try {
                                ex.printStackTrace();
                                transacCon.rollback();
                                transacCon.close();
                            } catch (SQLException exc) {
                                throw new RuntimeException(exc);
                            }
                            throw new RuntimeException(ex);
                        }
                    }

                });
        dateComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setDate(dateComboBox.getSelectedItem());
                if (addedTransactions != null && getDate() != null)
                    addedTransactions.fetchData(getDate());


            }
        });
        remarkTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                generateTransactions(Integer.parseInt(remarkTextField.getText()), -1);
            }
        });
        clearAccountButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                String query = "SELECT COALESCE(SUM(bd.totalfinalcost), 0) - COALESCE(SUM(t.amount), 0) AS outstanding FROM billdetails bd LEFT JOIN transactions t ON t.customer_name = bd.customer_name WHERE bd.customer_name = ?";

                String customerName = partyNameComboBox.getSelectedItem() != null ? partyNameComboBox.getSelectedItem().toString() : "";
                String query = "SELECT (coalesce(sum(totalfinalcost),0) - (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE customer_name = ?) + (SELECT IFNULL(openingaccount, 0) FROM customers WHERE customer_name = ?)) AS outstanding FROM billdetails bd JOIN bills b ON b.billid = bd.billid WHERE b.customer_name = ?";
                try (Connection con = MyClass.createConnection(); PreparedStatement statement = con.prepareStatement(query)) {
                    statement.setString(1, customerName);
                    statement.setString(2, customerName);
                    statement.setString(3, customerName);
                    ResultSet rs = statement.executeQuery();
                    double balance = 0;
                    if (rs.next()) {
                        balance += rs.getDouble(1);

                    }

                    balance = (double) (Math.round(balance * 100)) / 100;

                    if (balance == 0) {
                        JOptionPane.showMessageDialog(MyClass.newTransaction, "no advances or dues found");

                        return;
                    }
                    if (balance < 0) {
                        balance *= (-1);
                        outRadioButton.setSelected(true);
                    } else {
                        inRadioButton.setSelected(true);
                    }
                    amountTextField.setText(balance + "");


                } catch (SQLException ex) {
                    ex.printStackTrace();
                }

            }
        });

        appendListOfCustomers(partyNameComboBox);
        UtilityMethods.generateAndAddDates(dateComboBox, false);

        inRadioButton.setSelected(true);
        addedTransactions = new AddedTransactions();
        addedTransactions.init();
        addedTransactions.setVisible(true);


        addedTransactions.fetchData(getDate());
        UtilityMethods.splitFrame(MyClass.newTransaction, addedTransactions, UtilityMethods.VERTI_SPLIT);

    }

    private JButton backButton;
    private JComboBox dateComboBox;
    private JPanel panel;
    private JComboBox partyNameComboBox;
    private JTextField amountTextField;
    private JRadioButton inRadioButton;
    private JRadioButton outRadioButton;
    private JButton submitButton;
    private JTextField remarkTextField;
    private JButton clearAccountButton;


}
