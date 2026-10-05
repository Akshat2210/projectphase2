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

    public  void createBackUpFile(String dbUser, String dbPassword, String dbName, String fileName) {
        String filePath = "tempBack.bat";
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yy___HH_mm_ss");
        sdf.format(new Date());
        String backupFile = fileName ;

        try {
            FileWriter writer = new FileWriter(filePath);
            writer.write("@echo off\n");
            writer.write("mysqldump -u " + dbUser + " -p" + escapeForBatch(dbPassword) + " " + dbName + " > \"" + backupFile + "\"\n");
            writer.close();

            // Run the batch file
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", filePath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);  // Debugging output
            }

            // Wait for process to complete
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(BackupAndRestore.this , "Backup file saved to "+ fileName);
            } else {
                JOptionPane.showMessageDialog(BackupAndRestore.this, "Backup failed with exit code: " + exitCode, "eror", JOptionPane.ERROR_MESSAGE);
                
            }



        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
                createBackUpFile(MyClass.login.getLoginID(),MyClass.login.getPassword(),MyClass.login.getDatabase(),save);
            }
        });
        pack();
    }
}
