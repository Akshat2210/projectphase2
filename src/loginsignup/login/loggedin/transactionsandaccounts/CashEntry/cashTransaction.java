package loginsignup.login.loggedin.transactionsandaccounts.CashEntry;

import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Random;

public class cashTransaction extends JFrame {
    // Automatic journals duplicate bills/payments; opening journals duplicate openingaccount.
    // Only direct adjustments are additional to the existing source-record calculation.
    private static final String CLEAR_ACCOUNT_BALANCE_QUERY =
            "SELECT COALESCE(c.openingaccount, 0) + " +
            "COALESCE((SELECT SUM(bd.totalfinalcost) FROM billdetails bd " +
            "JOIN bills b ON b.billid = bd.billid WHERE b.customer_name = c.customer_name " +
            "AND COALESCE(b.drafts, 0) = 0), 0) - " +
            "COALESCE((SELECT SUM(t.amount) FROM transactions t " +
            "WHERE t.customer_name = c.customer_name), 0) + " +
            "COALESCE((SELECT SUM(COALESCE(j.debit, 0) - COALESCE(j.credit, 0)) " +
            "FROM journalentry j WHERE j.accountname = c.customer_name " +
            "AND j.transaction_type = 'direct'), 0) AS outstanding " +
            "FROM customers c WHERE c.customer_name = ?";
    private LocalDate date;

    public JButton getBackButton() {
        return backButton;
    }

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

    public cashTransaction() {

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
                    String journalEntryQuery = "INSERT INTO journalentry " +
                            "(transaction_type, date, accountname, billid, transaction_id, credit, debit) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)";
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy");
                    Date parsed = null;
                    try {
                        parsed = sdf.parse(dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString());
                    } catch (ParseException ex) {
                        throw new RuntimeException(ex);
                    }
                    if (outRadioButton.isSelected()) amount = amount * (-1);
                    java.sql.Date sqlDate = new java.sql.Date(parsed.getTime());


                    try (Connection con = MyClass.createConnection()) {
                        try (
                                PreparedStatement transactionStatement = con.prepareStatement(transactionQuery, Statement.RETURN_GENERATED_KEYS);
                                PreparedStatement customerStatement = con.prepareStatement(customerTableQuery);
                                PreparedStatement journalEntryStatement = con.prepareStatement(journalEntryQuery);
                        ) {
                            con.setAutoCommit(false);
                            transactionStatement.setString(1, customerName);
                            transactionStatement.setDouble(2, amount);
                            transactionStatement.setDate(3, sqlDate);
                            transactionStatement.setString(4, remark);
                            transactionStatement.executeUpdate();

                            int transactionId;
                            try (ResultSet generatedKeys = transactionStatement.getGeneratedKeys()) {
                                if (!generatedKeys.next()) {
                                    throw new SQLException("Could not create the transaction record.");
                                }
                                transactionId = generatedKeys.getInt(1);
                            }

                            customerStatement.setDouble(1, amount);
                            customerStatement.setString(2, customerName);
                            customerStatement.executeUpdate();

                            double journalAmount = Math.abs(amount);

                            // The party account is credited for cash received and debited for cash paid.
                            journalEntryStatement.setString(1, "payments");
                            journalEntryStatement.setDate(2, sqlDate);
                            journalEntryStatement.setString(3, customerName);
                            journalEntryStatement.setNull(4, Types.INTEGER);
                            journalEntryStatement.setInt(5, transactionId);
                            journalEntryStatement.setDouble(6, inRadioButton.isSelected() ? journalAmount : 0);
                            journalEntryStatement.setDouble(7, outRadioButton.isSelected() ? journalAmount : 0);
                            journalEntryStatement.addBatch();

                            // Cash is debited for money received and credited for money paid out.
                            journalEntryStatement.setString(1, "payments");
                            journalEntryStatement.setDate(2, sqlDate);
                            journalEntryStatement.setString(3, "Cash");
                            journalEntryStatement.setNull(4, Types.INTEGER);
                            journalEntryStatement.setInt(5, transactionId);
                            journalEntryStatement.setDouble(6, inRadioButton.isSelected() ? 0 : journalAmount);
                            journalEntryStatement.setDouble(7, outRadioButton.isSelected() ? 0 : journalAmount);
                            journalEntryStatement.addBatch();

                            journalEntryStatement.executeBatch();
                            con.commit();
                            addedTransactions.fetchData(getDate());
                        }catch (SQLException ex){
                            JOptionPane.showMessageDialog(cashTransaction.this, ex.getMessage());
                            con.rollback();
                        }
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(cashTransaction.this, ex.getMessage());
                        throw new RuntimeException(ex);
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
                String customerName = partyNameComboBox.getSelectedItem() != null ? partyNameComboBox.getSelectedItem().toString() : "";
                if (partyNameComboBox.getSelectedIndex() <= 0 || customerName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(cashTransaction.this, "Select a valid party first.");
                    return;
                }
                try (Connection con = MyClass.createConnection();
                     PreparedStatement statement = con.prepareStatement(CLEAR_ACCOUNT_BALANCE_QUERY)) {
                    statement.setString(1, customerName);
                    BigDecimal balance;
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            amountTextField.setText("");
                            JOptionPane.showMessageDialog(cashTransaction.this, "Selected party was not found.");
                            return;
                        }
                        balance = rs.getBigDecimal("outstanding").setScale(2, RoundingMode.HALF_UP);
                    }

                    if (balance.signum() == 0) {
                        amountTextField.setText("");
                        JOptionPane.showMessageDialog(MyClass.newTransaction, "no advances or dues found");

                        return;
                    }
                    if (balance.signum() < 0) {
                        outRadioButton.setSelected(true);
                    } else {
                        inRadioButton.setSelected(true);
                    }
                    amountTextField.setText(balance.abs().toPlainString());


                } catch (SQLException ex) {
                    amountTextField.setText("");
                    JOptionPane.showMessageDialog(cashTransaction.this,
                            "Could not calculate outstanding balance: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
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
