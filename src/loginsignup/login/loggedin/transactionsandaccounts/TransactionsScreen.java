package loginsignup.login.loggedin.transactionsandaccounts;

import loginsignup.login.loggedin.transactionsandaccounts.CashEntry.cashTransaction;
import loginsignup.login.loggedin.transactionsandaccounts.journalEntry.JournalEntry;
import loginsignup.login.loggedin.transactionsandaccounts.viewTransactions.ViewTransactions;
import mainpack.MyClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TransactionsScreen extends JFrame {
    private JButton backButton;
    private JButton newTransactionButton;
    private JButton viewTransactionsButton;
    private JButton journalEntryButton;
    private JPanel panel;

    public TransactionsScreen() {
}

    public void init() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                setVisible(false);
                dispose();
                MyClass.mainScreen.setVisible(true);
                MyClass.mainScreen.setTrasactionManagementButtonEnabled(true);
            }
        });
        newTransactionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cashTransaction newTransaction=new cashTransaction(TransactionsScreen.this);

                newTransaction.init();
                newTransaction.setVisible(true);
                setVisible(false);

            }
        });
        viewTransactionsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                ViewTransactions viewTransactions =new ViewTransactions(TransactionsScreen.this);
                viewTransactions.init();
                viewTransactions.setVisible(true);
            }
        });
        journalEntryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JournalEntry journalEntry = new JournalEntry(TransactionsScreen.this);
                journalEntry.init();
                journalEntry.setVisible(true);
                setVisible(false);
            }
        });

    }
}
