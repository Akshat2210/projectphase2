package utils;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class LogUtils {
    public static synchronized void logError(Exception exception) {
        if (exception == null) return;
        String username = mainpack.MyClass.login == null
                ? "unknown" : mainpack.MyClass.login.getLoginID();
        if (username == null || username.isBlank()) username = "unknown";
        username = username.trim().replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS"));
        String exceptionName = exception.getClass().getSimpleName();
        if (exceptionName.isEmpty()) exceptionName = "Exception";
        java.nio.file.Path directory = java.nio.file.Path.of("src", "logs");
        java.nio.file.Path file = directory.resolve(username + "_" + timestamp + "_" + exceptionName + ".logs");
        try {
            java.nio.file.Files.createDirectories(directory);
            java.io.StringWriter trace = new java.io.StringWriter();
            exception.printStackTrace(new java.io.PrintWriter(trace));
            // Append on a same-millisecond collision instead of overwriting an earlier error.
            try (BufferedWriter writer = java.nio.file.Files.newBufferedWriter(file,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                writer.write("Username: " + username + " | Time: " + timestamp);
                writer.newLine();
                writer.write(trace.toString());
                writer.newLine();
            }
        } catch (IOException | SecurityException loggingError) {
            System.err.println("Could not write error log: " + loggingError.getMessage());
        }
    }

    static void main() {
    }

    //----------------------------------orderslips logigin class-------w----------------------------------------
    public static int generateVectorLog(Vector<Vector> vector, String title)
    {
        String date=LocalDateTime       .now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm_ss"));
        File file=new File("src/logs/"+title+"_"+date+".logs");
        try(FileWriter fileWriter=new FileWriter(file);
        BufferedWriter bufferedWriter=new BufferedWriter(fileWriter);
        ){
            for(int i=0;i<vector.size();i++){
                for(int j=0;j<vector.get(i).size();j++){
                    bufferedWriter.write(vector.get(i).get(j)+",");
                }
                bufferedWriter.write("\n");
            }
            System.out.println("orderslip logged succesfully");
            return CONSTANTS.SUCCESS_CODE;
        } catch (IOException e) {
            e.printStackTrace();
            return (CONSTANTS.FAIL_CODE);
        }
    }
    public static String generateInventorySnapShot(String status, String date) {
        java.time.format.DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
        String now;
        now = date.isEmpty() ? LocalDateTime.now().format(dateTimeFormatter) : date;
        try (Connection con = UtilityMethods.createConnection();
             PreparedStatement preparedStatement = con.prepareStatement("select * from " + DBStructure.INVENTORY_TABLE);) {
            File file = new File("src/logs/inventory_" + status + "_" + now + ".logs");
            if (!file.exists()) {
                file.createNewFile();
            }

            try (FileWriter fileWriter = new FileWriter(file);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            ) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        bufferedWriter.write(resultSet.getString(1) + ", " + resultSet.getString(2) + ", " + resultSet.getString(3) + ", " + "\n");
                    }
                }
            }
            return now;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "could not create snapshot", "Error", JOptionPane.ERROR_MESSAGE);
//            throw new RuntimeException(e);
            e.printStackTrace();
            return String.valueOf(CONSTANTS.SQL_ERROR);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "could not create snapshot", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return String.valueOf(CONSTANTS.FAIL_CODE);
        }
    }

}
