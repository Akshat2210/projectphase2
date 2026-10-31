package loginsignup.login.loggedin.billing;

import loginsignup.login.loggedin.MainScreen;
import loginsignup.login.loggedin.billing.newBill.NewBill;
import loginsignup.login.loggedin.billing.newBill.SearchResultWindow;
import loginsignup.login.loggedin.billing.purcahseBill.PurchaseBill;
import loginsignup.login.loggedin.billing.viewbills.ViewBackendBill;
import loginsignup.login.loggedin.billing.viewbills.ViewCustomerBill;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static mainpack.MyClass.viewCustomerBill;


public class BillingScreen extends JFrame {
    private JPanel panel;
    private JButton newBillButton;
    private JButton backButton;
    private JButton viewCustomerBillsButton;
    private final MainScreen parentWindow;

    public JButton getViewBillButton() {
        return viewCustomerBillsButton;
    }

    private JButton viewBillButton;
    private JButton newPurchaseButton;

    public JButton getNewBillButton() {
        return newBillButton;
    }

    public BillingScreen(MainScreen parentWindow) {
        this.parentWindow = parentWindow;
    }
    public void init(){
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setContentPane(panel);
        pack();

        newBillButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               NewBill newBill=new NewBill();
                newBill.init();
                newBill.setVisible(true);

                setVisible(false);

            }
        });
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                setVisible(false);
                    dispose();
                    parentWindow.setBillingButtonEnabled(true);
                    parentWindow.setVisible(true);
            }
        });
        viewCustomerBillsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                viewCustomerBill=new ViewCustomerBill();
                viewCustomerBill.init();
                viewCustomerBill.setVisible(true);
            }
        });
        viewBillButton.addActionListener(e -> {
            ViewBackendBill viewBackendBill=new ViewBackendBill(BillingScreen.this);
            viewBackendBill.init();
            viewBackendBill.setVisible(true);
            setVisible(false);
        });
        newPurchaseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PurchaseBill purchaseBill=new PurchaseBill(BillingScreen.this);
                purchaseBill.init();
                purchaseBill.setVisible(true);
                setVisible(false);
            }
        });

    }
    public JButton getPurchaseBillButton() {
    return newPurchaseButton;
    }

    public void clickNewBillButton() {
        newBillButton.doClick();
    }
}
