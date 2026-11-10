package loginsignup.login.loggedin;

import loginsignup.login.loggedin.accountingandledger.AALScreen;
import loginsignup.login.loggedin.backupandrestore.BackupAndRestore;
import loginsignup.login.loggedin.billing.BillingScreen;
import loginsignup.login.loggedin.billing.newBill.NewBill;
import loginsignup.login.loggedin.inventorymanagement.InventoryScreen;
import loginsignup.login.loggedin.ordermanagement.OrderScreen;
import loginsignup.login.loggedin.partyManagement.PartyManagementScreen;
import loginsignup.login.loggedin.processingmanagement.ProcessingManagementScreen;
import loginsignup.login.loggedin.transactionsandaccounts.TransactionsScreen;
import loginsignup.login.loggedin.transactionsandaccounts.CashEntry.cashTransaction;
import mainpack.MyClass;
import utils.Variables;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;

public class MainScreen extends JFrame {

    public MainScreen() {
    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        this.setContentPane(panel);
        pack();
        if (Variables.devMode) {
            developerField.setEnabled(true);
        } else {
            developerField.setEnabled(false);
        }
        billingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.billingScreen = new BillingScreen(MainScreen.this);
                MyClass.billingScreen.init();
                MyClass.billingScreen.setVisible(true);
                MainScreen.this.billingButton.setEnabled(false);
            }
        });
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.closeConnectionPool();
                MyClass.login.nullLoginParameters();
                Variables.devMode = false;
                MyClass.login.setDevModeSelected(false);
                MyClass.login.setVisible(true);
                dispose();
            }
        });
        orderManagementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.orderScreen = new OrderScreen();
                MyClass.orderScreen.init();
                MyClass.orderScreen.setVisible(true);
                orderManagementButton.setEnabled(false);
            }
        });
        inventoryManagementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.inventoryScreen = new InventoryScreen();
                MyClass.inventoryScreen.init();
                MyClass.inventoryScreen.setVisible(true);
                inventoryManagementButton.setEnabled(false);

            }
        });
        backUpAndRestoreButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setBackupAndRestoreButtonEnable(false);
                BackupAndRestore backupAndRestore = new BackupAndRestore();
                backupAndRestore.init();
                backupAndRestore.setVisible(true);
            }
        });
        transactionManagementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                TransactionsScreen transactions = new TransactionsScreen();
                transactions.init();
                transactions.setVisible(true);
                MainScreen.this.transactionManagementButton.setEnabled(false);
            }
        });
        addPartyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PartyManagementScreen partyManagementScreen = new PartyManagementScreen(MainScreen.this);
                partyManagementScreen.init();
                partyManagementScreen.setVisible(true);
                addPartyButton.setEnabled(false);
            }
        });
        accountingAndLedgerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AALScreen aalScreen = new AALScreen();
                aalScreen.setVisible(true);
                aalScreen.init();
                setVisible(false);
            }
        });
        processingManagementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProcessingManagementScreen processingManagementScreen = new ProcessingManagementScreen();
                processingManagementScreen.init();
                processingManagementScreen.setVisible(true);
                setProcessingManagementButtonEnabled(false);
            }
        });
        developerField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String text = developerField.getText();
                if (text.startsWith("delete ")) {
                    String date = text.substring(7);
                    try {
                        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MM-yy"));
                        deleteAndForwardBalance(localDate);
                    } catch (DateTimeParseException ex) {
                        return;
                    }

                }
                if (text.startsWith("randomGenerationOfBills ")) {
                    int numberofDays = Integer.parseInt(text.substring(24));
                    generateBillsAndTransaction(numberofDays);
                }
            }
        });
        try {
            setTitle("WELCOME " + MyClass.TITLE + ": " + MyClass.login.getLoginID().toUpperCase() + " ji".toUpperCase());
        } catch (NullPointerException ex) {
            System.out.println("customer name is not yet fetched due to following error");
//            Thread.dumpStack();

            ex.printStackTrace();
        }


    }

    private void generateBillsAndTransaction(int numberOfDays) {

        Random random = new Random();

        for (int counter = 0; counter < numberOfDays; counter++) {
            boolean choice = random.nextBoolean();//true means generating transaction
            int numberOfTransactions = 1 + random.nextInt(4);
            if (!choice) {
                for (int subCounter = 0; subCounter < numberOfTransactions; subCounter++) {
                    NewBill newBill = new NewBill();
                    newBill.init();
                    int numberOfItems = 1 + random.nextInt(10);
                    int date = numberOfDays - counter;

                    newBill.insertRandomValues(numberOfItems, date, null);
                    newBill.getSubmitButton().doClick();
                    newBill.dispose();
                }
            }
            if (choice) {
//                loginsignup.login.loggedin.transactionsandaccounts.CashEntry.cashTransaction newTransaction = new cashTransaction(TransactionsScreen.this);
//                newTransaction.init();
//                int date = numberOfDays - counter;
//                newTransaction.generateTransactions(numberOfTransactions, date);
//                newTransaction.dispose();
            }

        }

    }

    private void deleteAndForwardBalance(LocalDate localDate) {
        System.out.println(localDate.toString() + " deleting data");
//        String updateQuery = "update customers c join billdetails bd on bd.customer_name=c.customer_name set openingAccount=openingAccount + (select sum(totalfinalcost) from billdetails bd2 where bd2.customer_name=c.customer_name and bd2.date<?));";
        String updateQuery =
                "UPDATE customers c " +
                        "LEFT JOIN ( " +
                        "    SELECT b.customer_name, SUM(bd.totalfinalcost) AS total_bills " +
                        "    FROM billdetails bd " +
                        "    JOIN bills b ON bd.billid = b.billid " +
                        "    WHERE b.date < ? " +
                        "    GROUP BY b.customer_name " +
                        ") AS bill_sum ON c.customer_name = bill_sum.customer_name " +
                        "LEFT JOIN ( " +
                        "    SELECT t.customer_name, SUM(t.amount) AS total_transactions " +
                        "    FROM transactions t " +
                        "    WHERE t.date < ? " +
                        "    GROUP BY t.customer_name " +
                        ") AS txn_sum ON c.customer_name = txn_sum.customer_name " +
                        "SET " +
                        "    c.openingAccount = c.openingAccount + IFNULL(bill_sum.total_bills, 0) - IFNULL(txn_sum.total_transactions, 0), " +
                        "    c.balance = c.balance - IFNULL(bill_sum.total_bills, 0) + IFNULL(txn_sum.total_transactions, 0)";


        String deleteBillsQuery = "delete from bills where date<?";
        String deleteTransactionsQuery = "delete from transactions where date<?";
        Connection con = null;
        try {
            con = MyClass.createConnection();
            con.setAutoCommit(false);
            PreparedStatement statement = con.prepareStatement(updateQuery);
            statement.setDate(1, java.sql.Date.valueOf(localDate));
            statement.setDate(2, java.sql.Date.valueOf(localDate));
            statement.executeUpdate();
            statement.close();
            statement = con.prepareStatement(deleteBillsQuery);
            statement.setDate(1, java.sql.Date.valueOf(localDate));
            statement.executeUpdate();
            statement.close();
            statement = con.prepareStatement(deleteTransactionsQuery);
            statement.setDate(1, java.sql.Date.valueOf(localDate));
            statement.executeUpdate();
            statement.close();
            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }


    }

    public void clickOrderManagementButton() {
        orderManagementButton.doClick();
    }

    public void clickAddPartyButton() {
        addPartyButton.doClick();
    }

    public void clickLogoutButton() {
        logoutButton.doClick();

    }

    private JButton billingButton;
    private JPanel panel;
    private JButton logoutButton;
    private JButton orderManagementButton;
    private JButton backUpAndRestoreButton;
    private JButton transactionManagementButton;
    private JButton addPartyButton;
    private JButton inventoryManagementButton;
    private JButton accountingAndLedgerButton;
    private JTextField developerField;
    private JButton processingManagementButton;

    public void setBillingButtonEnabled(boolean b) {
        billingButton.setEnabled(b);
    }

    public void setOrderManagementButtonEnabled(boolean enabled) {
        orderManagementButton.setEnabled(enabled);
    }

    public void setTrasactionManagementButtonEnabled(boolean enabled) {
        transactionManagementButton.setEnabled(enabled);
    }

    public void setAddPartyButtonEnabled(boolean enabled) {
        addPartyButton.setEnabled(enabled);
    }

    public void setInventoryManagementButtonEnabled(boolean enabled) {
        inventoryManagementButton.setEnabled(enabled);
    }

    public void setProcessingManagementButtonEnabled(boolean enabled) {
        processingManagementButton.setEnabled(enabled);
    }

    public void clickBillingButton() {
        billingButton.doClick();
    }

    public void setBackupAndRestoreButtonEnable(boolean b) {
        backUpAndRestoreButton.setEnabled(b);
    }
}
