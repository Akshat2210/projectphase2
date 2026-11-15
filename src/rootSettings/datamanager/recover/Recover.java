package rootSettings.datamanager.recover;

import mainpack.MyClass;
import rootSettings.datamanager.DataManager;
import rootSettings.datamanager.ProtectedDeletionBackup;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Recover extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JButton fromEncryptedFileButton;
    private final DataManager parentWindow;
    private boolean initialized;

    public Recover(DataManager parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        setTitle(MyClass.TITLE + ": RECOVER DATA");
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parentWindow);
        backButton.addActionListener(event -> dispose());
        fromEncryptedFileButton.addActionListener(event -> chooseEncryptedBackup());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (parentWindow != null) parentWindow.setVisible(true);
            }
        });
    }

    private void chooseEncryptedBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select encrypted deletion backup");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Encrypted SQL backup (*.sql.enc)", "enc"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path encryptedBackup = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        JPasswordField passwordField = new JPasswordField(20);
        int answer = JOptionPane.showConfirmDialog(this,
                new Object[]{"Enter the 16-digit recovery password:", passwordField},
                "Decrypt backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;

        char[] password = passwordField.getPassword();
        if (!isRecoveryPassword(password)) {
            Arrays.fill(password, '\0');
            JOptionPane.showMessageDialog(this, "Enter the 16-digit recovery password.",
                    "Invalid password", JOptionPane.ERROR_MESSAGE);
            return;
        }

        fromEncryptedFileButton.setEnabled(false);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                Path decrypted = Files.createTempFile("accounting-recovery-", ".sql");
                try (OutputStream output = Files.newOutputStream(decrypted)) {
                    ProtectedDeletionBackup.decrypt(encryptedBackup, password, output);
                    return decrypted;
                } catch (Exception exception) {
                    Files.deleteIfExists(decrypted);
                    throw exception;
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                fromEncryptedFileButton.setEnabled(true);
                try {
                    chooseRecoveryTarget(get());
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Recover.this,
                            "Could not decrypt the backup. Check the file and recovery password.\n"
                                    + rootMessage(exception),
                            "Recovery stopped", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void chooseRecoveryTarget(Path decryptedSql) {
        Object[] options = {"Create new database", "Use existing empty database", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Choose where the recovered data should be imported.\n"
                        + "Existing databases must be empty and will never be overwritten.",
                "Recovery destination", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            deleteTemporaryFile(decryptedSql);
            return;
        }

        String target = choice == 0 ? askForNewDatabaseName() : chooseExistingDatabase();
        if (target == null) {
            deleteTemporaryFile(decryptedSql);
            return;
        }

        boolean createNew = choice == 0;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Import the decrypted backup into database '" + target + "'?\n"
                        + "This can take several minutes.",
                "Confirm recovery", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            deleteTemporaryFile(decryptedSql);
            return;
        }
        importBackup(decryptedSql, target, createNew);
    }

    private String askForNewDatabaseName() {
        String target = JOptionPane.showInputDialog(this,
                "Name for the new recovery database:", "Create recovery database",
                JOptionPane.QUESTION_MESSAGE);
        if (target == null) return null;
        target = target.trim();
        if (!target.matches("[A-Za-z0-9_]{1,64}")) {
            JOptionPane.showMessageDialog(this,
                    "Database name must contain 1-64 letters, numbers, or underscores.",
                    "Invalid database name", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return target;
    }

    private String chooseExistingDatabase() {
        try {
            List<String> databases = userDatabases();
            if (databases.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No non-system databases are available.",
                        "No recovery target", JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
            JComboBox<String> databaseSelector = new JComboBox<>(databases.toArray(String[]::new));
            int answer = JOptionPane.showConfirmDialog(this, databaseSelector,
                    "Select empty recovery database", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            return answer == JOptionPane.OK_OPTION ? (String) databaseSelector.getSelectedItem() : null;
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(this, "Could not list databases: " + rootMessage(exception),
                    "Recovery stopped", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private List<String> userDatabases() throws SQLException {
        List<String> databases = new ArrayList<>();
        try (Connection connection = MyClass.createConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW DATABASES")) {
            while (rows.next()) {
                String database = rows.getString(1);
                if (!isSystemDatabase(database)) databases.add(database);
            }
        }
        return databases;
    }

    private void importBackup(Path decryptedSql, String target, boolean createNew) {
        fromEncryptedFileButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    prepareTargetDatabase(target, createNew);
                    importSql(decryptedSql, target);
                    return null;
                } finally {
                    deleteTemporaryFile(decryptedSql);
                }
            }

            @Override
            protected void done() {
                fromEncryptedFileButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(Recover.this,
                            "Recovery completed into database '" + target + "'.\n"
                                    + "Log in to that database to inspect the restored data.",
                            "Recovery complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Recover.this,
                            "Recovery failed. The target database was not overwritten.\n"
                                    + rootMessage(exception),
                            "Recovery failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void prepareTargetDatabase(String target, boolean createNew) throws SQLException {
        try (Connection connection = MyClass.createConnection()) {
            if (createNew) {
                if (databaseExists(connection, target)) {
                    throw new SQLException("Database '" + target + "' already exists. Choose another name.");
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE `" + target + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
            } else if (!databaseExists(connection, target)) {
                throw new SQLException("The selected database no longer exists.");
            }
            if (!isDatabaseEmpty(connection, target)) {
                throw new SQLException("Database '" + target + "' is not empty. Recovery will not overwrite it.");
            }
        }
    }

    private boolean databaseExists(Connection connection, String target) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            statement.setString(1, target);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean isDatabaseEmpty(Connection connection, String target) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?")) {
            statement.setString(1, target);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) == 0;
            }
        }
    }

    private void importSql(Path decryptedSql, String target) throws Exception {
        if (MyClass.login == null) throw new IOException("Log in before recovering a backup.");
        String user = MyClass.login.getLoginID();
        String password = MyClass.login.getPassword();
        String host = MyClass.login.getHost();
        String port = MyClass.login.getPort();
        if (user == null || password == null || host == null || host.isBlank()) {
            throw new IOException("Missing MySQL login details.");
        }

        Path credentials = Files.createTempFile("mysql-recovery-", ".cnf");
        Path importLog = Files.createTempFile("mysql-recovery-import-", ".log");
        Process process = null;
        try {
            Files.writeString(credentials, "[client]\nuser=\"" + mysqlOption(user) + "\"\npassword=\""
                    + mysqlOption(password) + "\"\n", StandardCharsets.UTF_8);
            process = new ProcessBuilder("mysql.exe", "--defaults-extra-file=" + credentials,
                    "--host=" + host, "--port=" + port, "--default-character-set=utf8mb4",
                    "--database=" + target).redirectInput(decryptedSql.toFile()).redirectErrorStream(true)
                    .redirectOutput(importLog.toFile()).start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Import timed out after five minutes.");
            }
            if (process.exitValue() != 0) {
                String output = Files.readString(importLog, StandardCharsets.UTF_8);
                throw new IOException("mysql import failed: " + output.substring(0, Math.min(output.length(), 4096)).trim());
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(credentials);
            Files.deleteIfExists(importLog);
        }
    }

    private static boolean isSystemDatabase(String database) {
        return database == null || List.of("mysql", "sys", "information_schema", "performance_schema")
                .contains(database.toLowerCase(java.util.Locale.ROOT));
    }

    private static String mysqlOption(String value) throws IOException {
        if (value.indexOf('\0') >= 0) throw new IOException("Invalid MySQL credential.");
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean isRecoveryPassword(char[] password) {
        if (password.length != 16) return false;
        for (char character : password) {
            if (character < '0' || character > '9') return false;
        }
        return true;
    }

    private static String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void deleteTemporaryFile(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }
}
