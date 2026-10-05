package mainpack;

import loginsignup.LOGIN_SIGNUP;
import loginsignup.login.LOGIN;
import loginsignup.login.loggedin.AddParty;
import loginsignup.login.loggedin.MainScreen;
import loginsignup.login.loggedin.accountingandledger.AALScreen;
import loginsignup.login.loggedin.accountingandledger.ledgerwindows.LedgerWindow;
import loginsignup.login.loggedin.billing.BillingScreen;
import loginsignup.login.loggedin.billing.newBill.NewBill;
import loginsignup.login.loggedin.billing.newBill.SearchResultWindow;
import loginsignup.login.loggedin.billing.purcahseBill.PurchaseBill;
import loginsignup.login.loggedin.billing.viewbills.ViewBackendBill;
import loginsignup.login.loggedin.billing.viewbills.ViewCustomerBill;
import loginsignup.login.loggedin.inventorymanagement.InventoryScreen;
import loginsignup.login.loggedin.inventorymanagement.addinventory.AddInventory;
import loginsignup.login.loggedin.ordermanagement.OrderScreen;
import loginsignup.login.loggedin.ordermanagement.generateorder.InventorySelect;
import loginsignup.login.loggedin.ordermanagement.generateorder.OrderGenerateForm;
import loginsignup.login.loggedin.ordermanagement.vieworders.ViewOrders;
import loginsignup.login.loggedin.rootAdmin.RootScreen;
import loginsignup.login.loggedin.transactionsandaccounts.Transactions;
import loginsignup.login.loggedin.transactionsandaccounts.newtransaction.NewTransaction;
import loginsignup.login.loggedin.transactionsandaccounts.viewTransactions.ViewTransactions;
import loginsignup.signup.SignUp;
import utils.UtilityMethods;

import javax.swing.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static utils.CONSTANTS.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class MyClass {
    // 1. Create the mapping once (you can do this statically or in constructor)
    public static Map<String, String> codeToItemName;
    public final static String TITLE = "GURUKRIPA JEWELLERS";
    public static final String PORT = "jdbc:mysql://localhost:3306/";
    public static InventorySelect inventorySelect;

    public static void main(String[] args) {

        {
            codeToItemName = new HashMap<>();
            codeToItemName.put("RH", "Rani Har");
            codeToItemName.put("RHS", "Rani Har Set");
            codeToItemName.put("MIDH", "Midium Har Nag");
            codeToItemName.put("MIDHS", "Midium Har Set");
            codeToItemName.put("MINH", "Mini Har Nag");
            codeToItemName.put("MINHS", "Mini Har Set");
            codeToItemName.put("R", "Ring");
            codeToItemName.put("TH", "Thegda");
            codeToItemName.put("KT", "Kandora Plus Thegda");
            codeToItemName.put("L", "Latkan");
            codeToItemName.put("TO", "Tops");

            codeToItemName.put("1J", "1 Step Jhumki");
            codeToItemName.put("2J", "2 Step Jhumki");
            codeToItemName.put("3J", "3 Step Jhumki");
            codeToItemName.put("4J", "4 Step Jhumki");
            codeToItemName.put("5J", "5 Step Jhumki");
            codeToItemName.put("PC", "Patli Chain");
            codeToItemName.put("MOC", "Moti Chain");
            codeToItemName.put("MIDC", "Midium Chain");
            codeToItemName.put("MINP", "Mini Pandal");
            codeToItemName.put("MIDP", "Midium Pandal");
            codeToItemName.put("BP", "Big Pandal");
            codeToItemName.put("3LMS4B", "3 Line MS Ladi 4Belt");
            codeToItemName.put("2LMS4B", "2 Line MS Ladi 4Belt");
            codeToItemName.put("3LMS2B", "3 Line MS Ladi 2Belt");
            codeToItemName.put("2LMS2B", "2 Line MS Ladi 2Belt");
            codeToItemName.put("NMS", "Nice MS Ladi");
            codeToItemName.put("RCP", "Regular Chain Patta");
            codeToItemName.put("NSCP", "Nice Small Chain Patta");
            codeToItemName.put("NRCP", "Nice Regular Chain Patta");


        }
        {
            UtilityMethods.printingThread = new Thread(() -> {
                while (true) {
                    try {
                        Runnable job = UtilityMethods.printQueue.take();  // waits until a job is available
                        job.run();
                    } catch (InterruptedException e) {
                        System.out.println("Printing thread interrupted");
                        break;
                    }
                }
            }, "PrintingThread");

        }

        UtilityMethods.printStartUp();
        UtilityMethods.printingThread.setDaemon(true);  // optional: will not block app from closing
        UtilityMethods.printingThread.start();
        login_signup = new LOGIN_SIGNUP();
        login_signup.init();
        login_signup.setVisible(true);
    }

    public static PurchaseBill purchaseBill;
    public static AALScreen aalScreen;
    public static ViewOrders viewOrders;
    public static NewTransaction newTransaction;
    public static SearchResultWindow searchResultWindow;
    public static ViewTransactions viewTransactions;
    public static AddParty addParty;
    public static ViewBackendBill viewBackendBill;
    public static LedgerWindow ledgerWindow;

    public static Connection createConnection() throws SQLException {
        Connection conn;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(login.getUrl(), login.getLoginID(), login.getPassword());
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            System.out.println("✅ Database Connected Successfully to: ");
            return conn;
        } catch (ClassNotFoundException e) {
            System.out.println("❌ MySQL Driver Not Found!");
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (SQLException e) {

            System.out.println("❌ Database Connection Failed!");
            if (e.getErrorCode() == SQL_INVALID_CREDENTIALS_ERROR) {
                JOptionPane.showMessageDialog(null, "invalid userid/password");
//                throw new SQLException(e);
            } else if (e.getErrorCode() == SQL_INVALID_DATABASE_ERROR) {
                JOptionPane.showMessageDialog(login, "database not found error " + e.getErrorCode());
//                throw new SQLException(e);
            }
            throw e;
        }

    }

    public static RootScreen rootScreen;
    public static NewBill newBill;
    public static Transactions transactions;
    public static LOGIN login;
    public static SignUp signUp;

    public static BillingScreen billingScreen;
    public static ViewCustomerBill viewCustomerBill;
    public static MainScreen mainScreen;
    public static LOGIN_SIGNUP login_signup;
    public static OrderScreen orderScreen;
    public static OrderGenerateForm orderGenerateForm;
    public static InventoryScreen inventoryScreen;
    public static AddInventory addInventory;

    public static Connection createConnection(String s) {

        try {
            return DriverManager.getConnection("jdbc:mysql://localhost:3306/sample", "root", "m!n8W74bLc#Iwt");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "error in testing");
            return null;
        }
    }

    public static void setCustomer(int i) {

    }
}