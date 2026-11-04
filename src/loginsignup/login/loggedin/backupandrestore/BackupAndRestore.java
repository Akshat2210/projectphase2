package loginsignup.login.loggedin.backupandrestore;

import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupAndRestore  extends JFrame {
    private JButton backupButton;
    private JButton restoreButton;
    private JPanel panel;
    private JButton multiuserBackupButton;
    private JButton backButton;

    public BackupAndRestore() {

        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                MyClass.mainScreen.setBackupAndRestoreButtonEnable(true);
            }
        });
    }

    public void createBackUpFile(String dbUser, String dbPassword, String dbHost, String dbPort,
                                 String dbName, String fileName) {
        if (fileName == null || fileName.isBlank()) return;

        java.nio.file.Path credentialsFile = null;
        java.nio.file.Path temporaryBackup = null;
        Process process = null;
        try {
            if (dbUser == null || dbPassword == null || dbHost == null || dbHost.isBlank()
                    || dbPort == null || dbPort.isBlank() || dbName == null || dbName.isBlank()
                    || dbName.startsWith("-") || dbName.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid backup connection details.");
            }
            int port;
            try {
                port = Integer.parseInt(dbPort);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid MySQL port.");
            }
            if (port < 1 || port > 65535 || dbHost.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid backup host or port.");
            }
            java.nio.file.Path destination = java.nio.file.Path.of(fileName).toAbsolutePath();

            // Set the Windows ACL at creation, before any password is written.
            java.nio.file.attribute.UserPrincipal currentUser = java.nio.file.FileSystems.getDefault()
                    .getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            java.nio.file.attribute.AclEntry access = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(currentUser)
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class))
                    .build();
            java.nio.file.attribute.FileAttribute<java.util.List<java.nio.file.attribute.AclEntry>> privateAccess =
                    new java.nio.file.attribute.FileAttribute<>() {
                        public String name() { return "acl:acl"; }
                        public java.util.List<java.nio.file.attribute.AclEntry> value() {
                            return java.util.List.of(access);
                        }
                    };
            credentialsFile = java.nio.file.Files.createTempFile("mysql-backup-", ".cnf", privateAccess);

            // Escape MySQL option-file values, not Windows shell commands.
            String userValue = dbUser.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\b", "\\b");
            String passwordValue = dbPassword.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\b", "\\b");
            if (dbUser.indexOf('\0') >= 0 || dbPassword.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Connection details contain an unsupported character.");
            }
            java.nio.file.Files.writeString(credentialsFile,
                    "[client]\nuser=\"" + userValue + "\"\npassword=\"" + passwordValue + "\"\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            temporaryBackup = java.nio.file.Files.createTempFile(destination.getParent(), "mysql-dump-", ".sql");

            // Output is redirected as raw bytes (not through PowerShell), and the
            // dump explicitly uses utf8mb4 so Hindi text survives backup/restore.
            ProcessBuilder builder = new ProcessBuilder("mysqldump.exe",
                    "--defaults-extra-file=" + credentialsFile,
                    "--host=" + dbHost,
                    "--port=" + port,
                    "--default-character-set=utf8mb4",
                    "--set-charset",
                    "--single-transaction",
                    dbName);
            builder.redirectOutput(temporaryBackup.toFile());
            // Keep diagnostics out of the SQL file and avoid logging connection secrets.
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            process.getOutputStream().close();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Backup failed with exit code: " + exitCode);
            }
            java.nio.file.Files.move(temporaryBackup, destination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            temporaryBackup = null;
            JOptionPane.showMessageDialog(this, "Backup file saved to " + fileName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(this, "Backup interrupted.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException | SecurityException e) {
            JOptionPane.showMessageDialog(this, "Could not create backup: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                // Ensure the child has stopped using the temporary files before cleanup.
                boolean interrupted = Thread.interrupted();
                while (process.isAlive()) {
                    try { process.waitFor(); }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            for (java.nio.file.Path temporaryFile : new java.nio.file.Path[]{credentialsFile, temporaryBackup}) {
                if (temporaryFile != null) {
                    try { java.nio.file.Files.deleteIfExists(temporaryFile); }
                    catch (IOException | SecurityException e) {
                        JOptionPane.showMessageDialog(this,
                                "Could not remove temporary backup file: " + temporaryFile,
                                "Cleanup error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }
    public static String escapeForBatch(String input) {
        return input.replace("%", "%%")  // Escape %
                .replace("^", "^^")  // Escape ^
                .replace("&", "^&")  // Escape &
                .replace("|", "^|")  // Escape |
                .replace("<", "^<")  // Escape <
                .replace(">", "^>")  // Escape >
                .replace("!", "^!"); // Escape !
    }

    public void init() {
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        backupButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String save = UtilityMethods.chooseSaveLocation();
                createBackUpFile(MyClass.login.getLoginID(), MyClass.login.getPassword(),
                        MyClass.login.getHost(), MyClass.login.getPort(), MyClass.login.getDatabase(), save);
            }
        });
        pack();
    }
}
