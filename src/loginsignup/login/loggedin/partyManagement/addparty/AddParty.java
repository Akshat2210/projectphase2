package loginsignup.login.loggedin.partyManagement.addparty;

import mainpack.MyClass;
import loginsignup.login.loggedin.partyManagement.PartyManagementScreen;
import utils.CONSTANTS;
import utils.DBStructure;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;


public class AddParty extends JFrame {
    private JPanel panel1;
    private JButton backButton;
    private JTextField customerNameField;
    private JButton addButton;
    private JTextField openingBalanceTextField;

    private final PartyManagementScreen parentWindow;

    public AddParty(PartyManagementScreen parentWindow) {
        this.parentWindow = parentWindow;
    }

    private void reset() {
        customerNameField.setText("");
        openingBalanceTextField.setText("");

    }

    public void setCustomerName(String customerName) {
        customerNameField.setText(customerName);
    }

    public void setOpeningBalance(String openingBalance) {
        openingBalanceTextField.setText(openingBalance);
    }

    public void clickAddButton() {
        addButton.doClick();
    }

    public void init() {
//        ((AbstractDocument) openingBalanceTextField.getDocument()).setDocumentFilter(UtilityMethods.getDocFilter());
        setContentPane(panel1);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parentWindow.setVisible(true);
                dispose();


            }
        });
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = customerNameField.getText().trim().isEmpty() ? "error" : customerNameField.getText().trim();
                if (name.contentEquals("error")) return;
                if (name.length() > 50) {
                    JOptionPane.showMessageDialog(AddParty.this, "Party name cannot exceed 50 characters.");
                    return;
                }
                String query = "insert into " + DBStructure.CUSTOMERS_TABLE + "(" + DBStructure.CUSTOMERS_CUSTOMER_NAME + "," + DBStructure.CUSTOMERS_OPENING_ACCOUNT + "," + DBStructure.CUSTOMERS_BALANCE + ") values(?,?,?)";
                String journalQuery = "INSERT INTO " + DBStructure.JOURNAL_ENTRY_TABLE + " (" +
                        DBStructure.JOURNAL_ENTRY_TRANSACTION_TYPE + ", " +
                        DBStructure.JOURNAL_ENTRY_DATE + ", " +
                        DBStructure.JOURNAL_ENTRY_ACCOUNT_NAME + ", " +
                        DBStructure.JOURNAL_ENTRY_BILL_ID + ", " +
                        DBStructure.JOURNAL_ENTRY_TRANSACTION_ID + ", " +
                        DBStructure.JOURNAL_ENTRY_CREDIT + ", " +
                        DBStructure.JOURNAL_ENTRY_DEBIT + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
                double openingaccount=0;
                try {
                    openingaccount = Double.parseDouble(openingBalanceTextField.getText()==null || openingBalanceTextField.getText().trim().isEmpty()?"0": openingBalanceTextField.getText());
                    if (!Double.isFinite(openingaccount)) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(AddParty.this,"please enter valid balance");
                return;
                }
                try(Connection con=MyClass.createConnection();
                    PreparedStatement preparedStatement=con.prepareStatement(query);
                    PreparedStatement journalStatement=con.prepareStatement(journalQuery)
                ) {
                    boolean originalAutoCommit = con.getAutoCommit();
                    try {
                        con.setAutoCommit(false);
                        preparedStatement.setString(1, name);
                        preparedStatement.setDouble(2, openingaccount);
                        preparedStatement.setDouble(3, openingaccount);
                        preparedStatement.executeUpdate();

                        if (openingaccount != 0) {
                            double amount = Math.abs(openingaccount);
                            java.sql.Date entryDate = new java.sql.Date(System.currentTimeMillis());

                            // A positive opening balance is receivable from the customer;
                            // a negative balance is an advance/credit owed to the customer.
                            addOpeningBalanceJournalLine(journalStatement, entryDate, name,
                                    openingaccount > 0 ? 0 : amount,
                                    openingaccount > 0 ? amount : 0);
                            addOpeningBalanceJournalLine(journalStatement, entryDate, "Opening Balance Equity",
                                    openingaccount > 0 ? amount : 0,
                                    openingaccount > 0 ? 0 : amount);
                            journalStatement.executeBatch();
                        }

                        con.commit();
                    } catch (SQLException ex) {
                        con.rollback();
                        throw ex;
                    } finally {
                        con.setAutoCommit(originalAutoCommit);
                    }
                    JOptionPane.showMessageDialog(AddParty.this, "Party added successfully.");
                    reset();
                } catch (SQLIntegrityConstraintViolationException ex) {
//                    throw new RuntimeException(ex);
                    JOptionPane.showMessageDialog(AddParty.this, "Part Name already exists");
                    ex.printStackTrace();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(AddParty.this, ex.getMessage());
                    return;
                }

            }
        });
        pack();

    }

    private void addOpeningBalanceJournalLine(PreparedStatement statement, java.sql.Date entryDate,
                                               String accountName, double credit, double debit) throws SQLException {
        statement.setString(1, CONSTANTS.OPENING_BALANCE_ENUM);
        statement.setDate(2, entryDate);
        statement.setString(3, accountName);
        statement.setNull(4, java.sql.Types.INTEGER);
        statement.setNull(5, java.sql.Types.INTEGER);
        statement.setDouble(6, credit);
        statement.setDouble(7, debit);
        statement.addBatch();
    }
}
