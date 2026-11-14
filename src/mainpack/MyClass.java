package mainpack;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import loginsignup.LOGIN_SIGNUP;
import loginsignup.login.LOGIN;
import loginsignup.login.loggedin.MainScreen;
import loginsignup.login.loggedin.billing.BillingScreen;
import loginsignup.login.loggedin.billing.viewbills.ViewCustomerBill;
import loginsignup.login.loggedin.inventorymanagement.InventoryScreen;
import loginsignup.login.loggedin.inventorymanagement.addinventory.AddInventory;
import loginsignup.login.loggedin.ordermanagement.OrderScreen;
import loginsignup.login.loggedin.ordermanagement.generateorder.InventorySelect;
import loginsignup.login.loggedin.ordermanagement.generateorder.OrderGenerateForm;
import loginsignup.login.loggedin.transactionsandaccounts.TransactionsScreen;
import loginsignup.portconfig.PortConfig;
import loginsignup.signup.SignUp;
import rootSettings.users.Users;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.CONSTANTS.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class MyClass {
    private static  DatabaseEndpoint DATABASE_ENDPOINT;
    public static  String HOST ;
    public static  String PORT ;
    public static final int TIMEOUT = 2000;
    public static final int CONNECTION_POOL_SIZE = 10;
    private static final Object CONNECTION_POOL_LOCK = new Object();
    private static HikariDataSource connectionPool;
    // 1. Create the mapping once (you can do this statically or in constructor)
    public static Map<String, String> codeToItemName;
    public final static String TITLE = "GURUKRIPA JEWELLERS";
    public static InventorySelect inventorySelect;
    public static void setHindiUiFont() {
        FontUIResource font = new FontUIResource("Nirmala UI", Font.PLAIN, 16);

        for (Object key : UIManager.getDefaults().keySet()) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, font);
            }
        }
    }
     private static DatabaseEndpoint loadDatabaseEndpoint() {
        try {
            String json;
            Path sourcePath = Path.of("src", "resources", "PORT.json");
            if (Files.isRegularFile(sourcePath)) {
                json = Files.readString(sourcePath, StandardCharsets.UTF_8);
            } else {
                InputStream resource = MyClass.class.getResourceAsStream("/resources/PORT.json");
                if (resource == null) resource = MyClass.class.getResourceAsStream("/PORT.json");
                if (resource == null) {
                        new PortConfig(null,true).setVisible(true);
                        return null;
                }
                try (InputStream input = resource) {
                    json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            String host = readJsonString(json, "HOST").trim();
            String port = readJsonString(json, "PORT").trim();
            if (host.isEmpty()) throw new IllegalArgumentException("HOST cannot be empty.");

            int portNumber;
            try {
                portNumber = Integer.parseInt(port);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("PORT must be a number.", exception);
            }
            if (portNumber < 1 || portNumber > 65535) {
                throw new IllegalArgumentException("PORT must be between 1 and 65535.");
            }
            return new DatabaseEndpoint(host, String.valueOf(portNumber));
        } catch (IOException | IllegalArgumentException exception) {
            throw new ExceptionInInitializerError(
                    "Could not load database endpoint from PORT.json: " + exception.getMessage());
        }
    }
    public static void loadConfiguration(boolean startApp) {
        DATABASE_ENDPOINT = loadDatabaseEndpoint();

        if (DATABASE_ENDPOINT == null) {
            return;
        }

        HOST = DATABASE_ENDPOINT.host;
        PORT = DATABASE_ENDPOINT.port;
        if(startApp)
            startApp();
    }
    private static String readJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing JSON value: " + key);
        }
        return matcher.group(1);
    }

    private static final class DatabaseEndpoint {
        private final String host;
        private final String port;

        private DatabaseEndpoint(String host, String port) {
            this.host = host;
            this.port = port;
        }
    }

    public static void main(String[] args) {
    setHindiUiFont();
        Runtime.getRuntime().addShutdownHook(
                new Thread(MyClass::closeConnectionPool, "DatabasePoolShutdown")
        );


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
        DATABASE_ENDPOINT= loadDatabaseEndpoint();
        if(DATABASE_ENDPOINT!=null) {
            HOST = DATABASE_ENDPOINT.host;
            PORT = DATABASE_ENDPOINT.port;
            startApp();
        }

    }
    public static void startApp(){
        login_signup = new LOGIN_SIGNUP();
        login_signup.init();
        login_signup.setVisible(true);
    }
    public static Users users;

    public static Connection createConnection() throws SQLException {
        try {
            return getConnectionPool().getConnection();
        } catch (SQLException e) {
            SQLException databaseError = findDatabaseError(e);
            System.out.println("❌ Database Connection Failed!");
            JOptionPane.showMessageDialog(null, e.getMessage(),"ERROR",JOptionPane.ERROR_MESSAGE);
            if (databaseError.getErrorCode() == SQL_INVALID_CREDENTIALS_ERROR) {
                JOptionPane.showMessageDialog(null, "invalid userid/password");
            } else if (databaseError.getErrorCode() == SQL_INVALID_DATABASE_ERROR) {
                JOptionPane.showMessageDialog(login, "database not found error " + databaseError.getErrorCode());
            }
            throw databaseError;
        }
    }

    private static SQLException findDatabaseError(SQLException exception) {
        SQLException databaseError = exception;
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() != 0) {
                databaseError = sqlException;
            }
            cause = cause.getCause();
        }
        return databaseError;
    }

    private static HikariDataSource getConnectionPool() throws SQLException {
        synchronized (CONNECTION_POOL_LOCK) {
            if (connectionPool != null && !connectionPool.isClosed()) return connectionPool;
            if (login == null || login.getUrl() == null || login.getLoginID() == null
                    || login.getPassword() == null) {
                throw new SQLException("Database login details are not available.");
            }

            HikariConfig config = new HikariConfig();
            config.setPoolName("AccountingSoftwareProPool");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(login.getUrl());
            config.setUsername(login.getLoginID());
            config.setPassword(login.getPassword());
            config.setMaximumPoolSize(CONNECTION_POOL_SIZE);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(TIMEOUT);
            config.setValidationTimeout(Math.min(TIMEOUT, 1000));
            config.setIdleTimeout(300_000);
            config.setMaxLifetime(1_800_000);
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            config.setInitializationFailTimeout(-1);

            try {
                connectionPool = new HikariDataSource(config);
                return connectionPool;
            } catch (RuntimeException exception) {
                throw new SQLException("Could not initialize the database connection pool.", exception);
            }
        }
    }

    public static void closeConnectionPool() {
        synchronized (CONNECTION_POOL_LOCK) {
            if (connectionPool != null) {
                connectionPool.close();
                connectionPool = null;
            }
        }
    }


    public static TransactionsScreen transactions;
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



    public static void setCustomer(int i) {

    }
}
