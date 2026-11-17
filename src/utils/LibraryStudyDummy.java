package utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LibraryStudyDummy {
    static void main() {
        UtilityMethods.pBuilder("");
        }
    public static void fileCreate(String[] data, String filename){
        File file=new File(filename);
        try {
            java.nio.file.Files.createDirectories(file.toPath().toAbsolutePath().getParent());
            if(file.createNewFile())
            {
                System.out.println(filename+" is created");
            }
            else System.out.println(filename+ " already exists");
            try (FileWriter fileWriter=new FileWriter(file,true);
                 BufferedWriter bufferedWriter=new BufferedWriter(fileWriter)) {
                int x=0;
                while (x<data.length){
                    bufferedWriter.append(data[x]).append("\n");
                    bufferedWriter.flush();
                    x++;
                }
            }

        } catch (IOException | SecurityException e) {
            System.err.println("Could not write file '" + filename + "': " + e.getMessage());
        }
    }
}
