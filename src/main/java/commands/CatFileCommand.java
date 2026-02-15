package commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

public class CatFileCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
    if (args.length < 3 || !args[1].equals("-p")) {
      System.out.println("Usage: cat-file -p <hash>");
      return;
    }
    
    String hash = args[2];
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectFile = new File(".git/objects/" + dirName + "/" + fileName);
    
    try (FileInputStream fis = new FileInputStream(objectFile);
         InflaterInputStream iis = new InflaterInputStream(fis)) {
      
      byte[] decompressed = iis.readAllBytes();
      
      // Find null byte that separates header from content
      int nullIndex = -1;
      for (int i = 0; i < decompressed.length; i++) {
        if (decompressed[i] == 0) {
          nullIndex = i;
          break;
        }
      }
      
      if (nullIndex == -1) {
        throw new RuntimeException("Invalid object format");
      }
      
      // Extract everything after the null byte
      byte[] content = Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
      
      System.out.print(new String(content));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
