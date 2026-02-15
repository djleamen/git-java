package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

public class LsTreeCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
    if (args.length < 3 || !args[1].equals("--name-only")) {
      System.out.println("Usage: ls-tree --name-only <tree_sha>");
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
      
      // Parse tree entries
      int pos = nullIndex + 1;
      while (pos < decompressed.length) {
        // Read mode
        int spacePos = pos;
        while (spacePos < decompressed.length && decompressed[spacePos] != ' ') {
          spacePos++;
        }
        pos = spacePos + 1;
        
        // Read name
        int nullPos = pos;
        while (nullPos < decompressed.length && decompressed[nullPos] != 0) {
          nullPos++;
        }
        String name = new String(decompressed, pos, nullPos - pos);
        System.out.println(name);
        pos = nullPos + 1;
        
        // Skip 20-byte hash
        pos += 20;
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
